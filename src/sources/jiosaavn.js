const { fetchJson } = require('../util');
const { decryptDesEcb } = require('../des');

const JIOSAAVN_API = 'https://www.jiosaavn.com/api.php';

function normalizeJioSaavnResult(song) {
  const permaUrl = song.perma_url || '';
  const coverRaw = song.image || '';

  let coverUrl = '';
  if (typeof coverRaw === 'string' && coverRaw.startsWith('http')) {
    coverUrl = coverRaw;
  } else if (typeof coverRaw === 'string' && coverRaw.length > 0) {
    try {
      const parsed = JSON.parse(coverRaw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        coverUrl = parsed[parsed.length - 1].link || parsed[0].link || '';
      }
    } catch {
      coverUrl = coverRaw;
    }
  }

  let durationMs = 0;
  if (song.duration) {
    const secs = parseInt(song.duration, 10);
    if (!isNaN(secs)) {
      durationMs = secs * 1000;
    }
  }

  return {
    id: song.id || song.songid || '',
    title: song.song || song.title || '',
    artist: song.singers || song.music || '',
    album: song.album || '',
    duration: durationMs,
    coverUrl,
    source: 'jiosaavn',
    quality: '',
    streamUrl: '',
    pageUrl: permaUrl,
    encryptedMediaUrl: song.encrypted_media_url || '',
    encryptedMediaUrls: song.encrypted_media_urls || ''
  };
}

function buildQualityMap(encryptedMediaUrl) {
  const qualities = ['96', '160', '320'];
  const result = {};

  for (const kbps of qualities) {
    try {
      const decryptedUrl = decryptDesEcb(encryptedMediaUrl);
      if (decryptedUrl) {
        result[kbps] = decryptedUrl;
      }
    } catch {
      // If decryption fails, skip
    }
  }

  return result;
}

async function resolveStreamUrl(song) {
  if (song.encryptedMediaUrl) {
    try {
      const decryptedUrl = decryptDesEcb(song.encryptedMediaUrl);
      if (decryptedUrl && decryptedUrl.startsWith('http')) {
        return { '320': decryptedUrl, '160': decryptedUrl, '96': decryptedUrl };
      }
    } catch (err) {
      console.error('[JioSaavn] DES decrypt failed:', err.message);
    }
  }

  if (song.encryptedMediaUrls) {
    try {
      const parsed = JSON.parse(song.encryptedMediaUrls);
      const result = {};
      for (const [kbps, encrypted] of Object.entries(parsed)) {
        try {
          result[kbps] = decryptDesEcb(encrypted);
        } catch {
          // skip
        }
      }
      if (Object.keys(result).length > 0) return result;
    } catch {
      // skip
    }
  }

  return {};
}

async function searchJioSaavn(query, limit = 10) {
  try {
    const params = new URLSearchParams({
      __call: 'autocomplete.get',
      cc: 'in',
      includeMetaTags: '1',
      query
    });

    const data = await fetchJson(`${JIOSAAVN_API}?${params}`, {
      timeout: 8000,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
      }
    });

    let songs = [];

    if (data.songs && data.songs.data) {
      songs = data.songs.data;
    } else if (Array.isArray(data)) {
      songs = data.filter(item => item.type === 'song' || item.song);
    }

    const normalized = songs.slice(0, limit).map(normalizeJioSaavnResult);

    for (const song of normalized) {
      const qualities = await resolveStreamUrl(song);
      if (qualities['320']) {
        song.streamUrl = qualities['320'];
        song.quality = '320kbps';
      } else if (qualities['160']) {
        song.streamUrl = qualities['160'];
        song.quality = '160kbps';
      } else if (qualities['96']) {
        song.streamUrl = qualities['96'];
        song.quality = '96kbps';
      }
    }

    return normalized.filter(s => s.streamUrl);
  } catch (err) {
    console.error('[JioSaavn] Search error:', err.message);
    return [];
  }
}

module.exports = { searchJioSaavn, resolveStreamUrl };
