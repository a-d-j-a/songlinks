const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const { searchItunes } = require('./sources/itunes');
const { searchJioSaavn } = require('./sources/jiosaavn');
const { searchYtmusic, getStreamUrl } = require('./sources/ytmusic');
const { dedupeSongs, fetchJson } = require('./util');
const { generateRecommendations } = require('./recommendations');

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

app.get('/recommendations', async (req, res) => {
  try {
    const { history, sources } = req.query;

    let parsedHistory = [];
    if (history) {
      try {
        parsedHistory = JSON.parse(history);
        if (!Array.isArray(parsedHistory)) {
          parsedHistory = [];
        }
      } catch (e) {
        console.error('[Recommendations] Invalid history JSON:', e.message);
      }
    }

    const enabledSources = parseSources(sources);
    const recommendations = await generateRecommendations(parsedHistory, enabledSources);

    res.json({
      count: recommendations.length,
      recommendations
    });
  } catch (err) {
    console.error('[Recommendations] Error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.get('/lyrics', async (req, res) => {
  try {
    const { title, artist } = req.query;

    if (!title || !artist) {
      return res.status(400).json({ error: 'Query parameters "title" and "artist" are required' });
    }

    const encodedArtist = encodeURIComponent(artist);
    const encodedTitle = encodeURIComponent(title);
    const url = `https://lrclib.net/api/get?artist_name=${encodedArtist}&track_name=${encodedTitle}`;

    const data = await withTimeout(fetchJson(url, { timeout: 10000 }), 10000);

    if (!data) {
      return res.json({ title, artist, lyrics: null, syncedLyrics: null });
    }

    res.json({
      title: data.trackName || title,
      artist: data.artistName || artist,
      lyrics: data.plainLyrics || null,
      syncedLyrics: data.syncedLyrics || null
    });
  } catch (err) {
    console.error('[Lyrics] Error:', err);
    res.json({ title: req.query.title, artist: req.query.artist, lyrics: null, syncedLyrics: null });
  }
});

app.get('/download', async (req, res) => {
  try {
    const { id } = req.query;

    if (!id) {
      return res.status(400).json({ error: 'Query parameter "id" is required' });
    }

    let streamUrl = null;
    let filename = 'song.mp3';

    if (id.startsWith('ytmusic_')) {
      const videoId = id.replace('ytmusic_', '');
      streamUrl = await withTimeout(getStreamUrl(videoId), 15000);
      filename = `${videoId}.mp3`;
    } else if (id.startsWith('itunes_')) {
      const trackId = id.replace('itunes_', '');
      const results = await searchItunes(trackId, 1);
      if (results.length > 0 && results[0].streamUrl) {
        streamUrl = results[0].streamUrl;
        filename = `itunes_${trackId}.mp3`;
      }
    } else {
      return res.status(404).json({ error: 'Unknown source or invalid id' });
    }

    if (!streamUrl) {
      return res.status(404).json({ error: 'Stream not available' });
    }

    const response = await fetch(streamUrl);
    if (!response.ok) {
      return res.status(502).json({ error: 'Failed to fetch audio' });
    }

    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);

    const buffer = Buffer.from(await response.arrayBuffer());
    res.send(buffer);
  } catch (err) {
    console.error('[Download] Error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.post('/backup', (req, res) => {
  try {
    const { songs = [], history = [], playlists = [] } = req.body;

    if (!Array.isArray(songs) || !Array.isArray(history) || !Array.isArray(playlists)) {
      return res.status(400).json({ error: 'Invalid data format' });
    }

    const backup = {
      metadata: {
        timestamp: new Date().toISOString(),
        version: '1.0.0'
      },
      songs,
      history,
      playlists
    };

    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Content-Disposition', `attachment; filename="songlinks_backup_${Date.now()}.json"`);
    res.json(backup);
  } catch (err) {
    console.error('[Backup] Error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.post('/restore', (req, res) => {
  try {
    const backup = req.body;

    if (!backup || typeof backup !== 'object') {
      return res.status(400).json({ error: 'Invalid backup format' });
    }

    if (!backup.metadata || !backup.metadata.timestamp || !backup.metadata.version) {
      return res.status(400).json({ error: 'Missing metadata' });
    }

    const songs = Array.isArray(backup.songs) ? backup.songs : [];
    const history = Array.isArray(backup.history) ? backup.history : [];
    const playlists = Array.isArray(backup.playlists) ? backup.playlists : [];

    res.json({
      restored: true,
      metadata: backup.metadata,
      songs,
      history,
      playlists
    });
  } catch (err) {
    console.error('[Restore] Error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.listen(PORT, () => {
  console.log(`[SongLinks API] Server running on port ${PORT}`);
  console.log(`[SongLinks API] Health: http://localhost:${PORT}/health`);
  console.log(`[SongLinks API] Search: http://localhost:${PORT}/search?q=never+gonna+give+you+up`);
});

module.exports = app;
