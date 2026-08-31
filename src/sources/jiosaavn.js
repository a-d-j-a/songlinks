// src/sources/jiosaavn.js — robust JioSaavn adapter
// Handles QUALITY_LADDER ordering, regex robustness, per-result decrypt try/catch,
// query/limit validation, fetch error handling, artist mapping, missing fields, stream building.

const { fetchJson, DEFAULT_TIMEOUT_MS } = require("../util");
const { decryptBase64 } = require("../des");

const DEFAULT_LIMIT = 10;
const MIN_LIMIT = 1;
const MAX_LIMIT = 50; // api.js caps at 20 globally, but allow 50 for direct use
const MAX_QUERY_LEN = 300;
const SA_AVN_API = "https://www.jiosaavn.com/api.php";

// 5-level quality ladder: low availability -> high fidelity (ascending).
// JioSaavn serves variants via `_<bitrate>.mp4` suffix; 12/48/96/160/320 are the
// canonical ladder (not 96/160/320/1548/3200 which are invalid/out-of-range).
const QUALITY_LADDER = ["12", "48", "96", "160", "320"];

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

function normalizeQuery(q) {
  if (typeof q !== "string") {
    throw new TypeError("jiosaavn.search: query must be a non-empty string");
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

function parseIntField(val) {
  if (val === null || val === undefined || val === "") return undefined;
  const n = typeof val === "string" ? parseInt(val.trim(), 10) : parseInt(val, 10);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

function parseDuration(val) {
  if (val === null || val === undefined || val === "") return undefined;
  const n = typeof val === "string" ? Number(val.trim()) : Number(val);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  const secs = Math.round(n);
  return Number.isFinite(secs) && secs > 0 ? secs : undefined;
}

function parseCoverImage(val) {
  if (typeof val !== "string" || !val) return undefined;
  const trimmed = val.trim();
  if (!trimmed) return undefined;
  // Robust upgrade 150x150 -> 500x500. Use replaceAll for global, fallback to split/join.
  if (trimmed.includes("150x150")) {
    try {
      return trimmed.replaceAll("150x150", "500x500");
    } catch (_) {
      return trimmed.split("150x150").join("500x500");
    }
  }
  // Also handle 50x50, 500x500 already, etc. Return as-is if no pattern.
  return trimmed;
}

function upgradeMediaUrl(url, kbps = "320") {
  if (typeof url !== "string" || !url) return url;
  const trimmed = url.trim();
  if (!trimmed) return trimmed;
  const target = String(kbps).trim();
  if (!target) return trimmed;
  // Must be http(s) url containing _<digits>.mp4 pattern; preserve query string.
  // Example: https://aac.saavncdn.com/070/abc_96.mp4  -> _320.mp4
  // Example with query: ..._96.mp4?Expires=123 -> ..._320.mp4?Expires=123
  // The regex replaces only the bitrate segment and keeps the rest.
  try {
    // Primary: _<digits>.mp4 (case-insensitive), preserves trailing query
    if (/_(\d+)\.mp4/i.test(trimmed)) {
      return trimmed.replace(/_(\d+)\.mp4/i, `_${target}.mp4`);
    }
    // Fallback generic: _<digits>.<ext> where ext is a-z0-9, keep query/ext
    // Defensive for non-mp4 cdn variants (e.g., .m4a, .aac) if ever returned.
    const genericMatch = trimmed.match(/_(\d+)\.([a-z0-9]+)(\?.*)?$/i);
    if (genericMatch) {
      const ext = genericMatch[2];
      const query = genericMatch[3] || "";
      // Replace only the last occurrence to avoid changing earlier path numbers
      return trimmed.replace(/_(\d+)\.([a-z0-9]+)(\?.*)?$/i, `_${target}.${ext}${query}`);
    }
  } catch (_) {
    return trimmed;
  }
  return trimmed;
}

function parseArtists(meta, fallbackSubtitle) {
  if (!meta || typeof meta !== "object") {
    return parseStringField(fallbackSubtitle);
  }
  const primary = Array.isArray(meta.artistMap?.primary_artists) ? meta.artistMap.primary_artists : [];
  const featured = Array.isArray(meta.artistMap?.featured_artists) ? meta.artistMap.featured_artists : [];
  const all = Array.isArray(meta.artistMap?.artists) ? meta.artistMap.artists : [];

  // Combine primary + featured, fallback to all if empty
  let combined = [...primary, ...featured];
  if (combined.length === 0 && all.length) combined = [...all];

  const names = combined
    .map((a) => {
      if (!a) return null;
      if (typeof a === "string") return a.trim();
      if (typeof a === "object") {
        // JioSaavn artist entries typically have { name, id, ... } but be tolerant
        const n = a.name || a.title || a.text || a.subTitle || "";
        try {
          const s = String(n).trim();
          return s || null;
        } catch (_) {
          return null;
        }
      }
      return null;
    })
    .filter(Boolean);

  // Dedupe preserving order
  const uniq = [...new Set(names)];
  if (uniq.length) return uniq.join(", ");

  // Fallbacks: singers, artist, primary_artists string, subtitle
  const fallback =
    parseStringField(meta.singers) ||
    parseStringField(meta.artist) ||
    parseStringField(meta.primary_artists) ||
    parseStringField(fallbackSubtitle);
  return fallback;
}

function buildStreams(decrypted) {
  if (typeof decrypted !== "string" || !decrypted) return [];
  const trimmed = decrypted.trim();
  if (!trimmed) return [];
  if (!/^https?:\/\//i.test(trimmed)) return [];
  const ext = trimmed.includes(".mp4") ? "mp4" : trimmed.includes(".aac") ? "aac" : "audio";
  // Build full 5-level ladder; each url is upgraded variant
  const streams = QUALITY_LADDER.map((q) => {
    const url = upgradeMediaUrl(trimmed, q);
    if (typeof url !== "string" || !url.trim() || !/^https?:\/\//i.test(url.trim())) return null;
    return { quality: `${q}kbps`, url: url.trim(), type: ext };
  }).filter(Boolean);
  return streams;
}

function mapResult(r) {
  if (!r || typeof r !== "object" || Array.isArray(r)) return null;

  const rawId = r.id;
  if (rawId === null || rawId === undefined || rawId === "") return null;
  let idStr;
  try {
    idStr = String(rawId).trim();
  } catch (_) {
    return null;
  }
  if (!idStr || idStr === "null" || idStr === "undefined" || idStr.toLowerCase() === "nan") return null;

  const meta = r.more_info && typeof r.more_info === "object" ? r.more_info : {};

  const title = parseStringField(r.title) || parseStringField(r.song) || parseStringField(meta.title);
  const artist = parseArtists(meta, r.subtitle);
  const album = parseStringField(meta.album) || parseStringField(r.album) || undefined;
  const duration = parseDuration(meta.duration);
  const language = parseStringField(r.language) || parseStringField(meta.language);
  const playCount = parseIntField(r.play_count);
  const cover = parseCoverImage(r.image);
  const page = parseStringField(r.perma_url) || parseStringField(r.url);

  // Per-result decrypt with isolated try/catch so one failure doesn't kill whole search
  let decrypted = null;
  const enc = meta.encrypted_media_url;
  if (typeof enc === "string" && enc.trim()) {
    try {
      decrypted = decryptBase64(enc);
    } catch (_) {
      // Decrypt failed for this single track — keep streams empty, don't throw
      decrypted = null;
    }
    // Validate decrypted result
    if (typeof decrypted === "string") {
      const t = decrypted.trim();
      if (!t || !/^https?:\/\//i.test(t)) decrypted = null;
      else decrypted = t;
    } else {
      decrypted = null;
    }
  }

  const streams = buildStreams(decrypted);

  const isExplicitRaw = r.explicit_content;
  const isExplicit = isExplicitRaw === "1" || isExplicitRaw === 1 || isExplicitRaw === true;
  const isDolby = meta.is_dolby_content === true || meta.is_dolby_content === "true" || meta.is_dolby_content === 1 || meta.is_dolby_content === "1";

  return {
    source: "jiosaavn",
    id: idStr,
    title: title || undefined,
    artist: artist || undefined,
    album: album || undefined,
    duration: duration,
    language: language || undefined,
    playCount: playCount,
    cover: cover,
    page: page,
    streams: streams,
    extra: { isExplicit: !!isExplicit, isDolby: !!isDolby },
  };
}

// ---------------------------------------------------------------------------
// main adapter
// ---------------------------------------------------------------------------

module.exports = {
  name: "jiosaavn",
  // expose for testing if needed
  QUALITY_LADDER,
  upgradeMediaUrl,
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
      timeout = DEFAULT_TIMEOUT_MS;
    }

    const params = new URLSearchParams({
      __call: "search.getResults",
      q: normalizedQ,
      _format: "json",
      _marker: "0",
      api_version: "4",
      ctx: "web6dot0",
      p: "1",
      n: String(normalizedLimit),
    });
    const url = `${SA_AVN_API}?${params}`;

    let json;
    try {
      json = await fetchJson(url, { timeout });
    } catch (err) {
      const msg = err && err.message ? err.message : String(err);
      const wrapped = new Error(`jiosaavn search failed for "${normalizedQ.slice(0, 50)}": ${msg}`);
      wrapped.cause = err;
      if (err && err.status) wrapped.status = err.status;
      if (err && err.name) wrapped.name = err.name;
      if (err && err.name === "TimeoutError") wrapped.name = "TimeoutError";
      throw wrapped;
    }

    if (json == null) return [];

    let resultsArray;
    if (Array.isArray(json.results)) {
      resultsArray = json.results;
    } else if (Array.isArray(json)) {
      resultsArray = json;
    } else if (json.results == null) {
      return [];
    } else {
      throw new Error(`jiosaavn: unexpected response shape (results is ${typeof json.results})`);
    }

    const mapped = [];
    for (const r of resultsArray) {
      try {
        const m = mapResult(r);
        if (m) mapped.push(m);
      } catch (_) {
        continue;
      }
    }

    return mapped;
  },
};
