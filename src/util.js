const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

const MOBILE_USER_AGENT = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

async function fetchJson(url, options = {}) {
  const timeout = options.timeout || 10000;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        'User-Agent': USER_AGENT,
        ...options.headers
      }
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

function dedupeResults(results, keyFn) {
  const seen = new Set();
  return results.filter(item => {
    const key = keyFn(item);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function dedupeSongs(songs) {
  return dedupeResults(songs, song => `${song.source}:${song.id}`);
}

module.exports = {
  USER_AGENT,
  MOBILE_USER_AGENT,
  fetchJson,
  dedupeResults,
  dedupeSongs
};
