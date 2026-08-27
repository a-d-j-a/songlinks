const { searchItunes } = require('./sources/itunes');
const { searchJioSaavn } = require('./sources/jiosaavn');
const { searchYtmusic } = require('./sources/ytmusic');
const { dedupeSongs } = require('./util');

function analyzeHistory(history) {
  if (!history || !Array.isArray(history)) return [];

  const artistCounts = {};
  history.forEach(item => {
    const artist = item.artist || item.artistName;
    if (artist) {
      const key = artist.toLowerCase().trim();
      artistCounts[key] = (artistCounts[key] || 0) + 1;
    }
  });

  return Object.entries(artistCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([artist]) => artist);
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error(`Timeout after ${ms}ms`)), ms)
    )
  ]);
}

async function searchArtistAcrossSources(artist, sources, limit = 3) {
  const searchPromises = sources.map(source => {
    switch (source) {
      case 'itunes':
        return withTimeout(searchItunes(artist, limit), 10000)
          .catch(() => []);
      case 'jiosaavn':
        return withTimeout(searchJioSaavn(artist, limit), 10000)
          .catch(() => []);
      case 'ytmusic':
        return withTimeout(searchYtmusic(artist, limit), 10000)
          .catch(() => []);
      default:
        return Promise.resolve([]);
    }
  });

  const results = await Promise.all(searchPromises);
  return results.flat();
}

async function getTrending(sources, limit = 3) {
  const queries = ['popular', 'trending', 'top hits'];
  const allResults = [];

  for (const query of queries) {
    const results = await searchArtistAcrossSources(query, sources, limit);
    allResults.push(...results);
  }

  return allResults;
}

function shuffleArray(array) {
  const shuffled = [...array];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
}

async function generateRecommendations(history, sources = ['itunes', 'jiosaavn', 'ytmusic']) {
  const topArtists = analyzeHistory(history);

  let recommendations = [];

  if (topArtists.length > 0) {
    for (const artist of topArtists) {
      const artistResults = await searchArtistAcrossSources(artist, sources, 2);
      recommendations.push(...artistResults);
    }
  }

  const trendingResults = await getTrending(sources, 3);
  recommendations.push(...trendingResults);

  const deduped = dedupeSongs(recommendations);

  const shuffled = shuffleArray(deduped);

  return shuffled.slice(0, 20);
}

module.exports = {
  generateRecommendations
};
