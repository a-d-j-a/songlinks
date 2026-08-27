package com.songlinks.app.ui.screens.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    var showQueue by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }

    val song = currentSong

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A2E),
                        Color(0xFF0D0D0D)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = OnSurfaceDark,
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (!song?.source.isNullOrBlank()) {
                    val sourceColor = when (song?.source?.lowercase()) {
                        "itunes" -> SourceiTunes
                        "jiosaavn" -> SourceJioSaavn
                        "ytmusic", "youtube" -> SourceYT
                        else -> PrimaryDark
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = sourceColor.copy(alpha = 0.12f),
                        modifier = Modifier.height(24.dp)
                    ) {
                    Text(
                        text = song?.source?.uppercase() ?: "",
                            color = sourceColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            AlbumArt(
                coverUrl = song?.cover,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 48.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            SongInfo(
                title = song?.title ?: "Not Playing",
                artist = song?.artist ?: "Unknown Artist"
            )

            Spacer(modifier = Modifier.height(16.dp))

            SeekBar(
                position = position,
                duration = duration,
                onSeekStarted = { },
                onSeekChanged = { },
                onSeekFinished = { pos ->
                    playerService?.seekTo(pos)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            MainControls(
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onPrevious = { playerService?.skipToPrevious() },
                onPlayPause = {
                    if (isPlaying) playerService?.pause() else playerService?.resume()
                },
                onNext = { playerService?.skipToNext() },
                onShuffle = { PlayerState.toggleShuffle() },
                onRepeat = { PlayerState.cycleRepeat() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) FavoriteRed else ControlButtonColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = {
                    val s = currentSong
                    if (s != null) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "${s.title} - ${s.artist}\n${s.pageUrl}")
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(shareIntent, "Share song")
                        )
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = ControlButtonColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = {
                    showQueue = !showQueue
                }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (showQueue) ActiveControlColor else ControlButtonColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = {
                    val s = currentSong
                    if (s != null) {
                        onNavigateToLyrics(
                            java.net.URLEncoder.encode(s.title, "UTF-8"),
                            java.net.URLEncoder.encode(s.artist, "UTF-8")
                        )
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Lyrics",
                        tint = ControlButtonColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = showQueue,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                QueuePanel(
                    queue = queue,
                    currentIndex = PlayerState.currentIndex.collectAsState().value,
                    onSongClick = { index ->
                        playerService?.setQueueAndPlay(queue, index)
                    },
                    modifier = Modifier.heightIn(max = 200.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumArt(
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = PrimaryDark.copy(alpha = 0.2f),
                        spotColor = PrimaryDark.copy(alpha = 0.3f)
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GradientStart, GradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(80.dp)
                )
            }
        }
    }
}

@Composable
private fun SongInfo(
    title: String,
    artist: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = OnSurfaceDark,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = artist,
            color = OnSurfaceVariantDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeekStarted: (Long) -> Unit,
    onSeekChanged: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit
) {
    var seekPosition by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    val progress = if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
    ) {
        Slider(
            value = if (isSeeking) seekPosition else progress,
            onValueChange = { newProgress ->
                isSeeking = true
                seekPosition = newProgress
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeekFinished((seekPosition * duration).toLong())
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryDark,
                activeTrackColor = PrimaryDark,
                inactiveTrackColor = SeekBarTrack
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPos = if (isSeeking) (seekPosition * duration).toLong() else position
            Text(
                text = formatDuration(displayPos),
                color = OnSurfaceVariantDark,
                fontSize = 12.sp
            )
            Text(
                text = formatDuration(duration),
                color = OnSurfaceVariantDark,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MainControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) ActiveControlColor else ControlButtonColor,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = OnSurfaceDark,
                modifier = Modifier.size(36.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PrimaryDark)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayPause
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OnPrimaryDark,
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = OnSurfaceDark,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = when (repeatMode) {
                    2 -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "Repeat",
                tint = if (repeatMode != 0) ActiveControlColor else ControlButtonColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<SongResult>,
    currentIndex: Int,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Up Next",
            color = OnSurfaceDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(queue) { index, song ->
                val isCurrentSong = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCurrentSong) PrimaryDark.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .clickable { onSongClick(index) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!song.cover.isNullOrBlank()) {
                        AsyncImage(
                            model = song.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(GradientStart.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = if (isCurrentSong) PrimaryDark else OnSurfaceDark,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = OnSurfaceVariantDark,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isCurrentSong) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PrimaryDark)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
