package com.songlinks.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.songlinks.app.ui.theme.Accent
import com.songlinks.app.ui.theme.AccentSurface
import com.songlinks.app.ui.theme.Card
import com.songlinks.app.ui.theme.CardBorder
import com.songlinks.app.ui.theme.OnSurfaceVariant
import com.songlinks.app.ui.theme.SourceJioSaavn
import com.songlinks.app.ui.theme.SourceYT
import com.songlinks.app.ui.theme.SourceiTunes
import com.songlinks.app.api.SongResult
import com.songlinks.app.ui.theme.TextPrimary
import com.songlinks.app.ui.theme.TextSecondary

@Composable
fun SongCard(
    song: SongResult,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    onOpenInBrowser: () -> Unit = {}
) {
    val sourceColor = when (song.source.lowercase()) {
        "itunes" -> SourceiTunes
        "jiosaavn" -> SourceJioSaavn
        "ytmusic", "youtube" -> SourceYT
        else -> OnSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) AccentSurface else Card
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) Accent.copy(alpha = 0.4f) else CardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBorder)
            ) {
                if (song.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SourceBadge(
                        source = song.source,
                        color = sourceColor
                    )
                    if (song.quality.isNotBlank()) {
                        QualityLabel(quality = song.quality)
                    }
                    if (song.durationMs > 0) {
                        DurationLabel(durationMs = song.durationMs)
                    }
                }
            }

            IconButton(onClick = onOpenInBrowser) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = "Open in browser",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = source.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QualityLabel(quality: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Accent.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = quality,
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DurationLabel(durationMs: Long) {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val formatted = "%d:%02d".format(minutes, seconds)

    Text(
        text = formatted,
        style = MaterialTheme.typography.labelSmall,
        color = OnSurfaceVariant
    )
}
