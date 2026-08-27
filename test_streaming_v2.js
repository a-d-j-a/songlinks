const https=require('https');
const crypto=require('crypto');
const YT_KEY='AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8';
const YT_BASE='https://music.youtube.com';
function fetchJson(url, opts={}){
 return new Promise((res,rej)=>{
   const u=new URL(url);
   const req=https.request({hostname:u.hostname,path:u.pathname+u.search,method:opts.method||'POST',headers:opts.headers||{}}, r=>{
     let d=''; r.on('data',c=>d+=c); r.on('end',()=>{ try{res({status:r.statusCode,body:d,json:JSON.parse(d)});}catch(e){res({status:r.statusCode,body:d,json:null});}});
   }); req.on('error',rej); if(opts.body) req.write(opts.body); req.end();
 });
}
function getRuns(col){
  const obj=col?.musicResponsiveListItemFlexColumnRenderer;
  if(!obj) return null;
  return obj.text?.runs || obj.runs || null;
}
function getPageType(run){
  return run?.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;
}
function parseDuration(text){
  const clean=text.trim().split(' • ').pop().trim();
  const parts=clean.split(':').map(x=>parseInt(x,10)||0);
  if(parts.length===2) return parts[0]*60+parts[1];
  if(parts.length===3) return parts[0]*3600+parts[1]*60+parts[2];
  return 0;
}
(async()=>{
  console.log('=== Test YT parsing with new Kotlin logic (text.runs) ===');
  const body=JSON.stringify({context:{client:{clientName:'WEB_REMIX',clientVersion:'1.20231030.00.00',hl:'en',gl:'US'}},query:'trending',params:'EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D'});
  const r=await fetchJson('https://music.youtube.com/youtubei/v1/search?key='+YT_KEY, {method:'POST', headers:{'User-Agent':'Mozilla/5.0','Content-Type':'application/json','Origin':YT_BASE,'Referer':YT_BASE+'/'}, body});
  console.log('HTTP',r.status,'len',r.body.length);
  function collect(o){const res=[]; if(!o||typeof o!=='object')return res; for(const k in o){const v=o[k]; if(k==='musicResponsiveListItemRenderer'&&v&&typeof v==='object')res.push(v); else if(v&&typeof v==='object'){ if(Array.isArray(v)) v.forEach(it=>{if(it&&typeof it==='object')res.push(...collect(it));}); else res.push(...collect(v));}} return res;}
  const items=collect(r.json);
  console.log('found',items.length,'items');
  let ok=0;
  for(const flex of items.slice(0,5)){
    const cols=flex.flexColumns||[];
    const runs0=getRuns(cols[0]);
    const title=(runs0||[]).map(x=>x.text||'').join('');
    const vid=(runs0||[]).find(x=>x.navigationEndpoint?.watchEndpoint?.videoId)?.navigationEndpoint?.watchEndpoint?.videoId||'';
    // second column parsing like Kotlin
    let artist='', album='', dur=0;
    if(cols.length>1){
      const runs1=getRuns(cols[1]);
      if(runs1){
        const artistParts=[], albumParts=[];
        let durText='';
        for(const el of runs1){
          const t=el.text||'';
          if(t===' • '||t===' & '||!t.trim()) continue;
          const pt=getPageType(el);
          if(pt==='MUSIC_PAGE_TYPE_ARTIST') artistParts.push(t);
          else if(pt==='MUSIC_PAGE_TYPE_ALBUM') albumParts.push(t);
          else if(/^\d+:\d+/.test(t)) durText=t;
        }
        if(artistParts.length) artist=artistParts.join(', ');
        else {
          // fallback: first part before •
          const all=(runs1).map(x=>x.text||'').join('');
          artist=all.split(' • ')[0].trim();
          if(artist.includes(':')) artist='';
        }
        album=albumParts.join('');
        if(durText) dur=parseDuration(durText);
        if(!dur && cols.length>2){
          const lastRuns=getRuns(cols[cols.length-1]);
          const lastText=(lastRuns||[]).map(x=>x.text||'').join('');
          const m=lastText.match(/(\d+:\d+(?::\d+)?)/);
          if(m) dur=parseDuration(m[1]);
        }
        if(!dur){
          const m=(runs1.map(x=>x.text||'').join('').match(/(\d+:\d+)/));
          if(m) dur=parseDuration(m[1]);
        }
      }
    }
    console.log(` - title='${title}' | vid=${vid} | artist='${artist}' | album='${album}' | dur=${dur}`);
    if(title && vid && artist) ok++;
  }
  console.log(`\nYT parse with new logic: ${ok}/5 ok (need title+vid+artist)`);
  if(ok>=3) console.log('PASS: YT new parser works');
  else console.log('FAIL: still broken');

  // Jiosaavn vlink fallback test
  console.log('\n=== JioSaavn vlink fallback ===');
  const jio = await new Promise((res,rej)=>{
    const u=new URL('https://www.jiosaavn.com/api.php?__call=autocomplete.get&cc=in&includeMetaTags=1&query=trending');
    const req=https.request({hostname:u.hostname,path:u.pathname+u.search,method:'GET',headers:{'User-Agent':'Mozilla/5.0'}}, r=>{
      let d=''; r.on('data',c=>d+=c); r.on('end',()=>{ try{res(JSON.parse(d));}catch(e){res(null);}});
    }); req.on('error',rej); req.end();
  });
  const songs=jio?.songs?.data||[];
  let jioOk=0;
  for(const obj of songs.slice(0,3)){
    const vlink=obj.more_info?.vlink||'';
    const enc=obj.encrypted_media_url||'';
    const artist=obj.singers||obj.more_info?.singers||obj.more_info?.primary_artists||'';
    console.log(` - ${obj.title} | artist=${artist} | enc=${enc? 'yes':'no'} | vlink=${vlink.slice(0,50)}...`);
    if(vlink) jioOk++;
  }
  console.log(`Jio vlink fallback: ${jioOk}/3 have vlink ${jioOk>0?'PASS':'FAIL'}`);

  // Now test YT stream with real vid from above
  const realVid=items[0] && (getRuns(items[0].flexColumns?.[0])||[]).find(x=>x.navigationEndpoint?.watchEndpoint?.videoId)?.navigationEndpoint?.watchEndpoint?.videoId;
  console.log('\n=== YT stream test with vid',realVid,' ===');
  if(realVid){
    for(const c of [{name:'WEB_REMIX',ver:'1.20231030.00.00',ua:'Mozilla/5.0',sdk:0},{name:'ANDROID_MUSIC',ver:'5.16.51',ua:'com.google.android.apps.youtube.music/5.16.51 (Linux; U; Android 13) gzip',sdk:30}]){
      const b=JSON.stringify({context:{client:{clientName:c.name,clientVersion:c.ver,hl:'en',gl:'US',...(c.sdk?{androidSdkVersion:c.sdk}:{})}},videoId:realVid,contentCheckOk:true,racyCheckOk:true});
      const rr=await fetchJson('https://music.youtube.com/youtubei/v1/player?key='+YT_KEY,{method:'POST',headers:{'User-Agent':c.ua,'Content-Type':'application/json','Origin':YT_BASE},body:b});
      console.log(` - ${c.name} HTTP ${rr.status} has streamingData=${!!rr.json?.streamingData}`);
      if(rr.json?.streamingData){
        const fmts=[...(rr.json.streamingData.adaptiveFormats||[]),...(rr.json.streamingData.formats||[])];
        const audio=fmts.filter(f=>f.mimeType?.startsWith('audio/')).sort((a,b)=>(b.bitrate||0)-(a.bitrate||0))[0];
        console.log(`   audio url? ${audio?.url ? 'yes '+audio.url.slice(0,60)+'...' : 'no '+JSON.stringify(audio?.signatureCipher||'').slice(0,60)} bitrate ${audio?.bitrate}`);
        if(audio?.url) {console.log('PASS: stream url found'); break;}
      }
    }
  }
})();
