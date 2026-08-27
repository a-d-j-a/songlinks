package com.songlinks.app.ui.screens.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.songlinks.app.ui.theme.Accent
import com.songlinks.app.ui.theme.GradientEnd
import com.songlinks.app.ui.theme.GradientStart
import com.songlinks.app.ui.theme.OnSurfaceVariant
import com.songlinks.app.ui.theme.Surface
import com.songlinks.app.ui.theme.TextPrimary
import com.songlinks.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    positionMs: Long = 0L,
    onBack: () -> Unit = {},
    viewModel: LyricsViewModel = viewModel()
) {
    LaunchedEffect(title, artist) {
        viewModel.fetchLyrics(title, artist)
    }

    LaunchedEffect(positionMs) {
        viewModel.updateCurrentPosition(positionMs)
    }

    val lyricsText by viewModel.lyricsText.collectAsState()
    val syncedLines by viewModel.syncedLines.collectAsState()
    val currentLineIndex by viewModel.currentLineIndex.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasSyncedLyrics by viewModel.hasSyncedLyrics.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(currentLineIndex, hasSyncedLyrics) {
        if (hasSyncedLyrics && currentLineIndex >= 0 && currentLineIndex < syncedLines.size) {
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -200
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GradientStart.copy(alpha = 0.15f),
                        Surface,
                        GradientEnd.copy(alpha = 0.1f)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasSyncedLyrics) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Synced",
                            tint = Accent,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Accent)
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = OnSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = error ?: "No lyrics found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                hasSyncedLyrics -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 24.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(syncedLines) { index, line ->
                            val isActive = index == currentLineIndex
                            val isPast = index < currentLineIndex

                            val textColor by animateColorAsState(
                                targetValue = when {
                                    isActive -> Accent
                                    isPast -> TextSecondary.copy(alpha = 0.5f)
                                    else -> TextPrimary.copy(alpha = 0.7f)
                                },
                                label = "lyrics_color"
                            )

                            val fontSize = if (isActive) 24.sp else 18.sp
                            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                            Text(
                                text = line.text,
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                lyricsText != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 24.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val lines = lyricsText!!.split("\n")
                        itemsIndexed(lines) { _, line ->
                            Text(
                                text = line.ifBlank { "\u00A0" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = OnSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No lyrics found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
