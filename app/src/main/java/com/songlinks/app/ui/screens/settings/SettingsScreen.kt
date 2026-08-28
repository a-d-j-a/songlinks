package com.songlinks.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.songlinks.app.ui.theme.Accent
import com.songlinks.app.ui.theme.Card as ThemeCard
import com.songlinks.app.ui.theme.CardBorder
import com.songlinks.app.ui.theme.OnSurfaceVariant
import com.songlinks.app.ui.theme.Surface
import com.songlinks.app.ui.theme.SurfaceVariant
import com.songlinks.app.ui.theme.TextPrimary
import com.songlinks.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    playerService: com.songlinks.app.player.PlayerService? = null,
    activity: com.songlinks.app.MainActivity? = null
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsState()
    val lastBackupDate by viewModel.lastBackupDate.collectAsState()
    val sleepTimerRemaining by com.songlinks.app.data.local.PlayerState.sleepTimerRemaining.collectAsState()
    val normalizationEnabled by viewModel.normalizationEnabled.collectAsState()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val showCodec by viewModel.showCodec.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val hideThumbnail by viewModel.hideThumbnail.collectAsState()
    val cropAlbumArt by viewModel.cropAlbumArt.collectAsState()
    val pureBlack by viewModel.pureBlack.collectAsState()
    val dynamicColors by viewModel.dynamicColors.collectAsState()
    val uiDensity by viewModel.uiDensity.collectAsState()
    val dataSaver by viewModel.dataSaver.collectAsState()
    val bluetoothAutoPlay by viewModel.bluetoothAutoPlay.collectAsState()
    val pauseOnMute by viewModel.pauseOnMute.collectAsState()
    val hideVideoSongs by viewModel.hideVideoSongs.collectAsState()
    val hideYoutubeShorts by viewModel.hideYoutubeShorts.collectAsState()
    val highRefreshRate by viewModel.highRefreshRate.collectAsState()
    val squigglySlider by viewModel.squigglySlider.collectAsState()
    val canvasEnabled by viewModel.canvasEnabled.collectAsState()
    val lyricsKaraoke by viewModel.lyricsKaraoke.collectAsState()
    val translateLyrics by viewModel.translateLyrics.collectAsState()
    val gridLibrary by viewModel.gridLibrary.collectAsState()
    val showStats by viewModel.showStats.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableLongStateOf(0L) }
    var customMinutes by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        }

        item {
            SettingsSection(title = "APPEARANCE") {
                SettingsSwitch(
                    title = "Dark Mode",
                    subtitle = "Currently only dark theme supported",
                    icon = Icons.Filled.DarkMode,
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.toggleTheme() }
                )
            }
        }

        item {
            SettingsSection(title = "AUDIO") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.HighQuality,
                            contentDescription = null,
                            tint = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Audio Quality",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "auto" to "Auto",
                            "low" to "Low",
                            "mid" to "Mid",
                            "high" to "High"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = audioQuality == value,
                                onClick = { viewModel.updateAudioQuality(value) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSwitch(
                    title = "Crossfade",
                    subtitle = "Smooth transition between songs",
                    icon = Icons.Filled.MergeType,
                    checked = crossfadeEnabled,
                    onCheckedChange = { viewModel.toggleCrossfade() }
                )

                AnimatedVisibility(visible = crossfadeEnabled) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Duration: ${crossfadeDuration.toInt()}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Slider(
                            value = crossfadeDuration,
                            onValueChange = { viewModel.updateCrossfadeDuration(it) },
                            valueRange = 1f..12f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = Accent,
                                activeTrackColor = Accent,
                                inactiveTrackColor = CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = "PLAYBACK") {
                SettingsSwitch(title = "Audio Normalization", subtitle = "Loudness normalization (ReplayGain)", icon = Icons.Filled.GraphicEq, checked = normalizationEnabled, onCheckedChange = { viewModel.toggleNormalization() })
                SettingsSwitch(title = "Gapless Playback", subtitle = "No gap between tracks", icon = Icons.Filled.MergeType, checked = gaplessEnabled, onCheckedChange = { viewModel.toggleGapless() })
                SettingsSwitch(title = "Show Codec", subtitle = "Show bitrate/codec on player", icon = Icons.Filled.Info, checked = showCodec, onCheckedChange = { viewModel.toggleShowCodec() })
                SettingsSwitch(title = "Keep Screen On", subtitle = "Prevent screen sleep while playing", icon = Icons.Filled.Visibility, checked = keepScreenOn, onCheckedChange = { viewModel.toggleKeepScreenOn() })
                SettingsSwitch(title = "Bluetooth Auto-play", subtitle = "Resume when BT connects", icon = Icons.Filled.Bluetooth, checked = bluetoothAutoPlay, onCheckedChange = { viewModel.toggleBluetoothAutoPlay() })
                SettingsSwitch(title = "Pause on Mute", subtitle = "Auto-pause when muted", icon = Icons.Filled.VolumeOff, checked = pauseOnMute, onCheckedChange = { viewModel.togglePauseOnMute() })
            }
        }

        item {
            SettingsSection(title = "PLAYER UI") {
                SettingsSwitch(title = "Hide Thumbnail", subtitle = "Minimal player without art", icon = Icons.Filled.VisibilityOff, checked = hideThumbnail, onCheckedChange = { viewModel.toggleHideThumbnail() })
                SettingsSwitch(title = "Crop Album Art", subtitle = "Fill player art crop", icon = Icons.Filled.Crop, checked = cropAlbumArt, onCheckedChange = { viewModel.toggleCropAlbumArt() })
                SettingsSwitch(title = "Squiggly Slider", subtitle = "Wavy progress bar", icon = Icons.Filled.GraphicEq, checked = squigglySlider, onCheckedChange = { viewModel.toggleSquigglySlider() })
                SettingsSwitch(title = "Canvas Animations", subtitle = "Tidal/Spotify canvas", icon = Icons.Filled.GraphicEq, checked = canvasEnabled, onCheckedChange = { viewModel.toggleCanvas() })
            }
        }

        item {
            SettingsSection(title = "DISPLAY") {
                SettingsSwitch(title = "Pure Black (AMOLED)", subtitle = "True black #000000", icon = Icons.Filled.Contrast, checked = pureBlack, onCheckedChange = { viewModel.togglePureBlack() })
                SettingsSwitch(title = "Dynamic Colors", subtitle = "Material You from wallpaper", icon = Icons.Filled.Palette, checked = dynamicColors, onCheckedChange = { viewModel.toggleDynamicColors() })
                SettingsSwitch(title = "High Refresh Rate", subtitle = "120Hz animations", icon = Icons.Filled.Refresh, checked = highRefreshRate, onCheckedChange = { viewModel.toggleHighRefreshRate() })
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("UI Density", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        listOf("compact" to "Compact", "comfortable" to "Comfortable", "spacious" to "Spacious").forEach { (v, l) ->
                            FilterChip(selected = uiDensity == v, onClick = { viewModel.updateUiDensity(v) }, label = { Text(l) }, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "EQUALIZER") {
                Text("5-Band Equalizer", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val bands = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
                    bands.forEach { label ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            // Placeholder slider vertical representation as FilterChip
                            FilterChip(selected = false, onClick = { }, label = { Text("0dB") }, shape = RoundedCornerShape(8.dp))
                        }
                    }
                }
                Text("Presets: Normal, Bass Boost, Treble, Vocal", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        item {
            SettingsSection(title = "CACHE") {
                SettingsInfoRow(icon = Icons.Filled.Info, title = "Cache Limit", value = "500 MB")
                Text("Clear cache to free storage", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Button(onClick = { /* TODO clear cache */ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Clear Cache") }
            }
        }

        item {
            SettingsSection(title = "QUEUE") {
                val queueReorder by viewModel.queueReorder.collectAsState()
                SettingsSwitch(title = "Queue Reorder", subtitle = "Drag to reorder queue", icon = Icons.Filled.MergeType, checked = queueReorder, onCheckedChange = { viewModel.toggleQueueReorder() })
            }
        }

        item {
            SettingsSection(title = "NETWORK") {
                SettingsSwitch(title = "Data Saver", subtitle = "Lower quality on mobile", icon = Icons.Filled.DataSaverOn, checked = dataSaver, onCheckedChange = { viewModel.toggleDataSaver() })
                SettingsSwitch(title = "Hide Video Songs", subtitle = "Filter video content", icon = Icons.Filled.VideoLibrary, checked = hideVideoSongs, onCheckedChange = { viewModel.toggleHideVideoSongs() })
                SettingsSwitch(title = "Hide YouTube Shorts", subtitle = "Filter Shorts", icon = Icons.Filled.Block, checked = hideYoutubeShorts, onCheckedChange = { viewModel.toggleHideShorts() })
            }
        }

        item {
            SettingsSection(title = "LYRICS") {
                SettingsSwitch(title = "Karaoke Mode", subtitle = "Word-by-word highlight", icon = Icons.Filled.MusicNote, checked = lyricsKaraoke, onCheckedChange = { viewModel.toggleLyricsKaraoke() })
                SettingsSwitch(title = "Translate Lyrics", subtitle = "Google Translate", icon = Icons.Filled.Translate, checked = translateLyrics, onCheckedChange = { viewModel.toggleTranslateLyrics() })
            }
        }

        item {
            SettingsSection(title = "LIBRARY") {
                SettingsSwitch(title = "Grid Library", subtitle = "2-column grid for saved", icon = Icons.Filled.GridView, checked = gridLibrary, onCheckedChange = { viewModel.toggleGridLibrary() })
                SettingsSwitch(title = "Show Stats", subtitle = "Top artists & sources", icon = Icons.Filled.BarChart, checked = showStats, onCheckedChange = { viewModel.toggleShowStats() })
            }
        }

        item {
            SettingsSection(title = "LOCAL & PODCAST") {
                val localScan by viewModel.localMediaScan.collectAsState()
                val podcast by viewModel.podcastEnabled.collectAsState()
                val spotify by viewModel.spotifyImport.collectAsState()
                val listen by viewModel.listenTogether.collectAsState()
                SettingsSwitch(title = "Local Media Scan", subtitle = "Play device MP3/FLAC", icon = Icons.Filled.MusicNote, checked = localScan, onCheckedChange = { viewModel.toggleLocalMediaScan() })
                SettingsSwitch(title = "Podcast Support", subtitle = "Podcasts alongside music", icon = Icons.Filled.MusicNote, checked = podcast, onCheckedChange = { viewModel.togglePodcast() })
                SettingsSwitch(title = "Import from Spotify", subtitle = "Bring playlists", icon = Icons.Filled.MusicNote, checked = spotify, onCheckedChange = { viewModel.toggleSpotifyImport() })
                SettingsSwitch(title = "Listen Together", subtitle = "Sync like Spotify Jam", icon = Icons.Filled.MusicNote, checked = listen, onCheckedChange = { viewModel.toggleListenTogether() })
            }
        }

        item {
            SettingsSection(title = "PLAYER EXTRAS") {
                val blur by viewModel.blurStrength.collectAsState()
                val gradient by viewModel.gradientOverlay.collectAsState()
                val lyricsOnPlayer by viewModel.showLyricsOnPlayer.collectAsState()
                val tempo by viewModel.tempoPitch.collectAsState()
                SettingsSwitch(title = "Gradient Overlay", subtitle = "Dark gradient on player", icon = Icons.Filled.Palette, checked = gradient, onCheckedChange = { viewModel.toggleGradientOverlay() })
                SettingsSwitch(title = "Show Lyrics on Player", subtitle = "Inline lyrics", icon = Icons.Filled.MusicNote, checked = lyricsOnPlayer, onCheckedChange = { viewModel.toggleShowLyricsOnPlayer() })
                SettingsSwitch(title = "Tempo/Pitch", subtitle = "Speed & pitch control", icon = Icons.Filled.GraphicEq, checked = tempo, onCheckedChange = { viewModel.toggleTempoPitch() })
                Text("Blur Strength: ${blur.toInt()}dp", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Slider(value = blur, onValueChange = { viewModel.updateBlurStrength(it) }, valueRange = 0f..60f, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
            }
        }

        item {
            SettingsSection(title = "STORAGE") {
                val autoDl by viewModel.autoDownloadWifi.collectAsState()
                val del30 by viewModel.deleteAfter30Days.collectAsState()
                val showYear by viewModel.showYear.collectAsState()
                val showSize by viewModel.showFileSize.collectAsState()
                val androidAuto by viewModel.androidAuto.collectAsState()
                SettingsSwitch(title = "Auto Download on WiFi", subtitle = "Cache favorites", icon = Icons.Filled.Download, checked = autoDl, onCheckedChange = { viewModel.toggleAutoDownloadWifi() })
                SettingsSwitch(title = "Delete after 30 days", subtitle = "Auto clean old", icon = Icons.Filled.DeleteSweep, checked = del30, onCheckedChange = { viewModel.toggleDeleteAfter30Days() })
                SettingsSwitch(title = "Show Year", subtitle = "Release year on cards", icon = Icons.Filled.Info, checked = showYear, onCheckedChange = { viewModel.toggleShowYear() })
                SettingsSwitch(title = "Show File Size", subtitle = "Size on downloads", icon = Icons.Filled.Info, checked = showSize, onCheckedChange = { viewModel.toggleShowFileSize() })
                SettingsSwitch(title = "Android Auto", subtitle = "Car support", icon = Icons.Filled.Info, checked = androidAuto, onCheckedChange = { viewModel.toggleAndroidAuto() })
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val sortBy by viewModel.sortBy.collectAsState()
                    Text("Sort By", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        listOf("recent" to "Recent", "title" to "Title", "artist" to "Artist").forEach { (v,l) -> FilterChip(selected = sortBy==v, onClick = { viewModel.updateSortBy(v) }, label = { Text(l) }, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "DOWNLOADS") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Download Quality",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "auto" to "Auto",
                            "low" to "Low",
                            "mid" to "Mid",
                            "high" to "High"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = downloadQuality == value,
                                onClick = { viewModel.updateDownloadQuality(value) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSwitch(
                    title = "Download over WiFi only",
                    subtitle = "Save mobile data",
                    icon = Icons.Filled.Wifi,
                    checked = downloadWifiOnly,
                    onCheckedChange = { viewModel.toggleDownloadWifiOnly() }
                )

                SettingsInfoRow(
                    icon = Icons.Filled.Download,
                    title = "Storage",
                    value = "Internal storage"
                )
            }
        }

        item {
            SettingsSection(title = "BACKUP") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CloudUpload,
                        contentDescription = null,
                        tint = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export Backup",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Save your data to a file",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val json = viewModel.getExportJson()
                            if (activity != null) {
                                activity.launchExport { uri ->
                                    try {
                                        val ctx = activity.applicationContext
                                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                                            out.write(json.toByteArray())
                                        }
                                        viewModel.onExportComplete()
                                    } catch (e: Exception) { android.util.Log.e("SettingsScreen", "export failed", e) }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export to File")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Backup",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Restore data from a backup file",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        if (activity != null) {
                            activity.launchImport { uri ->
                                try {
                                    val ctx = activity.applicationContext
                                    val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                    if (json != null) {
                                        viewModel.importFromJson(json)
                                    }
                                } catch (e: Exception) { android.util.Log.e("SettingsScreen", "import failed", e) }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import from File")
                }

                if (lastBackupDate > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val dateStr = remember(lastBackupDate) { dateFormat.format(Date(lastBackupDate)) }
                    SettingsInfoRow(
                        icon = Icons.Filled.Info,
                        title = "Last Backup",
                        value = dateStr
                    )
                }
            }
        }

        item {
            SettingsSection(title = "SLEEP TIMER") {
                val isActive = sleepTimerRemaining > 0L
                val remainingMs = sleepTimerRemaining
                val remainingMin = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
                val remainingSec = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60

                if (isActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = Accent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Active: ${remainingMin}:${String.format("%02d", remainingSec)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Accent
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            playerService?.cancelSleepTimer()
                        }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Text(
                    text = "Quick Timer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        FilterChip(
                            selected = sleepTimerMinutes == minutes.toLong() && isActive,
                            onClick = {
                                sleepTimerMinutes = minutes.toLong()
                                playerService?.startSleepTimer(minutes)
                            },
                            label = { Text("${minutes}m") },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Custom (minutes)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Minutes") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = SurfaceVariant,
                            unfocusedContainerColor = SurfaceVariant,
                            cursorColor = Accent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = Accent,
                            unfocusedLabelColor = OnSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val mins = customMinutes.toLongOrNull() ?: 0L
                            if (mins > 0) {
                                sleepTimerMinutes = mins
                                playerService?.startSleepTimer(mins.toInt())
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("Set")
                    }
                }
            }
        }

        item {
            SettingsSection(title = "ABOUT") {
                SettingsInfoRow(
                    icon = Icons.Filled.Info,
                    title = "Version",
                    value = "1.0.0"
                )
                SettingsInfoRow(
                    icon = Icons.Filled.Code,
                    title = "GitHub",
                    value = "songlinks"
                )
            }
        }

        item {
            SettingsSection(title = "DANGER ZONE") {
                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear History")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data")
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear your play history?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("This will reset all settings and data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearAllDialog = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Accent,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCard),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.3f),
                uncheckedThumbColor = OnSurfaceVariant,
                uncheckedTrackColor = CardBorder
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
