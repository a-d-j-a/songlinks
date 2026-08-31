package com.songlinks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.songlinks.sources.YtMusicSource
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private val vm: SearchViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SongLinksApp(vm, player)
            }
        }
    }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

@Composable
fun SongLinksApp(vm: SearchViewModel, player: ExoPlayer) {
    val scope = rememberCoroutineScope()
    var nowPlaying by remember { mutableStateOf<SongResult?>(null) }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF070A14), Color(0xFF0F1323)))) ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))), contentAlignment = Alignment.Center){ Text("♪", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column { Text("SongLinks", color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Bold); Text("iTunes • JioSaavn • YT Music", color=Color.White.copy(0.5f), fontSize=10.sp) }
            }
            Spacer(Modifier.height(16.dp))
            // Search bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value=vm.query, onValueChange={vm.query=it}, placeholder={Text("Search — e.g. Kesariya, Daft Punk")}, modifier=Modifier.weight(1f).clip(RoundedCornerShape(16.dp)), singleLine=true)
                Spacer(Modifier.width(8.dp))
                Button(onClick={vm.search()}, shape=RoundedCornerShape(16.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7C3AED))){ Text("Search") }
            }
            // Source toggles + limit
            Row(Modifier.fillMaxWidth().padding(top=12.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("all" to "All","itunes" to "iTunes","jiosaavn" to "JioSaavn","ytmusic" to "YT").forEach{ (k,l) ->
                        val active = if(k=="all") vm.sources.size==3 else vm.sources.contains(k)
                        FilterChip(selected=active, onClick={
                            vm.sources = if(k=="all") { if(vm.sources.size==3) emptySet() else setOf("itunes","jiosaavn","ytmusic") }
                            else { if(vm.sources.contains(k)) vm.sources - k else vm.sources + k }
                        }, label={Text(l, fontSize=11.sp)})
                    }
                }
                Text("limit ${vm.limit}", color=Color.White.copy(0.6f), fontSize=12.sp)
            }
            Slider(value=vm.limit.toFloat(), onValueChange={vm.limit=it.toInt().coerceIn(1,20)}, valueRange=1f..20f, modifier=Modifier.fillMaxWidth())
            if(vm.perSource.isNotEmpty()){
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp), modifier=Modifier.padding(top=6.dp)){
                    vm.perSource.forEach{ ps ->
                        val col = if(ps.ok) Color(0xFF10B981) else Color(0xFFEF4444)
                        Text("${ps.source} ${ps.count}•${ps.tookMs}ms", color=col, fontSize=10.sp, modifier=Modifier.background(Color.White.copy(0.08f), RoundedCornerShape(20.dp)).padding(horizontal=8.dp, vertical=4.dp))
                    }
                }
            }
            if(vm.isLoading) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment=Alignment.Center){ CircularProgressIndicator(color=Color(0xFFA78BFA)) } }
            vm.error?.let{ Text(it, color=Color(0xFFFCA5A5), fontSize=12.sp, modifier=Modifier.padding(top=8.dp)) }

            LazyVerticalGrid(columns=GridCells.Adaptive(160.dp), modifier=Modifier.weight(1f).padding(top=12.dp), verticalArrangement=Arrangement.spacedBy(12.dp), horizontalArrangement=Arrangement.spacedBy(12.dp)){
                items(vm.results){ r ->
                    Card(shape=RoundedCornerShape(16.dp), colors=CardDefaults.cardColors(containerColor=Color.White.copy(0.06f)), modifier=Modifier.clickable{
                        nowPlaying=r
                        scope.launch {
                            val url = if(r.source=="ytmusic" || r.streams.isEmpty()){
                                // Resolve YT via ANDROID 20.10.38 (1M cap) then proxy
                                try {
                                    val fmts = YtMusicSource.stream(r.id)
                                    // ytdl full-length would be via /yt-audio — here direct 1M proxied
                                    fmts.firstOrNull{ it.type.contains("mp4") }?.url ?: fmts.firstOrNull()?.url
                                } catch(_:Exception){ null }
                            } else r.streams.firstOrNull{ it.quality.contains("320") }?.url ?: r.streams.firstOrNull()?.url
                            url?.let{
                                // For YT, wrap via local proxy for CORS/Range (1M cap)
                                val playUrl = if(r.source=="ytmusic") "http://10.0.2.2:3000/proxy?url=${java.net.URLEncoder.encode(it,"UTF-8")}" else it
                                player.setMediaItem(MediaItem.fromUri(playUrl)); player.prepare(); player.play()
                            }
                        }
                    }){
                        Column(Modifier.padding(8.dp)){
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B1022))){
                                AsyncImage(model=r.cover, contentDescription=null, contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize())
                                Box(Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(20.dp)).padding(horizontal=6.dp, vertical=2.dp)){
                                    Text(r.source, color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Bold)
                                }
                            }
                            Text(r.title ?: "Untitled", color=Color.White, fontSize=13.sp, fontWeight=FontWeight.SemiBold, maxLines=2, modifier=Modifier.padding(top=6.dp))
                            Text(r.artist ?: "Unknown", color=Color.White.copy(0.6f), fontSize=11.sp, maxLines=1)
                            Text("${r.album ?: r.language ?: ""} • ${r.duration?.let{ "${it/60}:${"%02d".format(it%60)}" } ?: "—"}", color=Color.White.copy(0.4f), fontSize=10.sp, maxLines=1)
                        }
                    }
                }
            }
            nowPlaying?.let{ np ->
                Card(Modifier.fillMaxWidth().padding(top=8.dp), shape=RoundedCornerShape(16.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF12162A))){
                    Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically){
                        AsyncImage(model=np.cover, contentDescription=null, modifier=Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){ Text(np.title?:"", color=Color.White, fontWeight=FontWeight.Bold, fontSize=13.sp); Text(np.artist?:"", color=Color.White.copy(0.6f), fontSize=11.sp) }
                        Button(onClick={ if(player.isPlaying) player.pause() else player.play() }, shape=RoundedCornerShape(20.dp)){ Text(if(player.isPlaying) "Pause" else "Play") }
                    }
                }
            }
        }
    }
}
