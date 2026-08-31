// src/util.js — robust fetch helper, dedupe and UA
// Handles timeout, abort, JSON errors, non-JSON, retry, headers, status, cleanup

const DEFAULT_TIMEOUT_MS = (() => {
  const raw = process.env.FETCH_TIMEOUT_MS;
  if (raw == null || String(raw).trim() === "") return 6000;
  const v = parseInt(String(raw).trim(), 10);
  return Number.isFinite(v) && v > 0 ? Math.min(v, 30000) : 6000;
})();

const MAX_TIMEOUT_MS = 30000;
const MIN_TIMEOUT_MS = 1;
const DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
const UA = (process.env.FETCH_UA && String(process.env.FETCH_UA).trim()) || DEFAULT_UA;

// helpers -------------------------------------------------------------

function normalizeTimeout(val) {
  let n = Number(val);
  if (!Number.isFinite(n) || n <= 0) n = DEFAULT_TIMEOUT_MS;
  n = Math.trunc(n);
  if (n < MIN_TIMEOUT_MS) n = MIN_TIMEOUT_MS;
  if (n > MAX_TIMEOUT_MS) n = MAX_TIMEOUT_MS;
  return n;
}

function normalizeHeaders(input) {
  const out = {};
  if (input && typeof input === "object" && !Array.isArray(input)) {
    for (const [k, v] of Object.entries(input)) {
      if (v === undefined || v === null) continue;
      // keep original casing for wire but coerce value
      out[String(k)] = String(v);
    }
  }
  return out;
}

function hasHeader(headersObj, name) {
  const lower = String(name).toLowerCase();
  return Object.keys(headersObj).some((k) => String(k).toLowerCase() === lower);
}

function getHeader(headersObj, name) {
  const lower = String(name).toLowerCase();
  for (const [k, v] of Object.entries(headersObj)) {
    if (String(k).toLowerCase() === lower) return v;
  }
  return undefined;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function isAbortError(err) {
  if (!err) return false;
  return err.name === "AbortError" || err.name === "TimeoutError" || (err.message && /aborted|timeout/i.test(err.message));
}

function isRetryableStatus(status) {
  return status === 408 || status === 429 || (status >= 500 && status <= 599);
}

function parseRetryAfter(headerVal) {
  if (!headerVal) return null;
  const secs = parseInt(String(headerVal).trim(), 10);
  if (Number.isFinite(secs) && secs >= 0) return secs * 1000;
  // try HTTP-date
  const d = Date.parse(String(headerVal));
  if (Number.isFinite(d)) {
    const diff = d - Date.now();
    if (diff > 0 && diff < 60000) return diff;
  }
  return null;
}

function validateUrl(url) {
  if (typeof url !== "string" || url.trim() === "") {
    throw new TypeError("fetchJson: url must be non-empty string");
  }
  // allow relative? require absolute for fetch
  try {
    new URL(url);
  } catch (_) {
    // if not absolute, still allow — fetch will throw with clearer message
  }
}

async function fetchJson(url, { timeout = DEFAULT_TIMEOUT_MS, headers = {}, method = "GET", body, signal: externalSignal, retries = 1, retryDelay = 250 } = {}) {
  validateUrl(url);

  const normalizedTimeout = normalizeTimeout(timeout);
  const normalizedMethod = typeof method === "string" ? method.toUpperCase() : "GET";
  const allowedBodyMethods = new Set(["POST", "PUT", "PATCH", "DELETE"]);
  let normalizedHeaders = normalizeHeaders(headers);

  // UA handling — always ensure User-Agent unless caller explicitly removed with null (but we filter null, so check case-insensitive)
  if (!hasHeader(normalizedHeaders, "User-Agent")) {
    normalizedHeaders["User-Agent"] = UA;
  }
  if (!hasHeader(normalizedHeaders, "Accept")) {
    normalizedHeaders["Accept"] = "application/json";
  }

  // Normalize retries
  let maxRetries = Number(retries);
  if (!Number.isFinite(maxRetries) || maxRetries < 0) maxRetries = 0;
  maxRetries = Math.min(Math.trunc(maxRetries), 3);
  let baseDelay = Number(retryDelay);
  if (!Number.isFinite(baseDelay) || baseDelay < 0) baseDelay = 250;
  baseDelay = Math.min(Math.trunc(baseDelay), 5000);

  // Body sanity: if body is plain object and content-type json, stringify
  let finalBody = body;
  if (body != null && typeof body === "object" && !(body instanceof Uint8Array) && !(typeof Buffer !== "undefined" && Buffer.isBuffer(body)) && typeof body !== "string") {
    const ctype = getHeader(normalizedHeaders, "Content-Type") || "";
    if (/application\/json/i.test(ctype)) {
      try {
        finalBody = JSON.stringify(body);
      } catch (_) {
        // keep original if stringify fails
      }
    }
  }

  // Ensure fetch exists
  if (typeof fetch !== "function") {
    throw new Error("fetch is not available in this environment");
  }

  let lastError = null;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const ctrl = new AbortController();
    let timer = null;
    let externalListener = null;
    let externalAborted = false;

    // Link external signal if provided
    if (externalSignal) {
      // Validate external signal shape
      if (typeof externalSignal === "object" && typeof externalSignal.aborted === "boolean") {
        if (externalSignal.aborted) {
          const reason = externalSignal.reason || new DOMException("Aborted", "AbortError");
          throw reason instanceof Error ? reason : new DOMException(String(reason), "AbortError");
        }
        externalListener = () => {
          externalAborted = true;
          // propagate abort reason if available
          try {
            ctrl.abort(externalSignal.reason || new DOMException("Aborted via external signal", "AbortError"));
          } catch (_) {
            ctrl.abort();
          }
        };
        try {
          externalSignal.addEventListener("abort", externalListener);
        } catch (_) {
          // fallback for older signal impl
          if (typeof externalSignal.addListener === "function") externalSignal.addListener(externalListener);
        }
      }
    }

    const cleanup = () => {
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
      if (externalSignal && externalListener) {
        try {
          externalSignal.removeEventListener("abort", externalListener);
        } catch (_) {
          try {
            if (typeof externalSignal.removeListener === "function") externalSignal.removeListener(externalListener);
          } catch (_) {}
        }
      }
    };

    try {
      // timeout handling — abort with TimeoutError
      timer = setTimeout(() => {
        try {
          ctrl.abort(new DOMException(`Timeout after ${normalizedTimeout}ms`, "TimeoutError"));
        } catch (_) {
          // DOMException may not be constructible in some envs, fallback to Error
          const e = new Error(`Timeout after ${normalizedTimeout}ms`);
          e.name = "TimeoutError";
          ctrl.abort(e);
        }
      }, normalizedTimeout);

      const res = await fetch(url, {
        method: normalizedMethod,
        body: allowedBodyMethods.has(normalizedMethod) ? finalBody : undefined,
        signal: ctrl.signal,
        headers: normalizedHeaders,
      });

      cleanup();

      // Status handling
      if (!res.ok) {
        // read body snippet for diagnostics (limited)
        let snippet = "";
        let retryAfterMs = null;
        try {
          snippet = await res.text();
        } catch (_) {
          snippet = "";
        }
        snippet = snippet.slice(0, 500);

        // Check Retry-After
        try {
          const ra = res.headers ? res.headers.get("retry-after") || res.headers.get("Retry-After") : null;
          retryAfterMs = parseRetryAfter(ra);
        } catch (_) {}

        const errMsg = `HTTP ${res.status} ${res.statusText || ""}`.trim() + (snippet ? `: ${snippet.slice(0, 200)}` : "");
        const httpErr = new Error(errMsg);
        httpErr.status = res.status;
        httpErr.statusText = res.statusText;
        httpErr.bodySnippet = snippet;
        httpErr.headers = res.headers;

        if (isRetryableStatus(res.status) && attempt < maxRetries) {
          const delay = retryAfterMs != null ? Math.min(retryAfterMs, 5000) : baseDelay * Math.pow(2, attempt) + Math.floor(Math.random() * 100);
          await sleep(delay);
          lastError = httpErr;
          continue;
        }
        throw httpErr;
      }

      // OK — handle JSON parse / non-JSON
      // Use text then JSON.parse for better errors and content-type diagnostics
      let text;
      try {
        text = await res.text();
      } catch (e) {
        throw new Error(`Failed to read response body: ${e.message}`);
      }

      if (text == null || String(text).trim() === "") {
        // empty body where JSON expected — treat as error unless 204
        if (res.status === 204 || res.status === 205) {
          return null;
        }
        throw new Error(`Empty response body (status ${res.status}, content-type: ${res.headers ? res.headers.get("content-type") : "unknown"})`);
      }

      // Check content-type for diagnostics, but still try to parse JSON regardless
      let contentType = "";
      try {
        contentType = res.headers ? (res.headers.get("content-type") || "") : "";
      } catch (_) {}
      const isJsonContent = /application\/json/i.test(contentType) || /\+json/i.test(contentType);

      // If not JSON content-type, we still attempt parse but include warning in error if fails
      try {
        // Trim BOM
        const trimmed = String(text).trim().replace(/^\uFEFF/, "");
        // Handle case where response is JSON but prefixed with )]}'  (e.g., some APIs)
        // We do not strip automatically; let JSON.parse handle or fail with useful error
        return JSON.parse(trimmed);
      } catch (parseErr) {
        const snippet = String(text).slice(0, 500);
        const hint = isJsonContent ? "content-type indicates JSON but body is not valid JSON" : `non-JSON response (content-type: ${contentType || "unknown"})`;
        const err = new Error(`JSON parse error: ${parseErr.message} — ${hint}: ${snippet.slice(0, 300)}`);
        err.cause = parseErr;
        err.bodySnippet = snippet;
        err.contentType = contentType;
        // Parsing errors are not retryable by default, but if maxRetries and transient? don't retry
        throw err;
      }
    } catch (err) {
      cleanup();

      // Handle abort / timeout
      if (isAbortError(err)) {
        // Determine if timeout vs external abort
        let isTimeout = false;
        try {
          isTimeout = ctrl.signal.aborted ? (ctrl.signal.reason && (ctrl.signal.reason.name === "TimeoutError" || /timeout/i.test(ctrl.signal.reason.message || ""))) : false;
        } catch (_) {}
        // fallback: check error itself or signal reason string
        if (!isTimeout && err.name === "TimeoutError") isTimeout = true;
        if (!isTimeout) {
          try {
            const reason = ctrl.signal.reason;
            if (reason && typeof reason.message === "string" && /timeout/i.test(reason.message)) isTimeout = true;
          } catch (_) {}
        }
        // If externalAborted, preserve external reason
        if (externalAborted) {
          // rethrow external abort as-is
          throw err;
        }
        if (isTimeout) {
          const te = new Error(`Timeout after ${normalizedTimeout}ms for ${String(url).slice(0, 100)}`);
          te.name = "TimeoutError";
          te.cause = err;
          te.timeout = normalizedTimeout;
          // Retry timeout if attempts left
          if (attempt < maxRetries) {
            const delay = baseDelay * Math.pow(2, attempt);
            await sleep(delay);
            lastError = te;
            continue;
          }
          throw te;
        }
        // Generic abort
        if (attempt < maxRetries) {
          // aborts typically not retryable, but we treat as retryable if not external
          // Actually do not retry aborts unless timeout; rethrow
        }
        throw err;
      }

      // Network errors (TypeError: fetch failed, etc.) are retryable
      const isNetworkError = err instanceof TypeError || /fetch failed|network|ECONNRESET|ETIMEDOUT|ENOTFOUND/i.test(err.message || "");
      if (isNetworkError && attempt < maxRetries) {
        const delay = baseDelay * Math.pow(2, attempt) + Math.floor(Math.random() * 100);
        await sleep(delay);
        lastError = err;
        continue;
      }

      // If err already has status and is retryable but we missed earlier (e.g., thrown after body)
      if (err && err.status && isRetryableStatus(err.status) && attempt < maxRetries) {
        const delay = baseDelay * Math.pow(2, attempt);
        await sleep(delay);
        lastError = err;
        continue;
      }

      // Unknown error — if retries left and not client error (4xx except 429/408), retry?
      if (attempt < maxRetries && (!err.status || err.status >= 500 || err.status === 429 || err.status === 408)) {
        // Only retry if we haven't exhausted and error seems transient
        if (isNetworkError || err.name === "TimeoutError" || (err.status && isRetryableStatus(err.status))) {
          const delay = baseDelay * Math.pow(2, attempt);
          await sleep(delay);
          lastError = err;
          continue;
        }
      }

      throw err;
    }
  }

  // Should not reach here; throw lastError if any
  if (lastError) throw lastError;
  throw new Error("fetchJson: unknown error after retries");
}

function dedupe(arr, keyFn) {
  if (!Array.isArray(arr)) return [];
  const fn = typeof keyFn === "function" ? keyFn : (x) => x;
  const seen = new Set();
  const out = [];
  for (const item of arr) {
    let k;
    try {
      k = fn(item);
    } catch (_) {
      // skip items where keyFn throws
      continue;
    }
    // treat null, undefined, empty string as invalid keys — skip
    if (k === null || k === undefined || k === "") continue;
    // Handle NaN: String(NaN) is "NaN" which is consistent for Set
    let key;
    if (typeof k === "object") {
      // For objects, try JSON.stringify for value equality, fallback to String
      try {
        key = JSON.stringify(k);
      } catch (_) {
        key = String(k);
      }
      // JSON.stringify(null) would have been caught earlier, but handle "null" etc
      if (key === undefined) key = String(k);
    } else if (typeof k === "symbol") {
      key = String(k);
    } else {
      // primitives: normalize to string to dedupe "1" and 1 together (useful for id dedup)
      key = String(k);
    }
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out;
}

module.exports = { fetchJson, dedupe, UA, DEFAULT_TIMEOUT_MS, DEFAULT_UA };
