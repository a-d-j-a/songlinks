const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const { searchItunes } = require('./sources/itunes');
const { searchJioSaavn } = require('./sources/jiosaavn');
const { searchYtmusic, getStreamUrl } = require('./sources/ytmusic');
const { dedupeSongs } = require('./util');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

const publicPath = path.join(__dirname, '..', 'public');
if (fs.existsSync(publicPath)) {
  app.use(express.static(publicPath));
}

function parseSources(sourcesParam) {
  if (!sourcesParam) return ['itunes', 'jiosaavn', 'ytmusic'];
  return sourcesParam
    .split(',')
    .map(s => s.trim().toLowerCase())
    .filter(s => ['itunes', 'jiosaavn', 'ytmusic'].includes(s));
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error(`Timeout after ${ms}ms`)), ms)
    )
  ]);
}

app.get('/search', async (req, res) => {
  try {
    const { q, limit = '10', sources } = req.query;

    if (!q || q.trim().length === 0) {
      return res.status(400).json({ error: 'Query parameter "q" is required' });
    }

    const limitNum = Math.min(Math.max(parseInt(limit, 10) || 10, 1), 50);
    const enabledSources = parseSources(sources);

    const searchPromises = enabledSources.map(source => {
      switch (source) {
        case 'itunes':
          return withTimeout(searchItunes(q, limitNum), 10000)
            .catch(err => {
              console.error('[Search] iTunes failed:', err.message);
              return [];
            });
        case 'jiosaavn':
          return withTimeout(searchJioSaavn(q, limitNum), 10000)
            .catch(err => {
              console.error('[Search] JioSaavn failed:', err.message);
              return [];
            });
        case 'ytmusic':
          return withTimeout(searchYtmusic(q, limitNum), 10000)
            .catch(err => {
              console.error('[Search] YTMusic failed:', err.message);
              return [];
            });
        default:
          return Promise.resolve([]);
      }
    });

    const results = await Promise.all(searchPromises);
    const allSongs = results.flat();
    const deduped = dedupeSongs(allSongs);

    res.json({
      query: q,
      limit: limitNum,
      sources: enabledSources,
      count: deduped.length,
      results: deduped
    });
  } catch (err) {
    console.error('[Search] Error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.get('/stream', async (req, res) => {
  try {
    const { id } = req.query;

    if (!id) {
      return res.status(400).json({ error: 'Query parameter "id" is required' });
    }

    if (id.startsWith('ytmusic_')) {
      const videoId = id.replace('ytmusic_', '');
      const streamUrl = await withTimeout(getStreamUrl(videoId), 15000);

      if (!streamUrl) {
        return res.status(404).json({ error: 'Stream not available' });
      }

      return res.json({
        id,
        source: 'ytmusic',
        streamUrl
      });
    }

    if (id.startsWith('itunes_')) {
      const trackId = id.replace('itunes_', '');
      const results = await searchItunes(trackId, 1);
      if (results.length > 0 && results[0].streamUrl) {
        return res.json({
          id,
          source: 'itunes',
          streamUrl: results[0].streamUrl
        });
      }
      return res.status(404).json({ error: 'Stream not available' });
    }

    if (id.startsWith('jiosaavn_')) {
      return res.status(404).json({
        error: 'JioSaavn streams require direct search resolution'
      });
    }

    res.status(404).json({ error: 'Unknown source or invalid id' });
  } catch (err) {
    console.error('[Stream] Error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    uptime: process.uptime(),
    timestamp: new Date().toISOString()
  });
});

app.listen(PORT, () => {
  console.log(`[SongLinks API] Server running on port ${PORT}`);
  console.log(`[SongLinks API] Health: http://localhost:${PORT}/health`);
  console.log(`[SongLinks API] Search: http://localhost:${PORT}/search?q=never+gonna+give+you+up`);
});

module.exports = app;
