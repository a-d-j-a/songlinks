package com.songlinks.app.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.songlinks.app.api.SongResult
import com.songlinks.app.data.local.PlayerState
import com.songlinks.app.player.PlayerService
import com.songlinks.app.ui.theme.*

@Composable
fun FullPlayerScreen(
    onDismiss: () -> Unit,
    onNavigateToLyrics: (String, String) -> Unit = { _, _ -> },
    playerService: PlayerService?
) {
    val currentSong by PlayerState.currentSong.collectAsState()
    val isPlaying by PlayerState.isPlaying.collectAsState()
    val position by PlayerState.position.collectAsState()
    val duration by PlayerState.duration.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val queue by PlayerState.queue.collectAsState()
    val shuffleEnabled by PlayerState.shuffle.collectAsState()
    val repeatMode by PlayerState.repeatMode.collectAsState()
    val currentIndex by PlayerState.currentIndex.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var isFavorite by remember(currentSong?.id) { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    val song = currentSong

    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D0D), Color(0xFF1A1A2E), Color(0xFF0D0D0D))))
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar — large tappable down arrow
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Close", tint = OnSurfaceDark, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            if (!song?.source.isNullOrBlank()) {
                val c = when (song?.source?.lowercase()) {
                    "itunes" -> SourceiTunes; "jiosaavn" -> SourceJioSaavn
                    "ytmusic", "youtube" -> SourceYT; else -> PrimaryDark
                }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(song!!.source.uppercase(), color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
            }
        }

        // Album art — capped size, centered, 1:1
        Box(
            modifier = Modifier.weight(1f, fill = true).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).heightIn(max = 300.dp).clip(RoundedCornerShape(20.dp))
                    .shadow(20.dp, RoundedCornerShape(20.dp), ambientColor = PrimaryDark.copy(alpha = 0.2f), spotColor = PrimaryDark.copy(alpha = 0.25f))
            ) {
                if (!song?.cover.isNullOrBlank()) {
                    AsyncImage(
                        model = song?.cover, contentDescription = "Album Art", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                    }
                }
            }
        }

        // Song info — always visible
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                song?.title ?: "Not Playing", color = OnSurfaceDark, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song?.artist ?: "Unknown Artist", color = OnSurfaceVariantDark, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(10.dp))

        // Seek bar — compact
        val progress = if (isSeeking) seekPosition else if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Slider(
                value = progress,
                onValueChange = { isSeeking = true; seekPosition = it },
                onValueChangeFinished = { isSeeking = false; playerService?.seekTo((seekPosition * duration).toLong()) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark, inactiveTrackColor = SeekBarTrack),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val displayPos = if (isSeeking) (seekPosition * duration).toLong() else position
                Text(formatDuration(displayPos), color = OnSurfaceVariantDark, fontSize = 11.sp)
                Text(formatDuration(duration), color = OnSurfaceVariantDark, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Main controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ControlBtn(if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle, if (shuffleEnabled) ActiveControlColor else ControlButtonColor, 22.dp) { PlayerState.toggleShuffle() }
            ControlBtn(Icons.Default.SkipPrevious, OnSurfaceDark, 30.dp) { playerService?.skipToPrevious() }
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(PrimaryDark)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (isPlaying) playerService?.pause() else playerService?.resume()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = OnPrimaryDark, modifier = Modifier.size(32.dp))
            }
            ControlBtn(Icons.Default.SkipNext, OnSurfaceDark, 30.dp) { playerService?.skipToNext() }
            ControlBtn(when (repeatMode) { 2 -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat }, if (repeatMode != 0) ActiveControlColor else ControlButtonColor, 22.dp) { PlayerState.cycleRepeat() }
        }

        Spacer(Modifier.height(6.dp))

        // Extra controls — smaller row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { isFavorite = !isFavorite }, modifier = Modifier.size(36.dp)) {
                Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if (isFavorite) FavoriteRed else ControlButtonColor, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = {
                val s = currentSong
                if (s != null) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "${s.title} - ${s.artist}\n${s.pageUrl}")
                    }
                    try { context.startActivity(android.content.Intent.createChooser(intent, "Share song")) } catch (_: Exception) {}
                }
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Share, "Share", tint = ControlButtonColor, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showQueue = !showQueue }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.QueueMusic, "Queue", tint = if (showQueue) ActiveControlColor else ControlButtonColor, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = {
                val s = currentSong
                if (s != null) onNavigateToLyrics(java.net.URLEncoder.encode(s.title, "UTF-8"), java.net.URLEncoder.encode(s.artist, "UTF-8"))
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MusicNote, "Lyrics", tint = ControlButtonColor, modifier = Modifier.size(18.dp))
            }
        }

        if (showQueue) {
            QueuePanel(queue = queue, currentIndex = currentIndex, onSongClick = { idx -> playerService?.setQueueAndPlay(queue, idx) })
        }
    }
}

@Composable
private fun ControlBtn(icon: ImageVector, tint: Color, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun QueuePanel(queue: List<SongResult>, currentIndex: Int, onSongClick: (Int) -> Unit) {
    if (queue.isEmpty()) return
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        itemsIndexed(queue, key = { _, s -> s.id }) { index, song ->
            val isCurrent = index == currentIndex
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) PrimaryDark.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSongClick(index) }.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!song.cover.isNullOrBlank()) {
                    AsyncImage(model = song.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
                } else {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(GradientStart.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = if (isCurrent) PrimaryDark else OnSurfaceDark, fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = OnSurfaceVariantDark, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isCurrent) Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryDark))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
