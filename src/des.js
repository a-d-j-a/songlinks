// src/des.js — pure JS DES ECB used by JioSaavn URL decryption
// Robust error handling, key management, empty-decrypt handling. CryptoJS DES ECB PKCS7 is required for compatibility.

const CryptoJS = require("crypto-js");

const DEFAULT_KEY_STR = (() => {
  const envKey = process.env.DES_KEY || process.env.JIOSAAVN_DES_KEY;
  if (envKey && typeof envKey === "string" && envKey.trim().length >= 4) {
    return envKey.trim();
  }
  return "38346591";
})();

function validateKeyString(str) {
  if (typeof str !== "string") throw new TypeError("DES key must be a string");
  const trimmed = str.trim();
  if (trimmed.length === 0) throw new Error("DES key must not be empty");
  // DES key must be 8 bytes (64-bit). CryptoJS will truncate/pad but we enforce 8 for correctness.
  // For compatibility we allow any 8-char string; if not 8, we warn and derive 8 bytes via truncation/padding.
  // JioSaavn uses exactly 8 ascii chars "38346591".
  const bytes = Buffer.byteLength(trimmed, "utf8");
  if (bytes !== 8) {
    // Not fatal — CryptoJS DES will use first 8 bytes; we normalize to 8 bytes by truncation or zero-pad
    // but emit warning for observability
    // console.warn(`DES key length is ${bytes} bytes, expected 8. Will truncate/pad.`);
  }
  return trimmed;
}

function parseKey(keyStr) {
  const valid = validateKeyString(keyStr);
  // Normalize to exactly 8 bytes: truncate or pad with zero bytes (\0) to avoid CryptoJS implicit handling quirks
  let normalized = valid;
  const len = Buffer.byteLength(normalized, "utf8");
  if (len > 8) {
    // Truncate by byte length, not char length for utf8 safety
    const buf = Buffer.from(normalized, "utf8").subarray(0, 8);
    normalized = buf.toString("utf8");
  } else if (len < 8) {
    const buf = Buffer.alloc(8, 0);
    Buffer.from(normalized, "utf8").copy(buf);
    // Use latin1 to preserve zero bytes; Utf8.parse of string with \0 may drop? Use Utf8.parse of latin1 bytes?
    // Instead construct WordArray via hex
    return CryptoJS.enc.Hex.parse(buf.toString("hex"));
  }
  return CryptoJS.enc.Utf8.parse(normalized);
}

// Cache default parsed key (frozen)
const _DEFAULT_KEY = parseKey(DEFAULT_KEY_STR);
// Freeze wordArray to prevent mutation
try {
  Object.freeze(_DEFAULT_KEY);
  if (_DEFAULT_KEY.words) Object.freeze(_DEFAULT_KEY.words);
} catch (_) {}

// Exported KEY for backward compat — use default
const KEY = _DEFAULT_KEY;

function isValidBase64(str) {
  if (typeof str !== "string") return false;
  const s = str.trim();
  if (s.length === 0 || s.length % 4 !== 0) {
    // Some JioSaavn ciphers may omit padding; allow but check charset
    // Instead of strict %4, just check charset
  }
  // Base64 charset + padding
  return /^[A-Za-z0-9+/=_-]+$/.test(s) && !/\s/.test(s);
}

function normalizeCipherInput(cipherB64) {
  if (cipherB64 === null || cipherB64 === undefined) {
    throw new TypeError("decryptBase64: cipherB64 must be a non-empty string");
  }
  if (typeof cipherB64 !== "string") {
    // Allow Buffer/Uint8Array
    if (typeof Buffer !== "undefined" && Buffer.isBuffer(cipherB64)) {
      cipherB64 = cipherB64.toString("utf8");
    } else if (cipherB64 instanceof Uint8Array) {
      cipherB64 = Buffer.from(cipherB64).toString("utf8");
    } else {
      throw new TypeError(`decryptBase64: expected string, got ${typeof cipherB64}`);
    }
  }
  const trimmed = cipherB64.trim();
  if (trimmed.length === 0) {
    throw new Error("decryptBase64: cipherB64 must not be empty");
  }
  // Remove whitespace/newlines that sometimes appear in JSON payloads
  const cleaned = trimmed.replace(/\s+/g, "");
  if (cleaned.length === 0) throw new Error("decryptBase64: cipherB64 is empty after trimming");
  // URL-safe base64 variant: replace - _ with + /
  // JioSaavn uses standard base64, but be tolerant
  let standard = cleaned.replace(/-/g, "+").replace(/_/g, "/");
  // Pad to multiple of 4
  const pad = standard.length % 4;
  if (pad) standard += "=".repeat(4 - pad);
  if (!isValidBase64(standard)) {
    throw new Error(`decryptBase64: invalid base64 characters in input (len ${cleaned.length})`);
  }
  return standard;
}

/**
 * Decrypt base64 DES ECB PKCS7 cipher.
 * @param {string} cipherB64 - base64 ciphertext
 * @param {object} [opts] - optional { key: string|WordArray }
 * @returns {string} decrypted utf8 string (e.g. https://...)
 * @throws {Error} on invalid input, base64 parse failure, decrypt failure, or empty result
 */
function decryptBase64(cipherB64, opts = {}) {
  const normalizedB64 = normalizeCipherInput(cipherB64);

  // Resolve key
  let keyWordArray = KEY;
  if (opts && opts.key != null) {
    if (typeof opts.key === "string") {
      try {
        keyWordArray = parseKey(opts.key);
      } catch (e) {
        throw new Error(`decryptBase64: invalid custom key: ${e.message}`);
      }
    } else if (typeof opts.key === "object" && opts.key.words && typeof opts.key.sigBytes === "number") {
      // Assume WordArray
      keyWordArray = opts.key;
    } else {
      throw new TypeError("decryptBase64: opts.key must be string or CryptoJS WordArray");
    }
  }

  let enc;
  try {
    enc = CryptoJS.enc.Base64.parse(normalizedB64);
  } catch (e) {
    throw new Error(`decryptBase64: Base64 parse failed: ${e && e.message ? e.message : String(e)}`);
  }

  if (!enc || typeof enc.sigBytes !== "number" || enc.sigBytes === 0) {
    throw new Error("decryptBase64: Base64 parse resulted in empty ciphertext");
  }

  let decrypted;
  try {
    decrypted = CryptoJS.DES.decrypt({ ciphertext: enc }, keyWordArray, {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7,
    });
  } catch (e) {
    // CryptoJS may throw on bad key or malformed cipher
    const msg = e && e.message ? e.message : String(e);
    throw new Error(`DES decrypt failed: ${msg}`);
  }

  if (!decrypted || typeof decrypted.sigBytes !== "number") {
    throw new Error("DES decrypt failed: no result (null/undefined)");
  }

  // sigBytes may be 0 or negative if decrypt failed due to bad padding
  if (decrypted.sigBytes <= 0) {
    throw new Error("DES decrypt failed: empty result (sigBytes <= 0) — likely wrong key or corrupted ciphertext");
  }

  let txt;
  try {
    txt = decrypted.toString(CryptoJS.enc.Utf8);
  } catch (e) {
    // Malformed UTF-8 — occurs when wrong key or not DES ECB
    const msg = e && e.message ? e.message : String(e);
    // Fallback try Latin1 to inspect bytes
    let latin1 = "";
    try {
      latin1 = decrypted.toString(CryptoJS.enc.Latin1);
    } catch (_) {}
    throw new Error(`DES decrypt UTF-8 conversion failed (${msg}); latin1 preview: ${latin1.slice(0, 100)}`);
  }

  if (txt == null || txt.length === 0) {
    // Try to give diagnostic: show hex of decrypted bytes
    let hex = "";
    try {
      hex = decrypted.toString(CryptoJS.enc.Hex).slice(0, 100);
    } catch (_) {}
    throw new Error(`DES decrypt failed: empty string after UTF-8 conversion (hex: ${hex || "n/a"}) — wrong key or corrupted base64?`);
  }

  const trimmed = txt.trim();
  if (trimmed.length === 0) {
    throw new Error("DES decrypt failed: result is whitespace only");
  }

  // Optional sanity: JioSaavn URLs should contain .mp4 or cdn or http; warn but don't fail
  // if (!/^https?:\/\//.test(trimmed)) {
  //   // still return, but caller can validate
  // }

  return trimmed;
}

// Helper to encrypt (for testing) — not exported as primary but useful for verification
function encryptBase64(plainText, keyStr = DEFAULT_KEY_STR) {
  if (typeof plainText !== "string" || plainText.length === 0) throw new TypeError("encryptBase64: plainText must be non-empty string");
  const k = parseKey(keyStr);
  const encrypted = CryptoJS.DES.encrypt(plainText, k, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7,
  });
  // encrypted.ciphertext is WordArray, need base64 of raw ciphertext (not OpenSSL format)
  return encrypted.ciphertext.toString(CryptoJS.enc.Base64);
}

module.exports = { decryptBase64, encryptBase64, KEY, DEFAULT_KEY_STR, parseKey, isValidBase64 };
