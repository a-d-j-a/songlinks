package com.songlinks.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.songlinks.app.api.SongResult
import com.songlinks.app.ui.theme.Card as ThemeCard
import com.songlinks.app.ui.theme.CardBorder
import com.songlinks.app.ui.theme.OnSurfaceVariant
import com.songlinks.app.ui.theme.TextPrimary
import com.songlinks.app.ui.theme.TextSecondary
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onSearch: (String) -> Unit = {},
    onSongTap: (SongResult) -> Unit = {}
) {
    val recentSearches by viewModel.recentSearches.collectAsState()

    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "Composing HomeScreen")
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 6..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(com.songlinks.app.ui.theme.Surface),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "SongLinks",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = com.songlinks.app.ui.theme.Accent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Discover your music",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        item {
            QuickAccessGrid(
                onCardTap = { query -> onSearch(query) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (recentSearches.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Searched",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        TextButton(onClick = { viewModel.clearRecent() }) {
                            Text("Clear", color = com.songlinks.app.ui.theme.Accent)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentSearches, key = { it }) { query ->
                            SuggestionChip(
                                onClick = { onSearch(query) },
                                label = { Text(query) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(
                    text = "Quick Links",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickLinkCard(
                        name = "iTunes",
                        icon = Icons.Filled.MusicNote,
                        color = com.songlinks.app.ui.theme.SourceiTunes,
                        onClick = { onSearch("itunes") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickLinkCard(
                        name = "JioSaavn",
                        icon = Icons.Filled.QueueMusic,
                        color = com.songlinks.app.ui.theme.SourceJioSaavn,
                        onClick = { onSearch("jiosaavn") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickLinkCard(
                        name = "YouTube",
                        icon = Icons.Filled.PlayCircleOutline,
                        color = com.songlinks.app.ui.theme.SourceYT,
                        onClick = { onSearch("youtube") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class CardData(val title: String, val icon: ImageVector, val gradient: Brush, val query: String)

@Composable
private fun QuickAccessGrid(
    onCardTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = remember {
        listOf(
            CardData("Trending", Icons.Filled.TrendingUp, Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))), "trending"),
            CardData("New Releases", Icons.Filled.NewReleases, Brush.linearGradient(listOf(Color(0xFF667EEA), Color(0xFF764BA2))), "new releases"),
            CardData("Charts", Icons.Filled.Leaderboard, Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))), "top charts"),
            CardData("Discover", Icons.Filled.Explore, Brush.linearGradient(listOf(Color(0xFFDA22FF), Color(0xFF9733EE))), "discover")
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = cards[0].title,
                icon = cards[0].icon,
                gradient = cards[0].gradient,
                onClick = { onCardTap(cards[0].query) },
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = cards[1].title,
                icon = cards[1].icon,
                gradient = cards[1].gradient,
                onClick = { onCardTap(cards[1].query) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = cards[2].title,
                icon = cards[2].icon,
                gradient = cards[2].gradient,
                onClick = { onCardTap(cards[2].query) },
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = cards[3].title,
                icon = cards[3].icon,
                gradient = cards[3].gradient,
                onClick = { onCardTap(cards[3].query) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun QuickLinkCard(
    name: String,
    icon: ImageVector,
    color: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = ThemeCard
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
    }
}
