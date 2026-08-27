const { fetchJson, MOBILE_USER_AGENT } = require('../util');

const YT_INNERTUBE_KEY = 'AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8';
const YT_INNERTUBE_BASE = 'https://music.youtube.com/youtubei/v1';
const YT_BASE = 'https://music.youtube.com';

const YT_HEADERS = {
  'User-Agent': MOBILE_USER_AGENT,
  'Content-Type': 'application/json',
  'Origin': YT_BASE,
  'Referer': YT_BASE + '/'
};

function normalizeYtResult(video) {
  const videoId = video.videoId || '';
  const title = video.title || '';
  const artists = video.artists || video.artist || [];
  let artistName = '';

  if (Array.isArray(artists) && artists.length > 0) {
    artistName = artists.map(a => a.name || a).join(', ');
  } else if (typeof artists === 'string') {
    artistName = artists;
  } else if (video.artist) {
    artistName = video.artist.name || video.artist;
  }

  const album = video.album || {};
  const albumName = typeof album === 'string' ? album : (album.name || '');

  const durationSec = video.duration_seconds
    || (video.lengthText ? parseInt(video.lengthText.simpleText || video.lengthText.runs?.[0]?.text || '0', 10) : 0);
  const durationMs = (durationSec || 0) * 1000;

  const thumbnails = video.thumbnails || video.thumbnail || [];
  let coverUrl = '';
  if (Array.isArray(thumbnails) && thumbnails.length > 0) {
    coverUrl = thumbnails[thumbnails.length - 1].url || thumbnails[0].url || '';
  } else if (typeof thumbnails === 'object' && thumbnails.url) {
    coverUrl = thumbnails.url;
  }

  if (coverUrl && !coverUrl.startsWith('http')) {
    coverUrl = 'https:' + coverUrl;
  }

  return {
    id: `ytmusic_${videoId}`,
    title,
    artist: artistName,
    album: albumName,
    duration: durationMs,
    coverUrl,
    source: 'ytmusic',
    quality: '',
    streamUrl: '',
    pageUrl: `${YT_BASE}/watch?v=${videoId}`
  };
}

async function searchYtmusic(query, limit = 10) {
  try {
    const payload = {
      context: {
        client: {
          clientName: 'WEB_REMIX',
          clientVersion: '1.20231030.00.00',
          hl: 'en',
          gl: 'US'
        }
      },
      query,
      params: 'EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D'
    };

    const data = await fetchJson(`${YT_INNERTUBE_BASE}/search?key=${YT_INNERTUBE_KEY}`, {
      method: 'POST',
      headers: YT_HEADERS,
      body: JSON.stringify(payload),
      timeout: 10000
    });

    const contents =
      data?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents?.[0]?.musicShelfRenderer?.contents ||
      data?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents?.flatMap(
        s => s.musicShelfRenderer?.contents || []
      ) ||
      [];

    const videos = contents
      .filter(item => item.musicResponsiveListItemRenderer)
      .map(item => {
        const flex = item.musicResponsiveListItemRenderer;
        const videoId = flex.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.runs?.find(
          r => r.navigationEndpoint?.watchEndpoint?.videoId
        )?.navigationEndpoint?.watchEndpoint?.videoId || flex.playlistItemData?.videoId || '';

        const titleRuns = flex.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.runs || [];
        const title = titleRuns.map(r => r.text).join('');

        const artistRuns = flex.flexColumns?.[1]?.musicResponsiveListItemFlexColumnRenderer?.runs || [];
        const artists = artistRuns
          .filter(r => r.navigationEndpoint?.browseEndpoint)
          .map(r => r.text);

        const albumRuns = flex.flexColumns?.[2]?.musicResponsiveListItemFlexColumnRenderer?.runs || [];
        const albumName = albumRuns.map(r => r.text).join('');

        const durationText = flex.flexColumns?.[flex.flexColumns.length - 1]?.musicResponsiveListItemFlexColumnRenderer?.runs?.[0]?.text || '0:00';
        const durationParts = durationText.split(':').map(Number);
        const durationSec = durationParts.length === 2
          ? durationParts[0] * 60 + durationParts[1]
          : durationParts.length === 3
            ? durationParts[0] * 3600 + durationParts[1] * 60 + durationParts[2]
            : 0;

        const thumbnails = flex.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails || [];

        return {
          videoId,
          title,
          artists: artists.length > 0 ? artists.join(', ') : '',
          album: albumName,
          duration_seconds: durationSec,
          thumbnails
        };
      })
      .filter(v => v.videoId && v.title);

    return videos.slice(0, limit).map(v => {
      const normalized = normalizeYtResult(v);
      normalized.quality = 'AAC';
      return normalized;
    });
  } catch (err) {
    console.error('[YTMusic] Search error:', err.message);
    return [];
  }
}

async function getStreamUrl(videoId) {
  try {
    const payload = {
      context: {
        client: {
          clientName: 'ANDROID',
          clientVersion: '19.09.37',
          androidSdkVersion: 30,
          hl: 'en',
          gl: 'US'
        }
      },
      videoId,
      contentCheckOk: true,
      racyCheckOk: true
    };

    const data = await fetchJson(`${YT_INNERTUBE_BASE}/player?key=${YT_INNERTUBE_KEY}`, {
      method: 'POST',
      headers: {
        ...YT_HEADERS,
        'User-Agent': 'com.google.android.youtube/19.09.37 (Linux; U; Android 13) gzip'
      },
      body: JSON.stringify(payload),
      timeout: 10000
    });

    const formats = [
      ...(data.streamingData?.adaptiveFormats || []),
      ...(data.streamingData?.formats || [])
    ];

    const audioFormat = formats
      .filter(f => f.mimeType?.startsWith('audio/'))
      .sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0))[0];

    if (audioFormat?.url) {
      return audioFormat.url;
    }

    if (audioFormat?.signatureCipher) {
      console.warn('[YTMusic] Stream requires cipher decryption, not supported');
      return null;
    }

    return null;
  } catch (err) {
    console.error('[YTMusic] Stream resolve error:', err.message);
    return null;
  }
}

module.exports = { searchYtmusic, getStreamUrl };
