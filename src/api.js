// src/api.js — main Express server
const express = require("express");
const cors = require("cors");
const path = require("path");
const fs = require("fs");
const { dedupe } = require("./util");
const itunes = require("./sources/itunes");
const jiosaavn = require("./sources/jiosaavn");
const ytmusic = require("./sources/ytmusic");
let ytdl = null;
try { ytdl = require("@distube/ytdl-core"); } catch {}
if (!ytdl) try { ytdl = require("ytdl-core"); } catch {}
// ytdl currently fails for i-1TgVAMNnE (Could not extract functions / Failed to find any playable formats) — keep for fallback but not required for 1M cap

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "100kb" }));
app.use(cors());

// Serve modern SPA from public/ — no build step, glassmorphism UI
const publicDir = path.join(__dirname, "..", "public");
app.use(express.static(publicDir, { index: false, extensions: ["html"] }));

const PORT = (() => {
  const p = parseInt(process.env.PORT || "3000", 10);
  return Number.isFinite(p) && p > 0 && p < 65536 ? p : 3000;
})();
const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 20;
const MAX_QUERY_LEN = 300;
const SOURCE_TIMEOUT_MS = (() => {
  const v = parseInt(process.env.SRC_TIMEOUT_MS || "4000", 10);
  return Number.isFinite(v) && v > 0 ? v : 4000;
})();

// sanitize string for safe logging (prevent log injection via CRLF)
function sanitizeForLog(str) {
  return String(str).replace(/[\r\n\t]/g, " ").replace(/[^ -~]/g, "").slice(0, 200);
}

function parseLimit(raw) {
  const n = parseInt(raw, 10);
  if (!Number.isFinite(n) || isNaN(n)) return DEFAULT_LIMIT;
  return Math.min(Math.max(Math.trunc(n), 1), MAX_LIMIT);
}

function normalizeStreams(streams) {
  if (!Array.isArray(streams)) return [];
  return streams
    .map((s) => ({
      quality: s.quality || s.bitrate || "unknown",
      url: s.url || "",
      type: s.type || s.mimeType || "audio",
    }))
    .filter((s) => typeof s.url === "string" && s.url.length > 0);
}

// Aggregate with collision-safe composite key and proper stream merging.
// Uses source:id so itunes "123" and jiosaavn "123" never collide.
// Also deduplicates streams by URL within each aggregated record.
function aggregate(results) {
  const grouped = new Map();
  for (const r of results) {
    if (!r || !r.id || !r.source) continue;
    const key = `${r.source}:${String(r.id)}`;
    const normalized = {
      ...r,
      id: String(r.id),
      streams: dedupe(normalizeStreams(r.streams), (s) => s.url),
    };
    if (!grouped.has(key)) {
      grouped.set(key, { ...normalized, _sources: [r.source], sources: [r.source] });
    } else {
      const existing = grouped.get(key);
      // merge sources
      if (!existing._sources.includes(r.source)) existing._sources.push(r.source);
      if (!existing.sources.includes(r.source)) existing.sources.push(r.source);
      // merge streams, deduped by URL
      const merged = dedupe([...existing.streams, ...normalized.streams], (s) => s.url);
      existing.streams = merged;
      // keep first non-empty fields, but fill missing from later duplicates
      for (const k of ["title", "artist", "album", "cover", "page", "duration", "genre", "language"]) {
        if (!existing[k] && normalized[k]) existing[k] = normalized[k];
      }
    }
  }
  // remove internal _sources before returning, keep public `sources`
  return Array.from(grouped.values()).map((v) => {
    const { _sources, ...rest } = v;
    // ensure streams are still deduped/normalized after merges
    rest.streams = dedupe(normalizeStreams(rest.streams), (s) => s.url);
    if (!rest.sources) rest.sources = _sources || [rest.source];
    return rest;
  });
}

async function safeSearch(adapter, q, limit) {
  const start = Date.now();
  let timer;
  try {
    const timeoutPromise = new Promise((_, rej) => {
      timer = setTimeout(() => rej(new Error(`timeout after ${SOURCE_TIMEOUT_MS}ms`)), SOURCE_TIMEOUT_MS);
    });
    const data = await Promise.race([adapter.search(q, limit), timeoutPromise]);
    const took = Date.now() - start;
    const arr = Array.isArray(data) ? data : [];
    return { ok: true, data: arr, source: adapter.name, took };
  } catch (e) {
    const took = Date.now() - start;
    const msg = e && e.message ? e.message : String(e);
    console.warn(`${adapter.name} search error for "${sanitizeForLog(q)}": ${sanitizeForLog(msg)}`);
    return { ok: false, err: msg, source: adapter.name, took };
  } finally {
    if (timer) clearTimeout(timer);
  }
}

const adapters = { itunes, jiosaavn, ytmusic };
const ALL_SOURCES = Object.keys(adapters);

const _indexPath = path.join(publicDir, "index.html");
app.get("/", (req, res) => {
  const acceptsHtml = req.headers.accept && req.headers.accept.includes("text/html");
  const wantsJson = req.query.format === "json" || (req.headers.accept && req.headers.accept.includes("application/json") && !acceptsHtml);
  if (acceptsHtml && !wantsJson && fs.existsSync(_indexPath)) {
    return res.sendFile(_indexPath);
  }
  // Provide JSON for API clients; browsers without index.html also get JSON
  if (fs.existsSync(_indexPath) && !wantsJson) {
    // If file exists and request is ambiguous (e.g. curl without explicit accept), check if it's likely a browser
    const ua = (req.headers["user-agent"] || "").toLowerCase();
    const isBrowser = ua.includes("mozilla") || ua.includes("chrome") || ua.includes("safari") || ua.includes("webkit");
    if (isBrowser) return res.sendFile(_indexPath);
  }
  res.json({ ok: true, service: "song-links-api", version: "1.0.0" });
});

// Alias for API info (always JSON, useful when / serves UI)
app.get("/api", (req, res) => res.json({ ok: true, service: "song-links-api", version: "1.0.0" }));

app.get("/search", async (req, res, next) => {
  const t0 = Date.now();
  try {
    const rawQ = req.query.q;
    const q = typeof rawQ === "string" ? rawQ.trim() : "";
    if (!q) return res.status(400).json({ ok: false, error: "Missing query parameter 'q'" });
    if (q.length > MAX_QUERY_LEN) {
      return res.status(400).json({ ok: false, error: `Query too long (max ${MAX_QUERY_LEN} chars)` });
    }

    const limit = parseLimit(req.query.limit !== undefined ? req.query.limit : String(DEFAULT_LIMIT));

    // sources parsing: case-insensitive, trim, handle "all"
    const rawSources = typeof req.query.sources === "string" ? req.query.sources : "";
    const sourcesRequested = rawSources
      .split(",")
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);

    let activeSources;
    if (sourcesRequested.length === 0 || sourcesRequested.includes("all")) {
      activeSources = [...ALL_SOURCES];
    } else {
      activeSources = sourcesRequested.filter((s) => adapters[s]);
      if (activeSources.length === 0) {
        return res.status(400).json({
          ok: false,
          error: `Invalid sources. Valid: ${ALL_SOURCES.join(", ")}, all`,
        });
      }
      // dedupe while preserving order
      activeSources = [...new Set(activeSources)];
    }

    const tasks = activeSources.map((name) => safeSearch(adapters[name], q, limit));
    const raw = await Promise.all(tasks);

    // protect against non-array returns from adapters
    const flat = raw.flatMap((r) => (r.ok && Array.isArray(r.data) ? r.data : []));
    const deduped = aggregate(flat);
    // final slice to enforce limit per aggregated results? keep as is, adapters already limited
    const limited = deduped.slice(0, limit * activeSources.length).slice(0, 60);

    const tookMs = Date.now() - t0;

    // per-source diagnostics
    const perSource = raw.map((r) => ({
      source: r.source,
      ok: r.ok,
      count: r.ok ? r.data.length : 0,
      tookMs: r.took,
      ...(r.ok ? {} : { error: r.err }),
    }));

    const hasErrors = raw.some((r) => !r.ok);

    res.json({
      ok: true,
      query: q,
      limit,
      totalResults: limited.length,
      tookMs,
      sources: activeSources,
      perSource,
      ...(hasErrors ? { warnings: raw.filter((r) => !r.ok).map((r) => ({ source: r.source, error: r.err })) } : {}),
      results: limited,
    });
  } catch (e) {
    next(e);
  }
});

app.get("/stream", async (req, res, next) => {
  try {
    let id = typeof req.query.id === "string" ? req.query.id.trim() : "";
    if (!id) return res.status(400).json({ ok: false, error: "Missing id param" });
    // support yt:<videoId> prefix shown in logs
    if (id.startsWith("yt:")) id = id.slice(3).trim();
    // basic validation: YouTube IDs are 11 chars alphanum _-, but allow broader for future
    if (!id || id.length > 100 || /[\s\r\n]/.test(id)) {
      return res.status(400).json({ ok: false, error: "Invalid id param" });
    }
    // timeout wrapper for stream as well
    const start = Date.now();
    let timer;
    const timeoutPromise = new Promise((_, rej) => {
      timer = setTimeout(() => rej(new Error(`timeout after ${SOURCE_TIMEOUT_MS}ms`)), SOURCE_TIMEOUT_MS);
    });
    try {
      const fmts = await Promise.race([ytmusic.stream(id), timeoutPromise]);
      const tookMs = Date.now() - start;
      // normalize and dedupe formats
      const normalized = dedupe(normalizeStreams(fmts), (s) => s.url);
      res.json({ ok: true, videoId: id, tookMs, formats: normalized });
    } finally {
      if (timer) clearTimeout(timer);
    }
  } catch (e) {
    const msg = e && e.message ? e.message : String(e);
    console.warn(`stream error for "${sanitizeForLog(req.query.id || "")}": ${sanitizeForLog(msg)}`);
    // map known errors to appropriate status
    const lower = msg.toLowerCase();
    if (lower.includes("timeout")) return res.status(504).json({ ok: false, error: msg });
    if (lower.includes("not found") || lower.includes("404")) return res.status(404).json({ ok: false, error: msg });
    res.status(502).json({ ok: false, error: msg });
  }
});

app.all("/proxy", async (req, res) => {
  const raw = req.query.url;
  if (!raw || typeof raw !== "string") return res.status(400).json({ ok: false, error: "Missing url param" });
  let target;
  try { target = new URL(raw.trim()); } catch { return res.status(400).json({ ok: false, error: "Invalid url" }); }
  if (!/^https:$/.test(target.protocol)) return res.status(400).json({ ok: false, error: "Only https allowed" });
  // allowlist to prevent open proxy abuse — YT googlevideo, jiosaavn cdn, apple
  const host = target.hostname.toLowerCase();
  const allowed = host.endsWith("googlevideo.com") || host.endsWith("googleusercontent.com") || host.endsWith("ytimg.com") || host.endsWith("ggpht.com") || host.endsWith("saavncdn.com") || host.endsWith("mzstatic.com") || host.endsWith("apple.com");
  if (!allowed) return res.status(403).json({ ok: false, error: "Host not allowed for proxy" });

  try {
    // For googlevideo, use &range= URL param (no Range header) — Range header with large ranges 403, &range= small chunks work
    // For other hosts (saavncdn etc.) use normal Range header passthrough
    const isGoogleVideo = host.endsWith("googlevideo.com");
    const clenStr = target.searchParams.get("clen");
    const clen = clenStr ? parseInt(clenStr, 10) : NaN;
    const total = Number.isFinite(clen) && clen > 0 ? clen : null;

    // Parse client's Range header
    let reqStart = null, reqEnd = null, hasRange = false;
    const rangeHdr = req.headers.range;
    if (rangeHdr && typeof rangeHdr === "string") {
      const m = rangeHdr.trim().match(/^bytes=(\d+)-(\d*)$/);
      if (m) {
        hasRange = true;
        reqStart = parseInt(m[1], 10);
        if (m[2] !== "") reqEnd = parseInt(m[2], 10);
        else if (total) reqEnd = total - 1;
        else reqEnd = reqStart + 1048576;
      }
    }
    // If no Range and googlevideo, default to first 1M for initial probe (browser preload metadata)
    // But if client asked for no Range (full file), we should return full file via 200, not 206
    // For now, if hasRange false and isGoogleVideo, treat as 0-1048576 for probe, but set hasRange true to return 206
    // Actually for audio element, first request is often Range: bytes=0-  or no Range — we handle both
    if (!hasRange && isGoogleVideo) {
      hasRange = true;
      reqStart = 0;
      reqEnd = total ? Math.min(1048576, total - 1) : 1048576;
      // if total is known and small, use total-1
      if (total && total <= 1048577) reqEnd = total - 1;
    }

    let upstream = null;
    let bodyBuffer = null;

    if (isGoogleVideo && hasRange) {
      // Cap at 1M — beyond 1M, videoplayback Range/&range fails (403) even for 500B after first use.
      // Return 416 so browser stops at 1M (~60s @131kbps) without code 2 network error.
      // Full YT needs sabr+poToken (yt-dlp) or use jiosaavn 320kbps for full length.
      if (reqStart > 1048576) {
        res.status(416);
        res.setHeader("Content-Range", `bytes */${total || "*"}`);
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Access-Control-Allow-Headers", "Range, Origin, Referer, User-Agent");
        res.setHeader("Accept-Ranges", "bytes");
        return res.end();
      }
      const start = reqStart, end = reqEnd;
      const cappedEnd = total ? Math.min(end, Math.min(1048576, total - 1)) : Math.min(end, 1048576);
      const getChunkSize = (cur) => cur === 0 ? 1048576 : 10000;
      const chunks = [];
      let cur = start;
      const finalEnd = Math.min(cappedEnd, total ? total - 1 : cappedEnd);
      const effectiveEnd = Math.min(finalEnd, 1048576);
      while (cur <= effectiveEnd) {
        const cs = getChunkSize(cur);
        const cEnd = Math.min(cur + cs, effectiveEnd + 1) - 1;
        let r = null;
        let lastErr = null;
        try {
          r = await fetch(target.toString(), {
            method: "GET",
            headers: {
              "Range": `bytes=${cur}-${cEnd}`,
              "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
              "Referer": "https://music.youtube.com/",
              "Origin": "https://music.youtube.com",
            },
            signal: AbortSignal.timeout(15000),
          });
          if (!r.ok) throw new Error(`Range header ${r.status}`);
        } catch (e) {
          lastErr = e;
          r = null;
        }
        if (!r || !r.ok) {
          try {
            const u = new URL(target.toString());
            u.searchParams.delete("range");
            u.searchParams.set("range", `${cur}-${cEnd}`);
            r = await fetch(u.toString(), {
              method: "GET",
              headers: {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer": "https://music.youtube.com/",
                "Origin": "https://music.youtube.com",
              },
              signal: AbortSignal.timeout(15000),
            });
            if (!r.ok) throw new Error(`&range param ${r.status}`);
          } catch (e) {
            lastErr = e;
            r = null;
          }
        }
        if (!r || !r.ok) {
          // If chunk fails and it's beyond 1M, return what we have so far with 206 (partial)
          if (cur > 0) break;
          const msg = lastErr ? lastErr.message : "unknown";
          throw new Error(`Upstream ${cur}-${cEnd} failed: ${msg}`);
        }
        const buf = Buffer.from(await r.arrayBuffer());
        chunks.push(buf);
        cur = cEnd + 1;
        if (cur > effectiveEnd) break;
      }
      bodyBuffer = Buffer.concat(chunks);
      const retEnd = Math.min(effectiveEnd, start + bodyBuffer.length - 1);
      // Prepare upstream-like object for header copying
      upstream = {
        status: 206,
        headers: {
          get: (n) => {
            const lower = n.toLowerCase();
            if (lower === "content-type") return "audio/mp4";
            if (lower === "content-length") return String(bodyBuffer.length);
            if (lower === "content-range") return `bytes ${start}-${retEnd}/${total || "*"}`;
            if (lower === "accept-ranges") return "bytes";
            return null;
          },
        },
        body: null,
      };
    } else {
      // Non-googlevideo or no Range — normal fetch with Range header passthrough
      const headers = {};
      if (hasRange) headers["Range"] = `bytes=${reqStart}-${reqEnd}`;
      else if (req.headers.range) headers["Range"] = req.headers.range;
      headers["User-Agent"] = req.headers["user-agent"] || "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
      if (req.headers.referer) headers["Referer"] = req.headers.referer;
      if (isGoogleVideo) {
        headers["Referer"] = "https://music.youtube.com/";
        headers["Origin"] = "https://music.youtube.com";
      }
      const r = await fetch(target.toString(), {
        method: "GET",
        headers,
        signal: AbortSignal.timeout(15000),
      });
      upstream = r;
    }

    // Copy status
    res.status(upstream.status);
    // copy relevant headers
    const copyHeaders = ["content-type", "content-length", "content-range", "accept-ranges", "cache-control", "expires", "last-modified", "etag"];
    for (const h of copyHeaders) {
      const v = upstream.headers.get(h);
      if (v) res.setHeader(h, v);
    }
    // Ensure content-type for googlevideo
    if (isGoogleVideo && !res.getHeader("content-type")) {
      // infer from URL mime param or itag
      const mimeParam = target.searchParams.get("mime");
      if (mimeParam) res.setHeader("content-type", decodeURIComponent(mimeParam).split(";")[0]);
      else res.setHeader("content-type", "audio/mp4");
    }
    if (!res.getHeader("accept-ranges")) res.setHeader("accept-ranges", "bytes");
    // CORS
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Headers", "Range, Origin, Referer, User-Agent");
    res.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges, Content-Type");
    res.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
    if (bodyBuffer) {
      // Already fetched via &range chunks
      res.setHeader("content-length", String(bodyBuffer.length));
      if (hasRange) {
        const start = reqStart, end = reqEnd;
        const crEnd = total ? Math.min(end, total - 1) : start + bodyBuffer.length - 1;
        res.setHeader("content-range", `bytes ${start}-${crEnd}/${total || "*"}`);
      }
      return res.end(bodyBuffer);
    }
    if (!upstream.body) return res.end();
    // Stream body
    const reader = upstream.body.getReader ? upstream.body.getReader() : null;
    if (reader) {
      // Web ReadableStream
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          res.write(value);
        }
        res.end();
      } catch (e) {
        if (!res.headersSent) res.status(502).end();
        else res.end();
      }
    } else {
      // Node stream fallback
      const nodeStream = upstream.body;
      nodeStream.on("error", () => { try { res.end(); } catch {} });
      nodeStream.pipe(res);
    }
  } catch (e) {
    const msg = e && e.message ? e.message : String(e);
    console.warn(`proxy error for "${sanitizeForLog(raw.slice(0,80))}": ${sanitizeForLog(msg)}`);
    if (!res.headersSent) res.status(502).json({ ok: false, error: `Proxy failed: ${msg}` });
    else res.end();
  }
});

// support CORS preflight for proxy
app.options("/proxy", (req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Range, Origin, Referer, User-Agent, Content-Type");
  res.status(204).end();
});

// Full-length YT audio via ytdl-core (bypasses 1M Range cap for long tracks)
app.get("/yt-audio", async (req, res) => {
  const rawId = (req.query.id || "").toString().trim();
  const id = rawId.replace(/^yt:/, "").trim();
  if (!id || !/^[a-zA-Z0-9_-]{5,100}$/.test(id)) return res.status(400).json({ ok: false, error: "Invalid id" });
  if (!ytdl) return res.status(500).json({ ok: false, error: "ytdl-core not installed" });
  try {
    const info = await ytdl.getInfo(`https://www.youtube.com/watch?v=${id}`, { requestOptions: { headers: { "User-Agent": "Mozilla/5.0" } } });
    // choose highest audio only, prefer mp4/aac for Safari
    let format = ytdl.chooseFormat(info.formats, { filter: "audioonly", quality: "highestaudio" });
    // fallback: any audio
    if (!format || !format.url) {
      const audios = info.formats.filter(f => f.mimeType && /audio/.test(f.mimeType));
      audios.sort((a,b) => (b.audioBitrate||0)-(a.audioBitrate||0));
      format = audios.find(f=>/mp4/.test(f.mimeType)) || audios[0];
    }
    if (!format || !format.url) return res.status(404).json({ ok: false, error: "No audio format found" });
    const target = new URL(format.url);
    const headers = {};
    if (req.headers.range) headers["Range"] = req.headers.range;
    headers["User-Agent"] = req.headers["user-agent"] || "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    // ytdl URLs are not IP-locked as strictly, but forward referer
    const upstream = await fetch(target.toString(), { method: "GET", headers, signal: AbortSignal.timeout(20000) });
    res.status(upstream.status);
    for (const h of ["content-type","content-length","content-range","accept-ranges","cache-control"]) {
      const v = upstream.headers.get(h);
      if (v) res.setHeader(h, v);
    }
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Headers", "Range, Origin, Referer, User-Agent");
    res.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges, Content-Type");
    res.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
    if (!res.getHeader("accept-ranges")) res.setHeader("accept-ranges", "bytes");
    if (!upstream.body) return res.end();
    const reader = upstream.body.getReader ? upstream.body.getReader() : null;
    if (reader) {
      try { while(true){ const {done,value}=await reader.read(); if(done)break; res.write(value);} res.end(); } catch{ if(!res.headersSent) res.status(502).end(); else res.end(); }
    } else {
      upstream.body.pipe(res);
    }
  } catch (e) {
    const msg = e && e.message ? e.message : String(e);
    console.warn(`yt-audio error for ${sanitizeForLog(id)}: ${sanitizeForLog(msg)}`);
    if (!res.headersSent) res.status(502).json({ ok: false, error: `yt-audio failed: ${msg}` });
    else res.end();
  }
});
app.options("/yt-audio", (req,res)=>{
  res.setHeader("Access-Control-Allow-Origin","*");
  res.setHeader("Access-Control-Allow-Methods","GET, HEAD, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers","Range, Origin, Referer, User-Agent");
  res.status(204).end();
});

app.get("/health", (req, res) => res.json({ ok: true, time: new Date().toISOString(), uptime: process.uptime() }));

// 404 for unknown routes
app.use((req, res) => {
  res.status(404).json({ ok: false, error: "Not found" });
});

// global error handler
app.use((err, req, res, _next) => {
  console.error(`unhandled error on ${sanitizeForLog(req.method)} ${sanitizeForLog(req.path)}:`, err && err.stack ? err.stack : err);
  res.status(500).json({ ok: false, error: "Internal server error" });
});

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`song-links-api listening on http://localhost:${PORT}`);
    console.log(`  /search?q=<query>&limit=10&sources=itunes,jiosaavn,ytmusic`);
    console.log(`  /stream?id=yt:<videoId>`);
  });
}

module.exports = app;
