package com.songlinks.app.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.songlinks.app.data.local.PlayerState
import com.songlinks.app.player.PlayerService
import com.songlinks.app.ui.theme.*
import kotlinx.coroutines.delay

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
    var isLoading by remember { mutableStateOf(false) }
    val song = currentSong
    val prefs = remember(context) { context.getSharedPreferences("songlinks_prefs", android.content.Context.MODE_PRIVATE) }
    val blurStrength = remember { prefs.getFloat("blur_strength", 36f) }
    val gradientOverlay = remember { prefs.getBoolean("gradient_overlay", true) }
    val hideThumbnail = remember { prefs.getBoolean("hide_thumbnail", false) }
    val cropAlbumArt = remember { prefs.getBoolean("crop_album_art", false) }
    val showCodec = remember { prefs.getBoolean("show_codec", false) }
    val keepScreenOnPref = remember { prefs.getBoolean("keep_screen_on", false) }
    val squigglySlider = remember { prefs.getBoolean("squiggly_slider", false) }
    val canvasEnabled = remember { prefs.getBoolean("canvas_enabled", true) }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(keepScreenOnPref, isPlaying) {
        val window = (view.context as? android.app.Activity)?.window
        if (keepScreenOnPref && isPlaying) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(song?.id) {
        isLoading = true
        // Wait briefly then clear if not playing — avoids flicker
        delay(600)
        if (!isPlaying) isLoading = false
    }
    LaunchedEffect(isPlaying) {
        if (isPlaying) isLoading = false
    }

    val rotation by animateFloatAsState(
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = tween(durationMillis = 20000),
        label = "artRotation"
    )

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // OuterTune/Echo style blurred cover background wired to prefs
        if (!song?.cover.isNullOrBlank() && !hideThumbnail) {
            AsyncImage(
                model = song?.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (blurStrength > 0) it.blur(blurStrength.dp) else it }
                    .alpha(0.45f)
            )
            if (gradientOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0A0A0A).copy(alpha = 0.25f),
                                    Color(0xFF0A0A0A).copy(alpha = 0.55f),
                                    Color(0xFF0A0A0A).copy(alpha = 0.85f),
                                    Color(0xFF0A0A0A)
                                )
                            )
                        )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF141414), Color(0xFF0A0A0A))))
            )
        }

        // Scrollable content — ensures it fits on all devices
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Echo handle + dismiss — always visible
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Spacer(Modifier.height(8.dp))
            // Top bar — dismiss + playing from
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Close", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                    Text(
                        when (song?.source?.lowercase()) {
                            "itunes" -> "iTunes"
                            "jiosaavn" -> "JioSaavn"
                            "ytmusic", "youtube" -> "YouTube Music"
                            else -> "SongLinks"
                        },
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Album art — Echo max 384dp, OuterTune uniform square, wired to hideThumbnail/crop/canvas
            if (!hideThumbnail) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .aspectRatio(1f)
                        .shadow(28.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A1A))
                ) {
                    if (!song?.cover.isNullOrBlank()) {
                        AsyncImage(
                            model = song?.cover,
                            contentDescription = "Album Art",
                            contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .graphicsLayer {
                                    scaleX = if (isPlaying) 1f else 0.98f
                                    scaleY = if (isPlaying) 1f else 0.98f
                                }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(72.dp))
                        }
                    }
                    if (isLoading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            LinearProgressIndicator(modifier = Modifier.width(44.dp).height(3.dp).clip(RoundedCornerShape(2.dp)), color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
                        }
                    }
                    if (canvasEnabled && isPlaying) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.0f)), contentAlignment = Alignment.BottomCenter) {
                            Text("◉ Canvas", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
            } else {
                // Echo Hide Player Thumbnail — minimal spacer
                Spacer(Modifier.height(12.dp))
                Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Song info
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(song?.title ?: "Not Playing", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(song?.artist ?: "Unknown Artist", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            if (showCodec) {
                Text(
                    text = song?.quality?.ifBlank { "AAC" } + " • ${if (song?.source == "jiosaavn") "320kbps" else if (song?.source == "ytmusic") "opus 160k" else "stream"}",
                    color = Accent.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Seek bar wired to squigglySlider pref
            val progress = if (isSeeking) seekPos else if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Slider(
                    value = progress,
                    onValueChange = { isSeeking = true; seekPos = it },
                    onValueChangeFinished = { isSeeking = false; playerService?.seekTo((seekPos * duration).toLong()) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = if (squigglySlider) Accent else Color.White, activeTrackColor = if (squigglySlider) Accent else Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth().height(20.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val displayPos = if (isSeeking) (seekPos * duration).toLong() else position
                    Text(formatDuration(displayPos), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                    Text(formatDuration(duration), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Main controls
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { PlayerState.toggleShuffle() }, modifier = Modifier.size(44.dp)) {
                    Icon(if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle, "Shuffle", tint = if (shuffleEnabled) Accent else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { playerService?.skipToPrevious() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (isPlaying) playerService?.pause() else playerService?.resume()
                        }, contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(targetState = isPlaying, label = "playPause") { playing ->
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
                IconButton(onClick = { playerService?.skipToNext() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { PlayerState.cycleRepeat() }, modifier = Modifier.size(44.dp)) {
                    Icon(when (repeatMode) { 2 -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat }, "Repeat", tint = if (repeatMode != 0) Accent else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Secondary glass pill — OuterTune style
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { isFavorite = !isFavorite }, modifier = Modifier.size(40.dp)) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Fav", tint = if (isFavorite) FavoriteRed else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    val s = currentSong ?: return@IconButton
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "${s.title} - ${s.artist}\n${s.pageUrl}") }
                    try { context.startActivity(android.content.Intent.createChooser(intent, "Share")) } catch (_: Exception) {}
                }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Share, "Share", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showQueue = !showQueue }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.QueueMusic, "Queue", tint = if (showQueue) Accent else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    val s = currentSong ?: return@IconButton
                    onNavigateToLyrics(java.net.URLEncoder.encode(s.title, "UTF-8"), java.net.URLEncoder.encode(s.artist, "UTF-8"))
                }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MusicNote, "Lyrics", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
            }

            // Queue — inline, not overlapping controls
            if (showQueue) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(max = 200.dp),
                    shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A).copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Up Next • ${queue.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${currentIndex + 1}/${queue.size}", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                            itemsIndexed(queue, key = { _, s -> s.id }) { idx, item ->
                                val isCurrent = idx == currentIndex
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isCurrent) Accent.copy(alpha = 0.12f) else Color.Transparent).clickable { playerService?.setQueueAndPlay(queue, idx) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(model = item.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, color = if (isCurrent) Accent else Color.White, fontSize = 13.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.artist, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
