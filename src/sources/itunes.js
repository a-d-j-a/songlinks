// src/sources/itunes.js — robust iTunes Search API adapter
// Handles query/limit validation, fetch error propagation, timeout, and resilient response mapping.

const { fetchJson, DEFAULT_TIMEOUT_MS } = require("../util");

const ITUNES_BASE = "https://itunes.apple.com/search";
const DEFAULT_LIMIT = 10;
const MIN_LIMIT = 1;
const MAX_LIMIT = 50; // iTunes API supports up to 200; cap at 50 for sanity, api.js caps at 20
const MAX_QUERY_LEN = 300;
const ARTWORK_SMALL = "100x100";
const ARTWORK_LARGE = "600x600";

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

function normalizeQuery(q) {
  if (typeof q !== "string") {
    throw new TypeError("itunes.search: query must be a non-empty string");
  }
  const trimmed = q.trim();
  if (!trimmed) {
    // Defensive: api.js already rejects empty, but direct calls should not hit network with empty query.
    // Return null sentinel so caller can return [] gracefully instead of throwing.
    return null;
  }
  if (trimmed.length > MAX_QUERY_LEN) {
    // Truncate to avoid 414 URI Too Long; api.js rejects >300, but we clamp gracefully
    return trimmed.slice(0, MAX_QUERY_LEN);
  }
  return trimmed;
}

function normalizeLimit(raw) {
  // Mirrors api.js parseLimit but allows slightly higher cap (50) for direct usage
  // Accept number, numeric string, float — clamp to [MIN_LIMIT, MAX_LIMIT]
  if (raw === undefined || raw === null || raw === "") return DEFAULT_LIMIT;
  // Use parseInt to be tolerant of strings like "10abc" (api.js uses parseInt)
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

function upgradeArtwork(url) {
  // Robustly upgrade 100x100 -> 600x600. Handles 100x100bb variant and repeated occurrences.
  if (typeof url !== "string" || !url) return undefined;
  const trimmed = url.trim();
  if (!trimmed) return undefined;
  // Prefer global replace; iTunes URLs are like .../100x100bb.jpg
  if (trimmed.includes(ARTWORK_SMALL)) {
    // Use split/join for global replace without regex escaping issues, or regex
    try {
      return trimmed.replaceAll(ARTWORK_SMALL, ARTWORK_LARGE);
    } catch (_) {
      // Fallback for older Node without replaceAll
      return trimmed.split(ARTWORK_SMALL).join(ARTWORK_LARGE);
    }
  }
  return trimmed;
}

function resolveArtwork(r) {
  // Prefer artworkUrl100, fallback to 60/30, then upgrade
  if (!r || typeof r !== "object") return undefined;
  const candidate =
    (typeof r.artworkUrl100 === "string" && r.artworkUrl100.trim() ? r.artworkUrl100 : null) ||
    (typeof r.artworkUrl60 === "string" && r.artworkUrl60.trim() ? r.artworkUrl60 : null) ||
    (typeof r.artworkUrl30 === "string" && r.artworkUrl30.trim() ? r.artworkUrl30 : null);
  if (!candidate) return undefined;
  const upgraded = upgradeArtwork(candidate);
  return upgraded || candidate;
}

function parseDuration(trackTimeMillis) {
  // trackTimeMillis -> seconds (rounded). Guard against non-numeric, null, NaN, Infinity.
  if (trackTimeMillis === null || trackTimeMillis === undefined) return undefined;
  // Accept string numeric as well (e.g., "210000")
  const num = typeof trackTimeMillis === "string" ? Number(trackTimeMillis.trim()) : Number(trackTimeMillis);
  if (!Number.isFinite(num) || num <= 0) return undefined;
  const secs = Math.round(num / 1000);
  return Number.isFinite(secs) && secs > 0 ? secs : undefined;
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

function buildStreams(r) {
  if (!r || typeof r !== "object") return [];
  const url = r.previewUrl;
  if (typeof url !== "string") return [];
  const trimmed = url.trim();
  if (!trimmed) return [];
  // Basic URL sanity: must be http(s)
  if (!/^https?:\/\//i.test(trimmed)) return [];
  return [{ quality: "preview", url: trimmed, type: "audio" }];
}

function isExplicit(r) {
  // iTunes uses trackExplicitness === "explicit" or "notExplicit", also "explicit" / "cleaned"
  // Old code used explicitness !== "notExplicit" which incorrectly marks undefined as explicit
  if (!r || typeof r !== "object") return false;
  const v = r.trackExplicitness || r.explicitness || r.collectionExplicitness;
  if (typeof v !== "string") return false;
  const lower = v.trim().toLowerCase();
  return lower === "explicit";
}

function mapResult(r) {
  // Returns mapped object or null if r is invalid / missing id
  if (!r || typeof r !== "object" || Array.isArray(r)) return null;

  // trackId is required — skip entries without it (prevents "undefined" ids)
  const rawId = r.trackId;
  if (rawId === null || rawId === undefined || rawId === "") return null;
  // Ensure id is string and not "null"/"undefined"/"NaN"
  let idStr;
  try {
    idStr = String(rawId).trim();
  } catch (_) {
    return null;
  }
  if (!idStr || idStr === "null" || idStr === "undefined" || idStr.toLowerCase() === "nan") return null;

  const title = parseStringField(r.trackName);
  const artist = parseStringField(r.artistName);
  const album = parseStringField(r.collectionName);
  const duration = parseDuration(r.trackTimeMillis);
  const release = parseStringField(r.releaseDate);
  const genre = parseStringField(r.primaryGenreName);
  const cover = resolveArtwork(r);
  const page = parseStringField(r.trackViewUrl) || parseStringField(r.collectionViewUrl);
  const streams = buildStreams(r);

  return {
    source: "itunes",
    id: idStr,
    // Keep keys always present for downstream aggregation; undefined fields will be filtered by caller
    title: title || undefined,
    artist: artist || undefined,
    album: album || undefined,
    duration: duration,
    release: release,
    genre: genre,
    cover: cover,
    page: page,
    streams: streams,
    extra: { isExplicit: isExplicit(r) },
  };
}

// ---------------------------------------------------------------------------
// main adapter
// ---------------------------------------------------------------------------

module.exports = {
  name: "itunes",
  async search(q, limit = DEFAULT_LIMIT, opts = {}) {
    // ---- query validation ----
    const normalizedQ = normalizeQuery(q);
    if (normalizedQ === null) {
      // Empty/whitespace-only query — graceful early return, no network call
      return [];
    }
    const normalizedLimit = normalizeLimit(limit);

    // ---- timeout handling ----
    // Allow caller to override via opts.timeout or env; fallback to DEFAULT_TIMEOUT_MS (util.js)
    let timeout;
    if (opts && typeof opts === "object" && opts.timeout != null) {
      timeout = normalizeTimeout(opts.timeout);
    } else if (typeof limit === "object" && limit !== null && limit.timeout != null) {
      // Support search(q, {timeout: ...}) misuse? not needed but defensive
      timeout = normalizeTimeout(limit.timeout);
    } else {
      timeout = DEFAULT_TIMEOUT_MS;
      // Also respect SRC_TIMEOUT_MS / FETCH_TIMEOUT_MS if set via env (util already does)
      // No extra env parsing needed; fetchJson will normalize anyway
    }

    const url = `${ITUNES_BASE}?term=${encodeURIComponent(normalizedQ)}&media=music&entity=song&limit=${normalizedLimit}`;

    let json;
    try {
      json = await fetchJson(url, { timeout });
    } catch (err) {
      // Enhance error with context, preserve original stack and status
      const msg = err && err.message ? err.message : String(err);
      const wrapped = new Error(`itunes search failed for "${normalizedQ.slice(0, 50)}": ${msg}`);
      wrapped.cause = err;
      if (err && err.status) wrapped.status = err.status;
      if (err && err.name) wrapped.name = err.name;
      // Preserve TimeoutError name for upstream handling (api.js maps timeout -> 504)
      if (err && err.name === "TimeoutError") wrapped.name = "TimeoutError";
      throw wrapped;
    }

    // fetchJson may return null for 204/205 (util.js) — treat as empty result, not error
    if (json == null) return [];

    // ---- response shape robustness ----
    // iTunes should return { resultCount, results: [...] } but handle missing / non-array / null
    let resultsArray;
    if (Array.isArray(json.results)) {
      resultsArray = json.results;
    } else if (Array.isArray(json)) {
      // Some mocks may return array directly
      resultsArray = json;
    } else if (json.results == null) {
      return [];
    } else {
      // results exists but not array — malformed response
      throw new Error(`itunes: unexpected response shape (results is ${typeof json.results})`);
    }

    // Map & filter invalid entries; never throw inside map due to missing fields
    const mapped = [];
    for (const r of resultsArray) {
      try {
        const m = mapResult(r);
        if (m) mapped.push(m);
      } catch (_) {
        // Skip malformed entries — do not fail whole search for one bad record
        continue;
      }
    }

    return mapped;
  },
};
