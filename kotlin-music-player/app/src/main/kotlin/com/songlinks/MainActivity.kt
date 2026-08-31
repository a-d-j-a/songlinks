package com.songlinks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.songlinks.sources.YtMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongLinksApp(vm: SearchViewModel, player: ExoPlayer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var nowPlaying by remember { mutableStateOf<SongResult?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    fun playResult(r: SongResult) {
        nowPlaying = r
        isBuffering = true
        playbackError = null
        scope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    if (r.source == "ytmusic" && r.streams.isEmpty()) {
                        val fmts = YtMusicSource.stream(r.id)
                        fmts.firstOrNull { it.type.contains("mp4") }?.url
                            ?: fmts.firstOrNull()?.url
                            ?: throw IllegalStateException("No audio formats returned")
                    } else {
                        r.streams.firstOrNull { it.quality.contains("320") }?.url
                            ?: r.streams.firstOrNull()?.url
                            ?: throw IllegalStateException("No streams available")
                    }
                }
                withContext(Dispatchers.Main) {
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    player.play()
                    isBuffering = false
                }
            } catch (e: Exception) {
                isBuffering = false
                playbackError = e.message?.take(100) ?: "Playback failed"
                Toast.makeText(context, "Play error: ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF070A14), Color(0xFF0F1323))))
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
                    contentAlignment = Alignment.Center
                ) { Text("\u266A", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("SongLinks", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("iTunes \u2022 JioSaavn \u2022 YT Music", color = Color.White.copy(0.5f), fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Search bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    placeholder = { Text("Search \u2014 e.g. Kesariya, Daft Punk") },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedContainerColor = Color.White.copy(0.05f),
                        unfocusedContainerColor = Color.White.copy(0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF7C3AED)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.search() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    enabled = !vm.isLoading
                ) { Text("Search") }
            }

            // Source toggles
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("itunes" to "iTunes", "jiosaavn" to "JioSaavn", "ytmusic" to "YT").forEach { (k, l) ->
                    val active = vm.sources.contains(k)
                    FilterChip(
                        selected = active,
                        onClick = {
                            vm.sources = if (vm.sources.contains(k)) vm.sources - k else vm.sources + k
                        },
                        label = { Text(l, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C3AED).copy(0.3f)
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${vm.limit} results",
                    color = Color.White.copy(0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Slider(
                value = vm.limit.toFloat(),
                onValueChange = { vm.limit = it.toInt().coerceIn(1, 20) },
                valueRange = 1f..20f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED)
                )
            )

            // Per-source diagnostics
            if (vm.perSource.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    vm.perSource.forEach { ps ->
                        val col = if (ps.ok) Color(0xFF10B981) else Color(0xFFEF4444)
                        Text(
                            "${ps.source} ${ps.count}\u2022${ps.tookMs}ms",
                            color = col,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Color.White.copy(0.08f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Loading / error
            if (vm.isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFA78BFA), modifier = Modifier.size(32.dp))
                }
            }
            vm.error?.let {
                Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            // Results grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.results, key = { "${it.source}:${it.id}" }) { r ->
                    val isPlaying = nowPlaying?.source == r.source && nowPlaying?.id == r.id
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlaying) Color(0xFF7C3AED).copy(0.15f) else Color.White.copy(0.06f)
                        ),
                        modifier = Modifier.clickable { playResult(r) }
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0B1022))
                            ) {
                                AsyncImage(
                                    model = r.cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Source badge
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(5.dp)
                                        .background(Color.Black.copy(0.7f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(r.source, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                // Playing indicator
                                if (isPlaying) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(24.dp)
                                            .background(Color(0xFF7C3AED), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isBuffering) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("\u25B6", color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                                // Sources badge (multi-source)
                                val srcs = r.sources
                                if (srcs != null && srcs.size > 1) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .background(Color.Black.copy(0.7f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "${srcs.size} sources",
                                            color = Color(0xFF10B981),
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }
                            Text(
                                r.title ?: "Untitled",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                r.artist ?: "Unknown",
                                color = Color.White.copy(0.6f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = listOfNotNull(r.album, r.language).joinToString(" \u2022 ")
                            val dur = r.duration?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "\u2014"
                            Text(
                                "$meta \u2022 $dur",
                                color = Color.White.copy(0.35f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Stream count badge
                            if (r.streams.isNotEmpty()) {
                                Text(
                                    "${r.streams.size} streams",
                                    color = Color(0xFF7C3AED).copy(0.7f),
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Now playing bar
            AnimatedVisibility(visible = nowPlaying != null) {
                nowPlaying?.let { np ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF12162A))
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0B1022))
                            ) {
                                AsyncImage(
                                    model = np.cover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    np.title ?: "",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    np.artist ?: "",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                playbackError?.let {
                                    Text(it, color = Color(0xFFEF4444), fontSize = 9.sp, maxLines = 1)
                                }
                            }
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = Color(0xFFA78BFA),
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = {
                                    if (player.isPlaying) player.pause() else player.play()
                                }) {
                                    Text(
                                        if (player.isPlaying) "\u23F8" else "\u25B6",
                                        color = Color.White,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
