package com.songlinks.app.ui.screens.playlists

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.songlinks.app.api.SongResult
import com.songlinks.app.data.local.PlaylistEntity
import com.songlinks.app.data.local.PlaylistSongEntity
import com.songlinks.app.ui.theme.Accent
import com.songlinks.app.ui.theme.Card as ThemeCard
import com.songlinks.app.ui.theme.CardBorder
import com.songlinks.app.ui.theme.OnSurfaceVariant
import com.songlinks.app.ui.theme.Surface
import com.songlinks.app.ui.theme.TextPrimary
import com.songlinks.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onSongTap: (SongResult) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: PlaylistsViewModel = viewModel()
) {
    LaunchedEffect(Unit) { Log.d("PlaylistsScreen", "Composing") }

    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val playlistCounts by viewModel.playlistCounts.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (selectedPlaylist != null) {
        PlaylistDetailScreen(
            playlist = selectedPlaylist!!,
            songs = playlistSongs,
            onSongTap = onSongTap,
            onRemoveSong = { songId ->
                viewModel.removeSongFromPlaylist(selectedPlaylist!!.id, songId)
            },
            onBack = { viewModel.deselectPlaylist() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Playlists")
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Surface
            )
        )

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = OnSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No playlists yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Create your first playlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = Accent
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Create Playlist")
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            songCount = playlistCounts[playlist.id] ?: 0,
                            onClick = { viewModel.selectPlaylist(playlist) }
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = Accent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Playlist")
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = CardBorder,
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    songCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCard),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(CardBorder)
            ) {
                if (!playlist.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(playlist.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = Accent.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$songCount songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    songs: List<PlaylistSongEntity>,
    onSongTap: (SongResult) -> Unit,
    onRemoveSong: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        TopAppBar(
            title = { Text(playlist.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Surface
            )
        )

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = OnSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No songs in this playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Add songs from search or library",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(songs.size, key = { songs[it].id }) { index ->
                    val song = songs[index]
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                onRemoveSong(song.songId)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                    else -> Color.Transparent
                                },
                                label = "swipe_color"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove",
                                    tint = Color.White
                                )
                            }
                        }
                    ) {
                    PlaylistSongCard(
                        song = song,
                        onClick = {
                            onSongTap(
                                com.songlinks.app.api.SongResult(
                                    source = song.source,
                                    id = song.songId,
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    cover = song.cover,
                                    page = song.page,
                                    duration = song.duration
                                )
                            )
                        }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSongCard(
    song: PlaylistSongEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCard),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBorder)
            ) {
                if (!song.cover.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.cover)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
