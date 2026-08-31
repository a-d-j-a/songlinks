// src/sources/ytmusic.js — robust YouTube Music Innertube adapter
// Handles env-based API key, updated clientVersion, resilient response parsing,
// normalized duration, robust artist extraction, signatureCipher filtering,
// query/limit/timeout validation, timeout propagation, and per-result isolation.

const { fetchJson, DEFAULT_TIMEOUT_MS } = require("../util");

const INNERTUBE_SEARCH = "https://music.youtube.com/youtubei/v1/search";
const INNERTUBE_PLAYER = "https://www.youtube.com/youtubei/v1/player";

// ---------------------------------------------------------------------------
// Config — env overrides, updated defaults (original 2024 versions were outdated)
// ---------------------------------------------------------------------------

const DEFAULT_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30";
const API_KEY = (() => {
  const env =
    (process.env.YTMUSIC_API_KEY && String(process.env.YTMUSIC_API_KEY).trim()) ||
    (process.env.YT_API_KEY && String(process.env.YT_API_KEY).trim()) ||
    (process.env.INNERTUBE_API_KEY && String(process.env.INNERTUBE_API_KEY).trim()) ||
    (process.env.YOUTUBE_API_KEY && String(process.env.YOUTUBE_API_KEY).trim()) ||
    "";
  return env || DEFAULT_API_KEY;
})();

const WEB_CLIENT_VERSION = (() => {
  const env =
    (process.env.YTMUSIC_CLIENT_VERSION && String(process.env.YTMUSIC_CLIENT_VERSION).trim()) ||
    (process.env.YT_CLIENT_VERSION && String(process.env.YT_CLIENT_VERSION).trim()) ||
    (process.env.INNERTUBE_CLIENT_VERSION && String(process.env.INNERTUBE_CLIENT_VERSION).trim()) ||
    "";
  // Updated from 1.20240403.01.00 (outdated). Pick 2024-12-02 which is still compatible;
  // callers can override via env for freshness.
  return env || "1.20241202.01.00";
})();

const ANDROID_CLIENT_VERSION = (() => {
  const env =
    (process.env.YTMUSIC_ANDROID_VERSION && String(process.env.YTMUSIC_ANDROID_VERSION).trim()) ||
    (process.env.YT_ANDROID_VERSION && String(process.env.YT_ANDROID_VERSION).trim()) ||
    "";
  // 20.10.38 is last version that returns direct URLs in adaptiveFormats.
  // 20.42.39+ uses SABR (serverAbrStreamingUrl) with no URLs in adaptiveFormats -> breaks stream.
  return env || "20.10.38";
})();

const ANDROID_SDK_VERSION = (() => {
  const raw = process.env.YTMUSIC_ANDROID_SDK || process.env.YT_ANDROID_SDK;
  if (raw && String(raw).trim()) return String(raw).trim();
  return "30";
})();

const DEFAULT_LIMIT = 10;
const MIN_LIMIT = 1;
const MAX_LIMIT = 50;
const MAX_QUERY_LEN = 300;

function getSearchContext() {
  return {
    context: {
      client: {
        clientName: "WEB_REMIX",
        clientVersion: WEB_CLIENT_VERSION,
        hl: "en",
        gl: "US",
      },
    },
  };
}

function getPlayerContext() {
  return {
    context: {
      client: {
        clientName: "ANDROID",
        clientVersion: ANDROID_CLIENT_VERSION,
        androidSdkVersion: ANDROID_SDK_VERSION,
        hl: "en",
        gl: "US",
      },
    },
  };
}

// ---------------------------------------------------------------------------
// Validation helpers (mirrors itunes/jiosaavn)
// ---------------------------------------------------------------------------

function normalizeQuery(q) {
  if (typeof q !== "string") {
    throw new TypeError("ytmusic.search: query must be a non-empty string");
  }
  const trimmed = q.trim();
  if (!trimmed) return null;
  if (trimmed.length > MAX_QUERY_LEN) return trimmed.slice(0, MAX_QUERY_LEN);
  return trimmed;
}

function normalizeLimit(raw) {
  if (raw === undefined || raw === null || raw === "") return DEFAULT_LIMIT;
  const parsed = parseInt(String(raw).trim(), 10);
  if (!Number.isFinite(parsed) || Number.isNaN(parsed)) return DEFAULT_LIMIT;
  let n = Math.trunc(parsed);
  if (n < MIN_LIMIT) n = MIN_LIMIT;
  if (n > MAX_LIMIT) n = MAX_LIMIT;
  return n;
}

function normalizeTimeout(raw) {
  let n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return DEFAULT_TIMEOUT_MS;
  n = Math.trunc(n);
  if (n < 1) n = 1;
  if (n > 30000) n = 30000;
  return n;
}

function parseStringField(val) {
  if (val === null || val === undefined) return undefined;
  try {
    const s = String(val).trim();
    return s ? s : undefined;
  } catch (_) {
    return undefined;
  }
}

// Duration helpers — normalize "M:SS" / "H:MM:SS" -> seconds (integer)

function parseDurationTextSingle(single) {
  if (single == null) return undefined;
  const str = String(single).trim();
  if (!str) return undefined;
  // HH:MM:SS
  const hms = str.match(/^(\d+):(\d{1,2}):(\d{1,2})$/);
  if (hms) {
    const h = parseInt(hms[1], 10);
    const m = parseInt(hms[2], 10);
    const sec = parseInt(hms[3], 10);
    if (!Number.isFinite(h) || !Number.isFinite(m) || !Number.isFinite(sec)) return undefined;
    if (m >= 60 || sec >= 60) return undefined;
    const total = h * 3600 + m * 60 + sec;
    return Number.isFinite(total) && total > 0 ? total : undefined;
  }
  // MM:SS or M:SS
  const ms = str.match(/^(\d+):(\d{1,2})$/);
  if (ms) {
    const m = parseInt(ms[1], 10);
    const s = parseInt(ms[2], 10);
    if (!Number.isFinite(m) || !Number.isFinite(s)) return undefined;
    if (s >= 60) return undefined;
    const total = m * 60 + s;
    return Number.isFinite(total) && total > 0 ? total : undefined;
  }
  // Fallback: if string is plain seconds numeric (unlikely for YT but handle)
  // Only if string is purely digits and reasonable (< 10 hours)
  if (/^\d+$/.test(str)) {
    const n = parseInt(str, 10);
    if (Number.isFinite(n) && n > 0 && n < 36000) {
      // Could be ambiguous; treat as seconds only if explicitly numeric and caller expects it.
      // For YT we prefer colon form; but allow fallback for approximateDurationMs / millis
      return n;
    }
  }
  return undefined;
}

function parseDurationText(text) {
  if (text == null) return undefined;
  const s = String(text).trim();
  if (!s) return undefined;
  // If contains bullet "•", split and search from end for duration-like part
  if (s.includes("•")) {
    const parts = s.split("•").map((p) => p.trim()).filter(Boolean);
    for (let i = parts.length - 1; i >= 0; i--) {
      const d = parseDurationTextSingle(parts[i]);
      if (d !== undefined) return d;
    }
    return undefined;
  }
  return parseDurationTextSingle(s);
}

function parseDurationMillis(val) {
  if (val == null || val === "") return undefined;
  const n = typeof val === "string" ? Number(val.trim()) : Number(val);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  const secs = Math.round(n / 1000);
  return Number.isFinite(secs) && secs > 0 ? secs : undefined;
}

// ---------------------------------------------------------------------------
// Renderer traversal helpers
// ---------------------------------------------------------------------------

function getFlexColumnRuns(renderer, idx) {
  try {
    const cols = renderer.flexColumns;
    if (!Array.isArray(cols)) return [];
    const col = cols[idx];
    const runs = col?.musicResponsiveListItemFlexColumnRenderer?.text?.runs;
    return Array.isArray(runs) ? runs : [];
  } catch (_) {
    return [];
  }
}

function runsToText(runs) {
  if (!Array.isArray(runs) || runs.length === 0) return "";
  return runs.map((r) => (r && typeof r.text === "string" ? r.text : "")).join("");
}

function getFixedColumnText(renderer, idx) {
  try {
    const cols = renderer.fixedColumns;
    if (!Array.isArray(cols)) return undefined;
    const col = cols[idx];
    if (!col) return undefined;
    const runs = col?.musicResponsiveListItemFixedColumnRenderer?.text?.runs;
    if (Array.isArray(runs) && runs.length) {
      const t = runs.map((r) => (typeof r.text === "string" ? r.text : "")).join("").trim();
      if (t) return t;
    }
    const simple = col?.musicResponsiveListItemFixedColumnRenderer?.text?.simpleText;
    if (typeof simple === "string" && simple.trim()) return simple.trim();
    return undefined;
  } catch (_) {
    return undefined;
  }
}

function resolveThumbnail(renderer) {
  try {
    const thumbs = renderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails;
    if (Array.isArray(thumbs) && thumbs.length) {
      for (let i = thumbs.length - 1; i >= 0; i--) {
        const u = thumbs[i]?.url;
        if (typeof u === "string" && u.trim() && /^https?:\/\//i.test(u.trim())) return u.trim();
      }
    }
    const alt = renderer?.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails;
    if (Array.isArray(alt) && alt.length) {
      for (let i = alt.length - 1; i >= 0; i--) {
        const u = alt[i]?.url;
        if (typeof u === "string" && u.trim() && /^https?:\/\//i.test(u.trim())) return u.trim();
      }
    }
    // Fallback: any thumbnail object
    const flat = renderer?.thumbnail?.thumbnails;
    if (Array.isArray(flat) && flat.length) {
      for (let i = flat.length - 1; i >= 0; i--) {
        const u = flat[i]?.url;
        if (typeof u === "string" && u.trim()) return u.trim();
      }
    }
  } catch (_) {}
  return undefined;
}

function resolveVideoId(renderer) {
  const candidates = [
    () => renderer?.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId,
    () => renderer?.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchPlaylistEndpoint?.videoId,
    () => renderer?.playlistItemData?.videoId,
    () => renderer?.navigationEndpoint?.watchEndpoint?.videoId,
    () => renderer?.doubleTapCommand?.watchEndpoint?.videoId,
    () => {
      const items = renderer?.menu?.menuRenderer?.items;
      if (!Array.isArray(items)) return null;
      for (const it of items) {
        const vid =
          it?.menuNavigationItemRenderer?.navigationEndpoint?.watchEndpoint?.videoId ||
          it?.menuServiceItemRenderer?.serviceEndpoint?.queueAddEndpoint?.queueTarget?.videoId ||
          it?.menuServiceItemRenderer?.serviceEndpoint?.playlistEditEndpoint?.actions?.[0]?.addedVideoId ||
          it?.toggleMenuServiceItemRenderer?.defaultServiceEndpoint?.likeEndpoint?.target?.videoId ||
          it?.toggleMenuServiceItemRenderer?.toggledServiceEndpoint?.likeEndpoint?.target?.videoId;
        if (vid) return vid;
      }
      return null;
    },
    () => {
      const cols = renderer?.flexColumns;
      if (!Array.isArray(cols)) return null;
      for (const c of cols) {
        const runs = c?.musicResponsiveListItemFlexColumnRenderer?.text?.runs;
        if (!Array.isArray(runs)) continue;
        for (const run of runs) {
          const vid = run?.navigationEndpoint?.watchEndpoint?.videoId;
          if (vid) return vid;
          const browseVid = run?.navigationEndpoint?.browseEndpoint?.browseId;
          // browseId is not videoId, skip
          if (browseVid && /^[a-zA-Z0-9_-]{11}$/.test(browseVid)) return browseVid;
        }
      }
      return null;
    },
  ];

  for (const fn of candidates) {
    try {
      const v = fn();
      if (typeof v === "string" && v.trim()) {
        const trimmed = v.trim();
        if (trimmed.length >= 5 && trimmed.length <= 100 && !/\s/.test(trimmed) && /^[a-zA-Z0-9_-]+$/.test(trimmed)) {
          // Prefer 11-char YouTube IDs but allow broader for robustness
          return trimmed;
        }
      }
    } catch (_) {}
  }
  return null;
}

function resolveArtist(subtitleRuns) {
  if (!Array.isArray(subtitleRuns) || subtitleRuns.length === 0) return undefined;
  const parts = [];
  let cur = "";
  for (const r of subtitleRuns) {
    const t = typeof r.text === "string" ? r.text : "";
    if (t === " • ") {
      parts.push(cur.trim());
      cur = "";
    } else {
      cur += t;
    }
  }
  if (cur) parts.push(cur.trim());
  const filtered = parts.map((p) => p.trim()).filter(Boolean);
  if (filtered.length === 0) return undefined;

  const durationRe = /^(\d+:)?\d+:\d+$/;
  const yearRe = /^(19|20)\d{2}$/;
  const viewsRe = /^\d+([,.]\d+)*\s*(views|plays|listeners|watching)/i;
  const countRe = /^\d[\d,.]*$/;

  // Find first non-metadata part that looks like artist
  for (let i = 0; i < filtered.length; i++) {
    const p = filtered[i];
    if (!p) continue;
    if (durationRe.test(p)) continue;
    if (yearRe.test(p)) continue;
    if (viewsRe.test(p)) continue;
    if (/^explicit$/i.test(p)) continue;
    if (countRe.test(p) && p.length < 6) continue;
    // This is likely artist (could be "Artist1, Artist2" or "Artist • Topic")
    return p;
  }
  // Fallback: filter out duration/year and take first remaining
  const nonMeta = filtered.filter((p) => !durationRe.test(p) && !yearRe.test(p) && !viewsRe.test(p));
  if (nonMeta.length) return nonMeta[0];
  return undefined;
}

function resolveTitle(titleRuns) {
  if (!Array.isArray(titleRuns) || titleRuns.length === 0) return undefined;
  const text = runsToText(titleRuns).trim();
  return text || undefined;
}

function resolveDuration(renderer, subtitleRuns) {
  // 1) fixedColumns (most reliable for duration like "3:45")
  const fixed = getFixedColumnText(renderer, 0);
  if (fixed) {
    const d = parseDurationText(fixed);
    if (d !== undefined) return d;
  }
  // 2) subtitle runs — last bullet often duration
  if (Array.isArray(subtitleRuns) && subtitleRuns.length) {
    const text = runsToText(subtitleRuns);
    if (text) {
      const d = parseDurationText(text);
      if (d !== undefined) return d;
    }
    // Also try splitting parts and testing each from end
    const parts = [];
    let cur = "";
    for (const r of subtitleRuns) {
      const t = r.text || "";
      if (t === " • ") {
        parts.push(cur);
        cur = "";
      } else {
        cur += t;
      }
    }
    if (cur) parts.push(cur);
    for (let i = parts.length - 1; i >= 0; i--) {
      const d = parseDurationTextSingle((parts[i] || "").trim());
      if (d !== undefined) return d;
    }
  }
  // 3) Check renderer lengthMs fields if present (some responses have length, duration)
  const altDuration =
    renderer?.fixedColumns?.[0]?.musicResponsiveListItemFixedColumnRenderer?.text?.runs?.[0]?.text ||
    renderer?.lengthText?.runs?.[0]?.text ||
    renderer?.lengthText?.simpleText;
  if (altDuration) {
    const d = parseDurationText(String(altDuration));
    if (d !== undefined) return d;
  }
  return undefined;
}

function mapMusicItem(renderer) {
  if (!renderer || typeof renderer !== "object" || Array.isArray(renderer)) return null;

  const videoId = resolveVideoId(renderer);
  if (!videoId) return null;
  const vidStr = String(videoId).trim();
  if (!vidStr || vidStr === "null" || vidStr === "undefined" || vidStr.toLowerCase() === "nan") return null;
  if (vidStr.length > 100 || /\s/.test(vidStr)) return null;

  const titleRuns = getFlexColumnRuns(renderer, 0);
  const subtitleRuns = getFlexColumnRuns(renderer, 1);

  let title = resolveTitle(titleRuns);
  if (!title) {
    // fallback to simpleText
    try {
      const simple = renderer.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.simpleText;
      if (typeof simple === "string" && simple.trim()) title = simple.trim();
    } catch (_) {}
  }
  title = parseStringField(title);

  let artist = resolveArtist(subtitleRuns);
  if (!artist) {
    const joined = runsToText(subtitleRuns).trim();
    if (joined) {
      const first = joined.split("•")[0]?.trim();
      if (first && first !== title) {
        // avoid treating duration/year/views as artist
        if (!/^(\d+:)?\d+:\d+$/.test(first) && !/^(19|20)\d{2}$/.test(first) && !/^\d+([,.]\d+)*\s*(views|plays|listeners|watching)/i.test(first)) {
          artist = parseStringField(first);
        }
      }
    }
  } else {
    artist = parseStringField(artist);
    // guard: if resolved artist looks like duration, discard
    if (artist && /^(\d+:)?\d+:\d+$/.test(artist)) artist = undefined;
  }

  let duration = resolveDuration(renderer, subtitleRuns);
  if (duration !== undefined && (!Number.isFinite(duration) || duration <= 0)) duration = undefined;

  const cover = resolveThumbnail(renderer);
  const page = `https://music.youtube.com/watch?v=${vidStr}`;

  return {
    source: "ytmusic",
    id: vidStr,
    title: title || undefined,
    artist: artist || undefined,
    duration: duration,
    cover: cover,
    page: page,
    streams: [],
  };
}

// ---------------------------------------------------------------------------
// Robust extractResults — handles structural variations and missing sections
// ---------------------------------------------------------------------------

function extractResults(json) {
  if (!json || typeof json !== "object" || Array.isArray(json)) return [];

  // Known stable path: contents.tabbedSearchResultsRenderer.tabs[0].tabRenderer.content.sectionListRenderer.contents
  let contents = null;

  try {
    const tabs = json?.contents?.tabbedSearchResultsRenderer?.tabs;
    if (Array.isArray(tabs) && tabs.length) {
      for (const tab of tabs) {
        const c = tab?.tabRenderer?.content?.sectionListRenderer?.contents;
        if (Array.isArray(c) && c.length) {
          contents = c;
          break;
        }
        // alternative nesting: tab content directly
        const alt = tab?.tabRenderer?.content;
        if (alt?.sectionListRenderer?.contents && Array.isArray(alt.sectionListRenderer.contents)) {
          contents = alt.sectionListRenderer.contents;
          break;
        }
        // Some responses use singleColumn?
        if (alt && Array.isArray(alt?.sectionListRenderer?.contents)) {
          contents = alt.sectionListRenderer.contents;
          break;
        }
      }
    }
  } catch (_) {}

  if (!Array.isArray(contents)) {
    try {
      const sl = json?.contents?.sectionListRenderer?.contents;
      if (Array.isArray(sl) && sl.length) contents = sl;
    } catch (_) {}
  }

  if (!Array.isArray(contents)) {
    if (Array.isArray(json.contents)) {
      contents = json.contents;
    }
  }

  // Fallback: deep search for any shelf containing musicResponsiveListItemRenderer
  if (!Array.isArray(contents) || contents.length === 0) {
    const foundWrappers = [];
    const stack = [json];
    const seen = new Set();
    let steps = 0;
    const maxSteps = 400;

    while (stack.length && steps < maxSteps) {
      const cur = stack.pop();
      steps++;
      if (!cur || typeof cur !== "object" || seen.has(cur)) continue;
      seen.add(cur);

      if (cur.musicShelfRenderer && Array.isArray(cur.musicShelfRenderer.contents)) {
        for (const w of cur.musicShelfRenderer.contents) {
          if (w?.musicResponsiveListItemRenderer) foundWrappers.push(w);
        }
      }
      if (cur.musicCardShelfRenderer && Array.isArray(cur.musicCardShelfRenderer.contents)) {
        // cards not typical songs, skip unless they contain list items
        for (const w of cur.musicCardShelfRenderer.contents) {
          if (w?.musicResponsiveListItemRenderer) foundWrappers.push(w);
        }
      }
      if (cur.itemSectionRenderer && Array.isArray(cur.itemSectionRenderer.contents)) {
        for (const w of cur.itemSectionRenderer.contents) {
          if (w?.musicResponsiveListItemRenderer) foundWrappers.push(w);
          // also handle nested shelves inside itemSection
          if (w?.musicShelfRenderer) {
            const inner = w.musicShelfRenderer.contents;
            if (Array.isArray(inner)) {
              for (const iw of inner) if (iw?.musicResponsiveListItemRenderer) foundWrappers.push(iw);
            }
          }
        }
      }

      // push children for further traversal
      for (const v of Object.values(cur)) {
        if (v && typeof v === "object") {
          if (Array.isArray(v)) {
            for (const el of v) if (el && typeof el === "object") stack.push(el);
          } else {
            stack.push(v);
          }
        }
      }
    }

    if (foundWrappers.length) {
      const items = [];
      for (const w of foundWrappers) {
        const r = w?.musicResponsiveListItemRenderer || w;
        if (!r) continue;
        try {
          const mapped = mapMusicItem(r);
          if (mapped) items.push(mapped);
        } catch (_) {
          continue;
        }
      }
      return items;
    }
    return [];
  }

  // contents is sectionList contents — iterate shelves
  const items = [];
  for (const sec of contents) {
    if (!sec || typeof sec !== "object") continue;

    let inside = null;
    if (Array.isArray(sec?.musicShelfRenderer?.contents)) inside = sec.musicShelfRenderer.contents;
    else if (Array.isArray(sec?.itemSectionRenderer?.contents)) inside = sec.itemSectionRenderer.contents;
    else if (Array.isArray(sec?.musicCardShelfRenderer?.contents)) inside = sec.musicCardShelfRenderer.contents;
    else continue;

    for (const wrapped of inside) {
      if (!wrapped || typeof wrapped !== "object") continue;

      // Prefer musicResponsiveListItemRenderer, fallback to wrapped itself if it has flexColumns
      const renderer = wrapped.musicResponsiveListItemRenderer
        ? wrapped.musicResponsiveListItemRenderer
        : wrapped.flexColumns
        ? wrapped
        : null;

      if (!renderer || typeof renderer !== "object") continue;

      try {
        const mapped = mapMusicItem(renderer);
        if (mapped) items.push(mapped);
      } catch (_) {
        continue;
      }
    }
  }
  return items;
}

// ---------------------------------------------------------------------------
// Stream helpers — validation and format mapping
// ---------------------------------------------------------------------------

function validateVideoId(id) {
  if (id == null) throw new TypeError("ytmusic.stream: id must be a non-empty string");
  let s = String(id).trim();
  if (!s) throw new TypeError("ytmusic.stream: id must be a non-empty string");
  // support "yt:<id>" prefix used by /stream endpoint logs
  if (s.startsWith("yt:")) s = s.slice(3).trim();
  if (!s) throw new Error("ytmusic.stream: id is empty after stripping prefix");
  if (s.length > 100) throw new Error("ytmusic.stream: id too long (max 100)");
  if (/[\s\r\n]/.test(s)) throw new Error("ytmusic.stream: id must not contain whitespace");
  if (!/^[a-zA-Z0-9_-]+$/.test(s)) throw new Error("ytmusic.stream: id contains invalid characters");
  if (s.length < 5) throw new Error("ytmusic.stream: id too short");
  return s;
}

function parseBitrate(val) {
  if (val == null || val === "") return 0;
  const n = typeof val === "string" ? parseInt(val.trim(), 10) : Number(val);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

function parseContentLength(val) {
  if (val == null || val === "") return undefined;
  const n = typeof val === "string" ? parseInt(val.trim(), 10) : Number(val);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

module.exports = {
  name: "ytmusic",
  // expose for testing/debugging
  _extractResults: extractResults,
  _parseDuration: parseDurationText,
  _mapMusicItem: mapMusicItem,

  async search(q, limit = DEFAULT_LIMIT, opts = {}) {
    const normalizedQ = normalizeQuery(q);
    if (normalizedQ === null) {
      return [];
    }
    const normalizedLimit = normalizeLimit(limit);

    let timeout;
    if (opts && typeof opts === "object" && opts.timeout != null) {
      timeout = normalizeTimeout(opts.timeout);
    } else if (typeof limit === "object" && limit !== null && limit.timeout != null) {
      timeout = normalizeTimeout(limit.timeout);
    } else {
      timeout = normalizeTimeout(DEFAULT_TIMEOUT_MS);
      // allow fetchJson to also respect env timeouts; no extra handling needed
    }

    const url = `${INNERTUBE_SEARCH}?key=${API_KEY}&prettyPrint=false`;
    const body = { ...getSearchContext(), query: normalizedQ };

    let json;
    try {
      json = await fetchJson(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
        timeout,
      });
    } catch (err) {
      const msg = err && err.message ? err.message : String(err);
      const wrapped = new Error(`ytmusic search failed for "${normalizedQ.slice(0, 50)}": ${msg}`);
      wrapped.cause = err;
      if (err && err.status) wrapped.status = err.status;
      if (err && err.name) wrapped.name = err.name;
      if (err && err.name === "TimeoutError") wrapped.name = "TimeoutError";
      throw wrapped;
    }

    if (json == null) return [];

    // fetchJson returns parsed JSON; protect against unexpected shapes
    if (typeof json !== "object" || Array.isArray(json)) {
      // Some mocks return array directly — treat as empty since search should be object
      if (Array.isArray(json)) return [];
      throw new Error(`ytmusic: unexpected response shape (type ${typeof json})`);
    }

    let items;
    try {
      items = extractResults(json);
    } catch (err) {
      const msg = err && err.message ? err.message : String(err);
      throw new Error(`ytmusic: failed to parse search results: ${msg}`);
    }

    if (!Array.isArray(items)) return [];

    // Filter invalid entries (already done in mapMusicItem) and slice to limit
    const valid = [];
    for (const it of items) {
      try {
        if (!it || !it.id || !it.source) continue;
        // ensure id is string
        const idStr = String(it.id).trim();
        if (!idStr) continue;
        // duration already normalized; ensure it's number if present
        if (it.duration !== undefined && (!Number.isFinite(it.duration) || it.duration <= 0)) {
          it.duration = undefined;
        }
        valid.push({ ...it, id: idStr });
        if (valid.length >= normalizedLimit) break;
      } catch (_) {
        continue;
      }
    }

    return valid.slice(0, normalizedLimit);
  },

  // resolve direct audio URLs for a given YT video/song id
  async stream(id, opts = {}) {
    const videoId = validateVideoId(id);

    let timeout;
    if (opts && typeof opts === "object" && opts.timeout != null) {
      timeout = normalizeTimeout(opts.timeout);
    } else {
      timeout = normalizeTimeout(DEFAULT_TIMEOUT_MS);
    }

    const url = `${INNERTUBE_PLAYER}?key=${API_KEY}`;
    const body = {
      ...getPlayerContext(),
      videoId: videoId,
      racyCheckOk: true,
      contentCheckOk: true,
    };

    let json;
    try {
      json = await fetchJson(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
        timeout,
      });
    } catch (err) {
      const msg = err && err.message ? err.message : String(err);
      const wrapped = new Error(`ytmusic stream failed for "${videoId.slice(0, 30)}": ${msg}`);
      wrapped.cause = err;
      if (err && err.status) wrapped.status = err.status;
      if (err && err.name) wrapped.name = err.name;
      if (err && err.name === "TimeoutError") wrapped.name = "TimeoutError";
      throw wrapped;
    }

    if (json == null) {
      throw new Error(`ytmusic stream: empty response for ${videoId}`);
    }

    // playabilityStatus checks — map common statuses
    const playStatus = json.playabilityStatus;
    if (playStatus && typeof playStatus === "object") {
      const status = String(playStatus.status || "").toUpperCase();
      const reason = parseStringField(playStatus.reason) || parseStringField(playStatus.messages?.[0]) || "";
      if (status === "ERROR" || status === "LOGIN_REQUIRED" || status === "UNPLAYABLE" || status === "AGE_VERIFICATION_REQUIRED") {
        const msg = reason ? `${status}: ${reason}` : `Video ${status.toLowerCase()} (${videoId})`;
        const err = new Error(`ytmusic stream: ${msg}`);
        // Map to 404-like for upstream handling
        if (/not found|unavailable|private|deleted/i.test(msg)) err.status = 404;
        throw err;
      }
      if (status && status !== "OK" && status !== "LIVE_STREAM_OFFLINE") {
        // For unknown non-OK statuses, warn but continue if streamingData exists
        // e.g., "CONTENT_CHECK_REQUIRED" may still have formats
      }
    }

    const streamingData = json.streamingData;
    if (!streamingData || typeof streamingData !== "object") {
      // No streamingData — either no formats or requires login/cipher
      // Check for reason in playabilityStatus
      const reason = parseStringField(playStatus?.reason) || "no streamingData";
      throw new Error(`ytmusic stream: no playable formats for ${videoId}: ${reason}`);
    }

    const rawFormats = [
      ...(Array.isArray(streamingData.adaptiveFormats) ? streamingData.adaptiveFormats : []),
      ...(Array.isArray(streamingData.formats) ? streamingData.formats : []),
    ];

    if (rawFormats.length === 0) {
      throw new Error(`ytmusic stream: empty formats for ${videoId}`);
    }

    const audioOnly = rawFormats.filter((f) => {
      if (!f || typeof f !== "object") return false;
      const mt = typeof f.mimeType === "string" ? f.mimeType : "";
      return /audio/i.test(mt);
    });

    if (audioOnly.length === 0) {
      throw new Error(`ytmusic stream: no audio formats for ${videoId}`);
    }

    // Map and filter signatureCipher — these require JS deciphering and are not directly playable
    const mapped = [];
    let cipherCount = 0;
    for (const f of audioOnly) {
      try {
        // Skip if url missing and signatureCipher present (requires decipher)
        const hasCipher = typeof f.signatureCipher === "string" && f.signatureCipher.trim().length > 0;
        const hasUrl = typeof f.url === "string" && f.url.trim().length > 0 && /^https?:\/\//i.test(f.url.trim());
        if (hasCipher && !hasUrl) {
          cipherCount++;
          continue; // cannot use without deciphering — skip
        }
        if (!hasUrl) {
          // Some entries have cipher but no url — skip
          if (hasCipher) {
            cipherCount++;
            continue;
          }
          // No url and no cipher — skip malformed
          continue;
        }
        const url = f.url.trim();
        // Basic url sanity
        if (!/^https?:\/\//i.test(url)) continue;

        const bitrate = parseBitrate(f.bitrate ?? f.averageBitrate);
        const quality = bitrate ? `${Math.round(bitrate / 1000)}kbps` : parseStringField(f.qualityLabel) || parseStringField(f.quality) || "audio";
        const type = typeof f.mimeType === "string" ? f.mimeType.split(";")[0].trim() : "audio";
        const contentLength = parseContentLength(f.contentLength ?? f.contentLengthMs ?? f.approxDurationMs);
        const approxDurationMs = parseContentLength(f.approxDurationMs);

        mapped.push({
          quality: quality,
          url: url,
          type: type || "audio",
          bitrate: bitrate || undefined,
          contentLength: contentLength,
          approxDurationMs: approxDurationMs,
          // flag for diagnostics — upstream can decide to hide ciphered entries
          encrypted: !!hasCipher,
          // preserve itag for sorting/diagnostics
          itag: f.itag,
        });
      } catch (_) {
        continue;
      }
    }

    if (mapped.length === 0) {
      if (cipherCount > 0) {
        throw new Error(`ytmusic stream: all ${cipherCount} audio formats require signature deciphering (signatureCipher) for ${videoId} — no direct URLs available`);
      }
      throw new Error(`ytmusic stream: no playable audio URLs for ${videoId}`);
    }

    // Sort: prefer mp4/aac (universal Safari support) over webm/opus, then bitrate descending
    mapped.sort((a, b) => {
      const aIsMp4 = String(a.type || "").includes("mp4") ? 0 : 1;
      const bIsMp4 = String(b.type || "").includes("mp4") ? 0 : 1;
      if (aIsMp4 !== bIsMp4) return aIsMp4 - bIsMp4;
      const qa = parseInt(String(a.quality).replace(/\D/g, ""), 10) || a.bitrate || 0;
      const qb = parseInt(String(b.quality).replace(/\D/g, ""), 10) || b.bitrate || 0;
      if (qb !== qa) return qb - qa;
      const ca = a.contentLength || 0;
      const cb = b.contentLength || 0;
      return cb - ca;
    });

    // Return normalized formats (compatible with api.js normalizeStreams)
    const ordered = mapped.map((f) => ({
      quality: f.quality,
      url: f.url,
      type: f.type,
      contentLength: f.contentLength,
      bitrate: f.bitrate,
      itag: f.itag,
      encrypted: !!f.encrypted,
    }));

    return ordered;
  },
};
