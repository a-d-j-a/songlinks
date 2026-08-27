package com.songlinks.app.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val queue by PlayerState.queue.collectAsState()
    val shuffleEnabled by PlayerState.shuffle.collectAsState()
    val repeatMode by PlayerState.repeatMode.collectAsState()
    val currentIndex by PlayerState.currentIndex.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showQueue by remember { mutableStateOf(false) }
    var isFavorite by remember(currentSong?.id) { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableStateOf(0f) }
    val song = currentSong

    val rotation by animateFloatAsState(targetValue = if (isPlaying) 360f else 0f, label = "artRotation")

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // Blurred background from cover
        if (!song?.cover.isNullOrBlank()) {
            AsyncImage(
                model = song?.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .alpha(0.35f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0A0A0A).copy(alpha = 0.2f),
                                Color(0xFF0A0A0A).copy(alpha = 0.85f),
                                Color(0xFF0A0A0A)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0A0A0A))))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar — large hit area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        when (song?.source?.lowercase()) {
                            "itunes" -> "iTunes"
                            "jiosaavn" -> "JioSaavn"
                            "ytmusic", "youtube" -> "YouTube Music"
                            else -> "SongLinks"
                        },
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Box(modifier = Modifier.size(48.dp)) // balance
            }

            // Album art — premium card with play state rotation hint
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .heightIn(max = 340.dp)
                        .shadow(28.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = PrimaryDark.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E))
                        .graphicsLayer { rotationZ = if (isPlaying) rotation else 0f }
                ) {
                    if (!song?.cover.isNullOrBlank()) {
                        AsyncImage(
                            model = song?.cover,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(72.dp))
                        }
                    }
                    // Subtle play indicator
                    if (!isPlaying) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }

            // Song info — centered, marquee-style
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    song?.title ?: "Not Playing",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    song?.artist ?: "Unknown Artist",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!song?.album.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song?.album ?: "",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Seek bar — custom thumb, buffered
            val progress = if (isSeeking) seekPos else if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Slider(
                    value = progress,
                    onValueChange = { isSeeking = true; seekPos = it },
                    onValueChangeFinished = { isSeeking = false; playerService?.seekTo((seekPos * duration).toLong()) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val displayPos = if (isSeeking) (seekPos * duration).toLong() else position
                    Text(formatDuration(displayPos), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(formatDuration(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main controls — large, accessible
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { PlayerState.toggleShuffle() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                        "Shuffle", tint = if (shuffleEnabled) Accent else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = { playerService?.skipToPrevious() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (isPlaying) playerService?.pause() else playerService?.resume()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(targetState = isPlaying, label = "playPause") { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.Black, modifier = Modifier.size(36.dp)
                        )
                    }
                }
                IconButton(onClick = { playerService?.skipToNext() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { PlayerState.cycleRepeat() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        when (repeatMode) { 2 -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat },
                        "Repeat", tint = if (repeatMode != 0) Accent else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Secondary actions — glass pill row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SmallAction(Icons.Default.FavoriteBorder, "Fav", isFavorite, FavoriteRed, Color.White.copy(alpha = 0.7f), isFavorite) { isFavorite = !isFavorite }
                SmallAction(Icons.Default.Share, "Share", false, Color.White, Color.White.copy(alpha = 0.7f), false) {
                    val s = currentSong; if (s != null) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "${s.title} - ${s.artist}\n${s.pageUrl}")
                        }
                        try { context.startActivity(android.content.Intent.createChooser(intent, "Share")) } catch (_: Exception) {}
                    }
                }
                SmallAction(Icons.Default.QueueMusic, "Queue", showQueue, Accent, Color.White.copy(alpha = 0.7f), showQueue) { showQueue = !showQueue }
                SmallAction(Icons.Default.MusicNote, "Lyrics", false, Accent, Color.White.copy(alpha = 0.7f), false) {
                    val s = currentSong; if (s != null) onNavigateToLyrics(java.net.URLEncoder.encode(s.title, "UTF-8"), java.net.URLEncoder.encode(s.artist, "UTF-8"))
                }
            }

            // Queue sheet — collapsible, not scroll-through
            if (showQueue) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .heightIn(max = 160.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Up Next • ${queue.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text("${currentIndex + 1}/${queue.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(queue, key = { _, s -> s.id }) { idx, song ->
                                val isCurrent = idx == currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) Accent.copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable { playerService?.setQueueAndPlay(queue, idx) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = song.cover, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, color = if (isCurrent) Accent else Color.White, fontSize = 13.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isCurrent) Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    icon: ImageVector,
    desc: String,
    active: Boolean,
    activeTint: Color,
    inactiveTint: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, desc, tint = if (isActive) activeTint else inactiveTint, modifier = Modifier.size(18.dp))
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}
