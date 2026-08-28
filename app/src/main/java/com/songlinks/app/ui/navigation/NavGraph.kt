package com.songlinks.app.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.songlinks.app.ui.components.MiniPlayer
import com.songlinks.app.ui.screens.downloads.DownloadsScreen
import com.songlinks.app.ui.screens.foryou.ForYouScreen
import com.songlinks.app.ui.screens.home.HomeScreen
import com.songlinks.app.ui.screens.library.LibraryScreen
import com.songlinks.app.ui.screens.lyrics.LyricsScreen
import com.songlinks.app.ui.screens.player.FullPlayerScreen
import com.songlinks.app.ui.screens.playlists.PlaylistsScreen
import com.songlinks.app.ui.screens.search.SearchScreen
import com.songlinks.app.ui.screens.settings.SettingsScreen
import com.songlinks.app.data.local.PlayerState
import com.songlinks.app.player.PlayerService
import com.songlinks.app.ui.theme.Accent
import com.songlinks.app.ui.theme.OnSurfaceVariant
import com.songlinks.app.ui.theme.Surface
import com.songlinks.app.ui.theme.SurfaceVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

private const val TAG = "NavGraph"

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("foryou", "For You", Icons.Filled.Person, Icons.Outlined.Person),
    BottomNavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    BottomNavItem("downloads", "Downloads", Icons.Filled.Download, Icons.Outlined.Download),
    BottomNavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

private fun playSongFromNav(
    playerService: PlayerService?,
    song: com.songlinks.app.api.SongResult,
    scope: CoroutineScope,
    context: Context
) {
    Log.d(TAG, "playSongFromNav: ${song.title} by ${song.artist} (source: ${song.source}, id: ${song.id})")
    val isPreview = song.quality.contains("preview", ignoreCase = true) || song.source.equals("itunes", ignoreCase = true)
    val directUrl = if (isPreview) null else (song.streams.firstOrNull()?.url?.takeIf { it.isNotBlank() } ?: song.streamUrl.takeIf { it.isNotBlank() })
    if (!directUrl.isNullOrBlank()) {
        Log.d(TAG, "Playing directly: ${directUrl.take(80)}")
        PlayerState.playSong(song)
        if (playerService != null) {
            playerService.play(directUrl, song.title, song.artist)
        } else {
            Log.w(TAG, "playerService is null, cannot play")
        }
    } else {
        val reason = if (isPreview) "preview fallback" else "empty url"
        Log.d(TAG, "Stream URL $reason, resolving via DirectApi for id: ${song.id} title=${song.title}")
        PlayerState.playSong(song)
        scope.launch(Dispatchers.IO) {
            try {
                val api = com.songlinks.app.api.DirectApi
                val resolvedUrl = api.resolveStreamUrl(song.id, song.title, song.artist)
                Log.d(TAG, "Resolved URL: ${resolvedUrl.take(80)}")
                if (resolvedUrl.isNotBlank()) {
                    val isFallback = song.quality.contains("preview", ignoreCase = true) || song.source.equals("itunes", ignoreCase = true)
                    val updatedSong = song.copy(
                        source = if (isFallback) "ytmusic" else song.source,
                        streams = listOf(com.songlinks.app.api.Stream(quality = if (isFallback) "AAC" else song.quality.ifBlank { "stream" }, url = resolvedUrl)),
                        streamUrl = resolvedUrl,
                        quality = if (isFallback) "AAC" else song.quality
                    )
                    withContext(Dispatchers.Main) {
                        PlayerState.playSong(updatedSong)
                        if (playerService != null) {
                            playerService.play(resolvedUrl, song.title, song.artist)
                        } else {
                            Log.e(TAG, "playerService null after resolve")
                        }
                    }
                } else {
                    Log.e(TAG, "Could not resolve stream URL for ${song.id}")
                    withContext(Dispatchers.Main) { PlayerState.setPlaying(false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving stream URL for ${song.id}", e)
                withContext(Dispatchers.Main) { PlayerState.setPlaying(false) }
            }
        }
    }
}

@Composable
fun SongLinksNavGraph(playerService: PlayerService? = null, activity: com.songlinks.app.MainActivity? = null) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Log.d(TAG, "SongLinksNavGraph: currentRoute=$currentRoute")

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = Surface,
        bottomBar = {
            Column {
                val currentSong by PlayerState.currentSong.collectAsState()

                AnimatedVisibility(
                    visible = currentSong != null && !isPlayerExpanded,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    MiniPlayer(
                        onNavigateToFullPlayer = {
                            Log.d(TAG, "Player expanded")
                            isPlayerExpanded = true
                        },
                        playerService = playerService
                    )
                }

                AnimatedVisibility(
                    visible = !isPlayerExpanded,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    NavigationBar(
                        containerColor = SurfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEachIndexed { index, item ->
                        val isSelected = currentRoute == item.route || (item.route == "search" && currentRoute?.startsWith("search") == true)
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                selectedTab = index
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                unselectedIconColor = OnSurfaceVariant,
                                unselectedTextColor = OnSurfaceVariant,
                                indicatorColor = Accent.copy(alpha = 0.12f)
                            )
                        )
                    }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onSongTap = { song -> playSongFromNav(playerService, song, coroutineScope, context) },
                    onSearch = {
                        selectedTab = 2
                        navController.navigate("search") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("foryou") {
                ForYouScreen(
                    onSongTap = { song -> playSongFromNav(playerService, song, coroutineScope, context) }
                )
            }
            composable(
                route = "search?query={query}",
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                SearchScreen(
                    initialQuery = query,
                    onSongClick = { song -> playSongFromNav(playerService, song, coroutineScope, context) },
                    onOpenInBrowser = { url ->
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { Log.e(TAG, "open browser failed", e) }
                    }
                )
            }
            composable("search") {
                SearchScreen(
                    initialQuery = "",
                    onSongClick = { song -> playSongFromNav(playerService, song, coroutineScope, context) },
                    onOpenInBrowser = { url ->
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { Log.e(TAG, "open browser failed", e) }
                    }
                )
            }
            composable("library") {
                LibraryScreen(
                    onSongClick = { song -> playSongFromNav(playerService, song, coroutineScope, context) },
                    onOpenInBrowser = { url ->
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { Log.e(TAG, "open browser failed", e) }
                    }
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    onSongTap = { song -> playSongFromNav(playerService, song, coroutineScope, context) }
                )
            }
            composable("playlists") {
                PlaylistsScreen(
                    onSongTap = { song -> playSongFromNav(playerService, song, coroutineScope, context) }
                )
            }
            composable(
                route = "lyrics?title={title}&artist={artist}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("artist") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                val encodedArtist = backStackEntry.arguments?.getString("artist") ?: ""
                val title = try { URLDecoder.decode(encodedTitle, "UTF-8") } catch (_: Exception) { encodedTitle }
                val artist = try { URLDecoder.decode(encodedArtist, "UTF-8") } catch (_: Exception) { encodedArtist }
                LyricsScreen(
                    title = title,
                    artist = artist,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(playerService = playerService, activity = activity)
            }
        }

        }
        // Fullscreen player overlay — outside Scaffold padding, covers bottomBar + MiniPlayer
        val currentSongOverlay by PlayerState.currentSong.collectAsState()
        androidx.compose.animation.AnimatedVisibility(
            visible = isPlayerExpanded && currentSongOverlay != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FullPlayerScreen(
                onDismiss = {
                    Log.d(TAG, "Player collapsed")
                    isPlayerExpanded = false
                },
                onNavigateToLyrics = { encodedTitle, encodedArtist ->
                    isPlayerExpanded = false
                    navController.navigate("lyrics?title=$encodedTitle&artist=$encodedArtist")
                },
                playerService = playerService
            )
        }
    }
}
