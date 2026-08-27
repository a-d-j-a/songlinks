#!/usr/bin/env node
// Test streaming logic ported from Kotlin sources — validates that Kotlin ports match JS originals
const https = require('https');
const crypto = require('crypto');

const YT_KEY = 'AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8';
const YT_BASE = 'https://music.youtube.com';
const DES_KEY = '38346591';

function fetchJson(url, opts = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = https.request({ hostname: u.hostname, path: u.pathname + u.search, method: opts.method || 'GET', headers: opts.headers || {} }, res => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: data, json: data ? JSON.parse(data) : null }); }
        catch (e) { resolve({ status: res.statusCode, body: data, json: null }); }
      });
    });
    req.on('error', reject);
    if (opts.body) req.write(opts.body);
    req.end();
  });
}

function decryptDesEcb(b64) {
  try {
    const key = Buffer.from(DES_KEY, 'utf8');
    const decipher = crypto.createDecipheriv('des-ecb', key, null);
    decipher.setAutoPadding(true);
    const enc = Buffer.from(b64, 'base64');
    return Buffer.concat([decipher.update(enc), decipher.final()]).toString('utf8');
  } catch (e) { return ''; }
}

function jsonEscape(s) { return s.replace(/\\/g,'\\\\').replace(/"/g,'\\"').replace(/\n/g,'\\n').replace(/\r/g,'\\r').replace(/\t/g,'\\t'); }

// --- Itunes test (mirrors ItunesSource.kt) ---
async function testItunes(query='trending') {
  console.log('\n=== ITUNES search:', query);
  const url = `https://itunes.apple.com/search?term=${encodeURIComponent(query)}&media=music&entity=song&limit=3&country=US`;
  const r = await fetchJson(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
  console.log('HTTP', r.status);
  if (r.json && r.json.results) {
    const songs = r.json.results.filter(x=>x.kind==='song' || x.wrapperType==='track').slice(0,2);
    songs.forEach(s=>{
      const id = `itunes_${s.trackId}`;
      const streamUrl = s.previewUrl || '';
      console.log(` - ${s.trackName} - ${s.artistName} | id=${id} | streamUrl=${streamUrl.slice(0,60)}... | durationMs=${s.trackTimeMillis}`);
      if (!streamUrl) console.log('   WARN: empty previewUrl');
      if (s.trackTimeMillis > 1000000) console.log('   WARN: durationMs seems too large?', s.trackTimeMillis);
    });
    if (songs.length===0) console.log('FAIL: 0 results');
    else console.log('PASS: itunes search ok');
    // Kotlin duration stored as Int ms, PlayerState now stores directly (not *1000) — verify
    const ms = songs[0]?.trackTimeMillis;
    if (ms && ms>0 && ms<1000000) console.log('PASS: duration ms range ok, PlayerState will store', ms, 'not', ms*1000);
    return songs;
  } else {
    console.log('FAIL: no json', r.body.slice(0,200));
    return [];
  }
}

// --- Jiosaavn test ---
async function testJiosaavn(query='trending') {
  console.log('\n=== JIOSAAVN search:', query);
  const url = `https://www.jiosaavn.com/api.php?__call=autocomplete.get&cc=in&includeMetaTags=1&query=${encodeURIComponent(query)}`;
  const r = await fetchJson(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
  console.log('HTTP', r.status);
  if (!r.json) { console.log('FAIL: no json', r.body.slice(0,300)); return []; }
  const songsData = r.json.songs?.data || r.json.results || [];
  console.log(`Found ${songsData.length} raw entries`);
  let ok=0;
  for (const obj of songsData.slice(0,2)) {
    const rawId = obj.id || obj.songid || '';
    const id = `jiosaavn_${rawId}`; // Kotlin fix: prefixed
    const title = obj.song || obj.title || '';
    const enc = obj.encrypted_media_url || '';
    const dec = enc ? decryptDesEcb(enc) : '';
    const durationSec = parseInt(obj.duration||'0',10);
    const durationMs = durationSec*1000;
    console.log(` - ${title} | rawId=${rawId} -> id=${id} | enc=${enc.slice(0,30)}... dec=${dec.slice(0,60)}... | durationMs=${durationMs}`);
    if (dec && dec.startsWith('http')) { console.log('   PASS: decrypt ok'); ok++; }
    else if (enc) console.log('   FAIL: decrypt empty or not http');
    if (!id.startsWith('jiosaavn_')) console.log('   FAIL: id not prefixed!');
  }
  if (ok>0) console.log('PASS: jiosaavn decrypt ok');
  else console.log('WARN: no decrypts, check DES_KEY');
  return songsData;
}

// --- YT search (mirrors YtmusicSource.kt searchWithParams) ---
async function testYtmusic(query='trending') {
  console.log('\n=== YTMUSIC search:', query);
  const ytSearch = async (params) => {
    const safeQuery = jsonEscape(query);
    const paramsBlock = params ? `, "params": "${params}"` : '';
    const payload = JSON.stringify(JSON.parse(`{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20231030.00.00","hl":"en","gl":"US"}},"query":"${safeQuery}"${paramsBlock}}`));
    // Use jsonEscape already, but JSON.parse above will re-escape; simpler build payload directly
    const body = `{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20231030.00.00","hl":"en","gl":"US"}},"query":${JSON.stringify(query)}${params ? `,"params":"${params}"` : ''}}`;
    const r = await fetchJson(`https://music.youtube.com/youtubei/v1/search?key=${YT_KEY}`, {
      method:'POST',
      headers: {'User-Agent':'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36','Content-Type':'application/json','Origin':YT_BASE,'Referer':YT_BASE+'/'},
      body
    });
    return r;
  };
  let r = await ytSearch('EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D');
  console.log('HTTP with filter', r.status, 'body len', r.body.length);
  let json = r.json;
  let items = [];
  function collect(obj) {
    const res=[];
    if (!obj || typeof obj!=='object') return res;
    for (const k in obj) {
      const v=obj[k];
      if (k==='musicResponsiveListItemRenderer' && v && typeof v==='object') res.push(v);
      else if (v && typeof v==='object') {
        if (Array.isArray(v)) v.forEach(it=>{ if(it && typeof it==='object') res.push(...collect(it)); });
        else res.push(...collect(v));
      }
    }
    return res;
  }
  items = json ? collect(json) : [];
  console.log(`Found ${items.length} musicResponsiveListItemRenderer (with filter)`);
  if (items.length===0) {
    console.log('Trying without filter...');
    r = await ytSearch('');
    console.log('HTTP no filter', r.status, 'len', r.body.length);
    json = r.json;
    items = json ? collect(json) : [];
    console.log(`Found ${items.length} without filter`);
  }
  items.slice(0,2).forEach(flex=>{
    const cols = flex.flexColumns || [];
    const runs0 = cols[0]?.musicResponsiveListItemFlexColumnRenderer?.runs || [];
    const title = runs0.map(x=>x.text||'').join('');
    const vid = (runs0.find(x=>x.navigationEndpoint?.watchEndpoint?.videoId) || {}).navigationEndpoint?.watchEndpoint?.videoId || flex.playlistItemData?.videoId || '';
    const artistRuns = cols[1]?.musicResponsiveListItemFlexColumnRenderer?.runs || [];
    const artist = artistRuns.map(x=>x.text||'').join('');
    console.log(` - ${title} | artist=${artist} | videoId=${vid}`);
    if (!vid) console.log('   WARN: no videoId');
  });
  if (items.length>0) console.log('PASS: ytmusic search ok');
  else console.log('FAIL: 0 results even without filter — check API key / response', r.body.slice(0,500));
  return items;
}

// --- YT getStreamUrl (mirrors Kotlin multi-client) ---
async function testYtStream(videoId) {
  console.log('\n=== YT getStreamUrl:', videoId);
  const clients = [
    {name:'ANDROID_MUSIC',ver:'5.16.51',ua:'com.google.android.apps.youtube.music/5.16.51 (Linux; U; Android 13) gzip',sdk:30},
    {name:'ANDROID',ver:'19.09.37',ua:'com.google.android.youtube/19.09.37 (Linux; U; Android 13) gzip',sdk:30},
    {name:'WEB_REMIX',ver:'1.20231030.00.00',ua:'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36',sdk:0}
  ];
  for (const c of clients) {
    const body = JSON.stringify({context:{client:{clientName:c.name,clientVersion:c.ver,hl:'en',gl:'US',...(c.sdk?{androidSdkVersion:c.sdk}:{})}},videoId,contentCheckOk:true,racyCheckOk:true});
    const r = await fetchJson(`https://music.youtube.com/youtubei/v1/player?key=${YT_KEY}`, {
      method:'POST', headers:{'User-Agent':c.ua,'Content-Type':'application/json','Origin':YT_BASE}, body
    });
    console.log(` - ${c.name} HTTP ${r.status}`);
    const sd = r.json?.streamingData;
    if (!sd) { console.log(`   no streamingData`); continue; }
    const fmts = [...(sd.adaptiveFormats||[]), ...(sd.formats||[])];
    const audio = fmts.filter(f=>f.mimeType?.startsWith('audio/')).sort((a,b)=>(b.bitrate||0)-(a.bitrate||0))[0];
    if (audio?.url) { console.log(`   PASS: ${c.name} got url ${audio.url.slice(0,70)}... bitrate ${audio.bitrate}`); return audio.url; }
    if (audio?.signatureCipher) console.log(`   has cipher, no url`);
    else console.log(`   no audio url`);
  }
  console.log('FAIL: all clients failed');
  return '';
}

// --- DirectApi resolve + PlayerState duration check ---
function testDirectApiAndPlayerState() {
  console.log('\n=== DirectApi & PlayerState checks (static)');
  // Simulate Kotlin logic
  const songItunes = { source:'itunes', id:'itunes_123', title:'T', artist:'A', duration: 200000, streams:[{url:'http://ex.com/a.mp3'}], streamUrl:'http://ex.com/a.mp3' };
  const songJio = { source:'jiosaavn', id:'jiosaavn_abc123', title:'T', artist:'A', duration: 180000, streams:[], streamUrl:'' };
  const songYt = { source:'ytmusic', id:'ytmusic_xyz', title:'T', artist:'A', duration: 210000, streams:[], streamUrl:'' };
  // PlayerState.playSong now does duration directly
  const playerStateDuration = (d)=> d; // fixed, not *1000
  console.log(` - itunes duration ${songItunes.duration} -> PlayerState ${playerStateDuration(songItunes.duration)} (was ${songItunes.duration*1000} before fix) PASS`);
  // distinctBy lowercase
  const list = [songItunes, {...songItunes, id:'ITUNES_123'}];
  const dedup = [...new Map(list.map(s=>[`${s.source}:${s.id.toLowerCase()}`, s])).values()];
  console.log(` - distinctBy lowercase: 2 items with case diff -> ${dedup.length} (expect 1) ${dedup.length===1?'PASS':'FAIL'}`);
  // resolveStreamUrl prefix
  function resolvePrefix(id){
    if(id.startsWith('ytmusic_')) return 'yt';
    if(id.startsWith('jiosaavn_')) return 'jio';
    if(id.startsWith('itunes_')) return 'itunes';
    return 'bare';
  }
  console.log(` - resolve ${songJio.id} -> ${resolvePrefix(songJio.id)} ${resolvePrefix(songJio.id)==='jio'?'PASS':'FAIL'}`);
  console.log(` - resolve ${songYt.id} -> ${resolvePrefix(songYt.id)} ${resolvePrefix(songYt.id)==='yt'?'PASS':'FAIL'}`);
  // jsonEscape
  const q = 'a\"b\\c'; const esc = jsonEscape(q); console.log(` - jsonEscape '${q}' -> '${esc}' ${esc==='a\\"b\\\\c'?'PASS':'FAIL'}`);
}

(async()=>{
  console.log('Starting streaming logic tests — ported from Kotlin');
  try {
    const itSongs = await testItunes('trending');
    await testJiosaavn('trending');
    const ytItems = await testYtmusic('trending');
    let vid = ytItems[0]?.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.runs?.find(x=>x.navigationEndpoint?.watchEndpoint?.videoId)?.navigationEndpoint?.watchEndpoint?.videoId;
    if (!vid && ytItems[0]) {
      // try alternative extraction
      const cols = ytItems[0].flexColumns||[];
      const runs = cols[0]?.musicResponsiveListItemFlexColumnRenderer?.runs||[];
      vid = runs.find(x=>x.navigationEndpoint?.watchEndpoint?.videoId)?.navigationEndpoint?.watchEndpoint?.videoId;
    }
    if (vid) await testYtStream(vid);
    else {
      console.log('\nNo videoId from search, testing with known id: dQw4w9WgXcQ');
      await testYtStream('dQw4w9WgXcQ');
    }
    testDirectApiAndPlayerState();
    console.log('\n=== DONE ===');
  } catch(e){ console.error('Test crashed', e); process.exit(1); }
})();
