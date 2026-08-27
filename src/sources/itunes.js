const { fetchJson } = require('../util');

const ITUNES_SEARCH_URL = 'https://itunes.apple.com/search';

function normalizeItunesResult(item) {
  const durationMs = (item.trackTimeMillis || 0);
  const quality = durationMs > 0 ? 'AAC' : '';

  return {
    id: `itunes_${item.trackId}`,
    title: item.trackName || item.collectionName || '',
    artist: item.artistName || '',
    album: item.collectionName || '',
    duration: durationMs,
    coverUrl: (item.artworkUrl100 || '').replace('100x100bb', '300x300bb'),
    source: 'itunes',
    quality,
    streamUrl: item.previewUrl || '',
    pageUrl: item.trackViewUrl || item.collectionViewUrl || ''
  };
}

async function searchItunes(query, limit = 10) {
  try {
    const params = new URLSearchParams({
      term: query,
      media: 'music',
      entity: 'song',
      limit: String(limit),
      country: 'US'
    });

    const data = await fetchJson(`${ITUNES_SEARCH_URL}?${params}`, {
      timeout: 8000
    });

    if (!data.results || !Array.isArray(data.results)) {
      return [];
    }

    return data.results
      .filter(item => item.wrapperType === 'track' || item.kind === 'song')
      .map(normalizeItunesResult);
  } catch (err) {
    console.error('[iTunes] Search error:', err.message);
    return [];
  }
}

module.exports = { searchItunes };
