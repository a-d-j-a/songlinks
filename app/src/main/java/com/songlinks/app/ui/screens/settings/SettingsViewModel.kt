package com.songlinks.app.ui.screens.settings

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.songlinks.app.SongLinksApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SettingsViewModel"

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _audioQuality = MutableStateFlow(
        prefs.getString("audio_quality", "auto") ?: "auto"
    )
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(prefs.getFloat("crossfade_duration", 3f))
    val crossfadeDuration: StateFlow<Float> = _crossfadeDuration.asStateFlow()

    private val _downloadQuality = MutableStateFlow(
        prefs.getString("download_quality", "auto") ?: "auto"
    )
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _downloadWifiOnly = MutableStateFlow(prefs.getBoolean("download_wifi_only", true))
    val downloadWifiOnly: StateFlow<Boolean> = _downloadWifiOnly.asStateFlow()

    private val _lastBackupDate = MutableStateFlow(
        prefs.getLong("last_backup_date", 0L)
    )
    val lastBackupDate: StateFlow<Long> = _lastBackupDate.asStateFlow()

    // --- 30+ Echo/OuterTune features ---
    private val _normalizationEnabled = MutableStateFlow(prefs.getBoolean("normalization_enabled", false))
    val normalizationEnabled: StateFlow<Boolean> = _normalizationEnabled.asStateFlow()
    private val _gaplessEnabled = MutableStateFlow(prefs.getBoolean("gapless_enabled", true))
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()
    private val _showCodec = MutableStateFlow(prefs.getBoolean("show_codec", false))
    val showCodec: StateFlow<Boolean> = _showCodec.asStateFlow()
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", false))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()
    private val _hideThumbnail = MutableStateFlow(prefs.getBoolean("hide_thumbnail", false))
    val hideThumbnail: StateFlow<Boolean> = _hideThumbnail.asStateFlow()
    private val _cropAlbumArt = MutableStateFlow(prefs.getBoolean("crop_album_art", false))
    val cropAlbumArt: StateFlow<Boolean> = _cropAlbumArt.asStateFlow()
    private val _pureBlack = MutableStateFlow(prefs.getBoolean("pure_black", true))
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()
    private val _dynamicColors = MutableStateFlow(prefs.getBoolean("dynamic_colors", false))
    val dynamicColors: StateFlow<Boolean> = _dynamicColors.asStateFlow()
    private val _uiDensity = MutableStateFlow(prefs.getString("ui_density", "comfortable") ?: "comfortable")
    val uiDensity: StateFlow<String> = _uiDensity.asStateFlow()
    private val _dataSaver = MutableStateFlow(prefs.getBoolean("data_saver", false))
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()
    private val _bluetoothAutoPlay = MutableStateFlow(prefs.getBoolean("bluetooth_autoplay", true))
    val bluetoothAutoPlay: StateFlow<Boolean> = _bluetoothAutoPlay.asStateFlow()
    private val _pauseOnMute = MutableStateFlow(prefs.getBoolean("pause_on_mute", true))
    val pauseOnMute: StateFlow<Boolean> = _pauseOnMute.asStateFlow()
    private val _hideVideoSongs = MutableStateFlow(prefs.getBoolean("hide_video_songs", false))
    val hideVideoSongs: StateFlow<Boolean> = _hideVideoSongs.asStateFlow()
    private val _hideYoutubeShorts = MutableStateFlow(prefs.getBoolean("hide_shorts", false))
    val hideYoutubeShorts: StateFlow<Boolean> = _hideYoutubeShorts.asStateFlow()
    private val _highRefreshRate = MutableStateFlow(prefs.getBoolean("high_refresh_rate", true))
    val highRefreshRate: StateFlow<Boolean> = _highRefreshRate.asStateFlow()
    private val _squigglySlider = MutableStateFlow(prefs.getBoolean("squiggly_slider", false))
    val squigglySlider: StateFlow<Boolean> = _squigglySlider.asStateFlow()
    private val _canvasEnabled = MutableStateFlow(prefs.getBoolean("canvas_enabled", true))
    val canvasEnabled: StateFlow<Boolean> = _canvasEnabled.asStateFlow()
    private val _lyricsKaraoke = MutableStateFlow(prefs.getBoolean("lyrics_karaoke", false))
    val lyricsKaraoke: StateFlow<Boolean> = _lyricsKaraoke.asStateFlow()
    private val _translateLyrics = MutableStateFlow(prefs.getBoolean("translate_lyrics", false))
    val translateLyrics: StateFlow<Boolean> = _translateLyrics.asStateFlow()
    private val _queueReorder = MutableStateFlow(prefs.getBoolean("queue_reorder", true))
    val queueReorder: StateFlow<Boolean> = _queueReorder.asStateFlow()
    private val _gridLibrary = MutableStateFlow(prefs.getBoolean("grid_library", true))
    val gridLibrary: StateFlow<Boolean> = _gridLibrary.asStateFlow()
    private val _showStats = MutableStateFlow(prefs.getBoolean("show_stats", true))
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()
    private val _cacheLimit = MutableStateFlow(prefs.getInt("cache_limit_mb", 500))
    val cacheLimit: StateFlow<Int> = _cacheLimit.asStateFlow()
    private val _recentLimit = MutableStateFlow(prefs.getInt("recent_limit", 10))
    val recentLimit: StateFlow<Int> = _recentLimit.asStateFlow()
    private val _localMediaScan = MutableStateFlow(prefs.getBoolean("local_media_scan", false))
    val localMediaScan: StateFlow<Boolean> = _localMediaScan.asStateFlow()
    private val _podcastEnabled = MutableStateFlow(prefs.getBoolean("podcast_enabled", false))
    val podcastEnabled: StateFlow<Boolean> = _podcastEnabled.asStateFlow()
    private val _spotifyImport = MutableStateFlow(prefs.getBoolean("spotify_import", false))
    val spotifyImport: StateFlow<Boolean> = _spotifyImport.asStateFlow()
    private val _listenTogether = MutableStateFlow(prefs.getBoolean("listen_together", false))
    val listenTogether: StateFlow<Boolean> = _listenTogether.asStateFlow()
    private val _blurStrength = MutableStateFlow(prefs.getFloat("blur_strength", 36f))
    val blurStrength: StateFlow<Float> = _blurStrength.asStateFlow()
    private val _gradientOverlay = MutableStateFlow(prefs.getBoolean("gradient_overlay", true))
    val gradientOverlay: StateFlow<Boolean> = _gradientOverlay.asStateFlow()
    private val _showLyricsOnPlayer = MutableStateFlow(prefs.getBoolean("show_lyrics_player", false))
    val showLyricsOnPlayer: StateFlow<Boolean> = _showLyricsOnPlayer.asStateFlow()
    private val _autoDownloadWifi = MutableStateFlow(prefs.getBoolean("auto_download_wifi", false))
    val autoDownloadWifi: StateFlow<Boolean> = _autoDownloadWifi.asStateFlow()
    private val _deleteAfter30Days = MutableStateFlow(prefs.getBoolean("delete_after_30d", false))
    val deleteAfter30Days: StateFlow<Boolean> = _deleteAfter30Days.asStateFlow()
    private val _sortBy = MutableStateFlow(prefs.getString("sort_by", "recent") ?: "recent")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()
    private val _groupBy = MutableStateFlow(prefs.getString("group_by", "none") ?: "none")
    val groupBy: StateFlow<String> = _groupBy.asStateFlow()
    private val _showYear = MutableStateFlow(prefs.getBoolean("show_year", true))
    val showYear: StateFlow<Boolean> = _showYear.asStateFlow()
    private val _showFileSize = MutableStateFlow(prefs.getBoolean("show_file_size", false))
    val showFileSize: StateFlow<Boolean> = _showFileSize.asStateFlow()
    private val _tempoPitch = MutableStateFlow(prefs.getBoolean("tempo_pitch", false))
    val tempoPitch: StateFlow<Boolean> = _tempoPitch.asStateFlow()
    private val _androidAuto = MutableStateFlow(prefs.getBoolean("android_auto", true))
    val androidAuto: StateFlow<Boolean> = _androidAuto.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        Log.d(TAG, "toggleTheme: ${_isDarkTheme.value}")
        prefs.edit().putBoolean("dark_theme", _isDarkTheme.value).apply()
    }

    fun updateAudioQuality(quality: String) {
        _audioQuality.value = quality
        prefs.edit().putString("audio_quality", quality).apply()
    }

    fun toggleCrossfade() {
        _crossfadeEnabled.value = !_crossfadeEnabled.value
        prefs.edit().putBoolean("crossfade_enabled", _crossfadeEnabled.value).apply()
    }

    fun updateCrossfadeDuration(duration: Float) {
        _crossfadeDuration.value = duration
        prefs.edit().putFloat("crossfade_duration", duration).apply()
    }

    fun updateDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        prefs.edit().putString("download_quality", quality).apply()
    }

    fun toggleDownloadWifiOnly() {
        _downloadWifiOnly.value = !_downloadWifiOnly.value
        prefs.edit().putBoolean("download_wifi_only", _downloadWifiOnly.value).apply()
    }

    // 30+ feature toggles
    fun toggleNormalization() { _normalizationEnabled.value = !_normalizationEnabled.value; prefs.edit().putBoolean("normalization_enabled", _normalizationEnabled.value).apply() }
    fun toggleGapless() { _gaplessEnabled.value = !_gaplessEnabled.value; prefs.edit().putBoolean("gapless_enabled", _gaplessEnabled.value).apply() }
    fun toggleShowCodec() { _showCodec.value = !_showCodec.value; prefs.edit().putBoolean("show_codec", _showCodec.value).apply() }
    fun toggleKeepScreenOn() { _keepScreenOn.value = !_keepScreenOn.value; prefs.edit().putBoolean("keep_screen_on", _keepScreenOn.value).apply() }
    fun toggleHideThumbnail() { _hideThumbnail.value = !_hideThumbnail.value; prefs.edit().putBoolean("hide_thumbnail", _hideThumbnail.value).apply() }
    fun toggleCropAlbumArt() { _cropAlbumArt.value = !_cropAlbumArt.value; prefs.edit().putBoolean("crop_album_art", _cropAlbumArt.value).apply() }
    fun togglePureBlack() { _pureBlack.value = !_pureBlack.value; prefs.edit().putBoolean("pure_black", _pureBlack.value).apply() }
    fun toggleDynamicColors() { _dynamicColors.value = !_dynamicColors.value; prefs.edit().putBoolean("dynamic_colors", _dynamicColors.value).apply() }
    fun updateUiDensity(v: String) { _uiDensity.value = v; prefs.edit().putString("ui_density", v).apply() }
    fun toggleDataSaver() { _dataSaver.value = !_dataSaver.value; prefs.edit().putBoolean("data_saver", _dataSaver.value).apply() }
    fun toggleBluetoothAutoPlay() { _bluetoothAutoPlay.value = !_bluetoothAutoPlay.value; prefs.edit().putBoolean("bluetooth_autoplay", _bluetoothAutoPlay.value).apply() }
    fun togglePauseOnMute() { _pauseOnMute.value = !_pauseOnMute.value; prefs.edit().putBoolean("pause_on_mute", _pauseOnMute.value).apply() }
    fun toggleHideVideoSongs() { _hideVideoSongs.value = !_hideVideoSongs.value; prefs.edit().putBoolean("hide_video_songs", _hideVideoSongs.value).apply() }
    fun toggleHideShorts() { _hideYoutubeShorts.value = !_hideYoutubeShorts.value; prefs.edit().putBoolean("hide_shorts", _hideYoutubeShorts.value).apply() }
    fun toggleHighRefreshRate() { _highRefreshRate.value = !_highRefreshRate.value; prefs.edit().putBoolean("high_refresh_rate", _highRefreshRate.value).apply() }
    fun toggleSquigglySlider() { _squigglySlider.value = !_squigglySlider.value; prefs.edit().putBoolean("squiggly_slider", _squigglySlider.value).apply() }
    fun toggleCanvas() { _canvasEnabled.value = !_canvasEnabled.value; prefs.edit().putBoolean("canvas_enabled", _canvasEnabled.value).apply() }
    fun toggleLyricsKaraoke() { _lyricsKaraoke.value = !_lyricsKaraoke.value; prefs.edit().putBoolean("lyrics_karaoke", _lyricsKaraoke.value).apply() }
    fun toggleTranslateLyrics() { _translateLyrics.value = !_translateLyrics.value; prefs.edit().putBoolean("translate_lyrics", _translateLyrics.value).apply() }
    fun toggleQueueReorder() { _queueReorder.value = !_queueReorder.value; prefs.edit().putBoolean("queue_reorder", _queueReorder.value).apply() }
    fun toggleGridLibrary() { _gridLibrary.value = !_gridLibrary.value; prefs.edit().putBoolean("grid_library", _gridLibrary.value).apply() }
    fun toggleShowStats() { _showStats.value = !_showStats.value; prefs.edit().putBoolean("show_stats", _showStats.value).apply() }
    fun updateCacheLimit(v: Int) { _cacheLimit.value = v; prefs.edit().putInt("cache_limit_mb", v).apply() }
    fun updateRecentLimit(v: Int) { _recentLimit.value = v; prefs.edit().putInt("recent_limit", v).apply() }
    fun toggleLocalMediaScan() { _localMediaScan.value = !_localMediaScan.value; prefs.edit().putBoolean("local_media_scan", _localMediaScan.value).apply() }
    fun togglePodcast() { _podcastEnabled.value = !_podcastEnabled.value; prefs.edit().putBoolean("podcast_enabled", _podcastEnabled.value).apply() }
    fun toggleSpotifyImport() { _spotifyImport.value = !_spotifyImport.value; prefs.edit().putBoolean("spotify_import", _spotifyImport.value).apply() }
    fun toggleListenTogether() { _listenTogether.value = !_listenTogether.value; prefs.edit().putBoolean("listen_together", _listenTogether.value).apply() }
    fun updateBlurStrength(v: Float) { _blurStrength.value = v; prefs.edit().putFloat("blur_strength", v).apply() }
    fun toggleGradientOverlay() { _gradientOverlay.value = !_gradientOverlay.value; prefs.edit().putBoolean("gradient_overlay", _gradientOverlay.value).apply() }
    fun toggleShowLyricsOnPlayer() { _showLyricsOnPlayer.value = !_showLyricsOnPlayer.value; prefs.edit().putBoolean("show_lyrics_player", _showLyricsOnPlayer.value).apply() }
    fun toggleAutoDownloadWifi() { _autoDownloadWifi.value = !_autoDownloadWifi.value; prefs.edit().putBoolean("auto_download_wifi", _autoDownloadWifi.value).apply() }
    fun toggleDeleteAfter30Days() { _deleteAfter30Days.value = !_deleteAfter30Days.value; prefs.edit().putBoolean("delete_after_30d", _deleteAfter30Days.value).apply() }
    fun updateSortBy(v: String) { _sortBy.value = v; prefs.edit().putString("sort_by", v).apply() }
    fun updateGroupBy(v: String) { _groupBy.value = v; prefs.edit().putString("group_by", v).apply() }
    fun toggleShowYear() { _showYear.value = !_showYear.value; prefs.edit().putBoolean("show_year", _showYear.value).apply() }
    fun toggleShowFileSize() { _showFileSize.value = !_showFileSize.value; prefs.edit().putBoolean("show_file_size", _showFileSize.value).apply() }
    fun toggleTempoPitch() { _tempoPitch.value = !_tempoPitch.value; prefs.edit().putBoolean("tempo_pitch", _tempoPitch.value).apply() }
    fun toggleAndroidAuto() { _androidAuto.value = !_androidAuto.value; prefs.edit().putBoolean("android_auto", _androidAuto.value).apply() }

    private fun buildBackupMap(playlistsJson: String = "[]"): Map<String, String> {
        val recentJson = prefs.getString("recent_searches_json", null)
            ?: prefs.getStringSet("recent_searches", null)?.let { Gson().toJson(it.toList()) } ?: "[]"
        return mapOf(
            "play_history" to (prefs.getString("play_history", "") ?: ""),
            "recent_searches_json" to recentJson,
            "audio_quality" to _audioQuality.value,
            "crossfade_enabled" to _crossfadeEnabled.value.toString(),
            "crossfade_duration" to _crossfadeDuration.value.toString(),
            "download_quality" to _downloadQuality.value,
            "download_wifi_only" to _downloadWifiOnly.value.toString(),
            "dark_theme" to _isDarkTheme.value.toString(),
            "normalization_enabled" to _normalizationEnabled.value.toString(),
            "gapless_enabled" to _gaplessEnabled.value.toString(),
            "show_codec" to _showCodec.value.toString(),
            "keep_screen_on" to _keepScreenOn.value.toString(),
            "hide_thumbnail" to _hideThumbnail.value.toString(),
            "crop_album_art" to _cropAlbumArt.value.toString(),
            "pure_black" to _pureBlack.value.toString(),
            "dynamic_colors" to _dynamicColors.value.toString(),
            "ui_density" to _uiDensity.value,
            "data_saver" to _dataSaver.value.toString(),
            "bluetooth_autoplay" to _bluetoothAutoPlay.value.toString(),
            "pause_on_mute" to _pauseOnMute.value.toString(),
            "hide_video_songs" to _hideVideoSongs.value.toString(),
            "hide_shorts" to _hideYoutubeShorts.value.toString(),
            "high_refresh_rate" to _highRefreshRate.value.toString(),
            "squiggly_slider" to _squigglySlider.value.toString(),
            "canvas_enabled" to _canvasEnabled.value.toString(),
            "lyrics_karaoke" to _lyricsKaraoke.value.toString(),
            "translate_lyrics" to _translateLyrics.value.toString(),
            "queue_reorder" to _queueReorder.value.toString(),
            "grid_library" to _gridLibrary.value.toString(),
            "show_stats" to _showStats.value.toString(),
            "cache_limit_mb" to _cacheLimit.value.toString(),
            "recent_limit" to _recentLimit.value.toString(),
            "local_media_scan" to _localMediaScan.value.toString(),
            "podcast_enabled" to _podcastEnabled.value.toString(),
            "spotify_import" to _spotifyImport.value.toString(),
            "listen_together" to _listenTogether.value.toString(),
            "blur_strength" to _blurStrength.value.toString(),
            "gradient_overlay" to _gradientOverlay.value.toString(),
            "show_lyrics_player" to _showLyricsOnPlayer.value.toString(),
            "auto_download_wifi" to _autoDownloadWifi.value.toString(),
            "delete_after_30d" to _deleteAfter30Days.value.toString(),
            "sort_by" to _sortBy.value,
            "group_by" to _groupBy.value,
            "show_year" to _showYear.value.toString(),
            "show_file_size" to _showFileSize.value.toString(),
            "tempo_pitch" to _tempoPitch.value.toString(),
            "android_auto" to _androidAuto.value.toString(),
            "playlists" to playlistsJson,
            "version" to "2.0"
        )
    }

    private suspend fun getPlaylistsJson(): String = withContext(Dispatchers.IO) {
        try {
            val app = getApplication<Application>() as SongLinksApp
            val playlists = app.database.playlistDao().getAllPlaylists().first()
            val result = playlists.map { pl ->
                val songs = try { app.database.playlistDao().getSongsInPlaylist(pl.id).first() } catch (_: Exception) { emptyList() }
                mapOf("name" to pl.name, "songs" to songs.map { s -> mapOf("id" to s.songId, "title" to s.title, "artist" to s.artist) })
            }
            Gson().toJson(result)
        } catch (e: Exception) { Log.e(TAG, "getPlaylistsJson failed", e); "[]" }
    }

    fun exportBackup() {
        Log.d(TAG, "exportBackup: saving local data to SharedPreferences backup")
        viewModelScope.launch {
            try {
                val playlistsJson = getPlaylistsJson()
                val backupMap = buildBackupMap(playlistsJson)
                val json = Gson().toJson(backupMap)
                prefs.edit().putString("local_backup", json).apply()
                _lastBackupDate.value = System.currentTimeMillis()
                prefs.edit().putLong("last_backup_date", _lastBackupDate.value).apply()
                Log.d(TAG, "exportBackup: success with playlists, timestamp=${_lastBackupDate.value}")
            } catch (e: Exception) {
                Log.e(TAG, "exportBackup failed", e)
            }
        }
    }

    suspend fun getExportJson(): String {
        Log.d(TAG, "getExportJson")
        val playlistsJson = getPlaylistsJson()
        return Gson().toJson(buildBackupMap(playlistsJson))
    }

    fun onExportComplete() {
        Log.d(TAG, "onExportComplete")
        _lastBackupDate.value = System.currentTimeMillis()
        prefs.edit().putLong("last_backup_date", _lastBackupDate.value).apply()
    }

    fun importFromJson(json: String) {
        Log.d(TAG, "importFromJson: parsing JSON")
        viewModelScope.launch {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val backupMap: Map<String, String> = Gson().fromJson(json, type) ?: emptyMap()
                val editor = prefs.edit()
                backupMap["play_history"]?.let { editor.putString("play_history", it) }
                backupMap["audio_quality"]?.let { editor.putString("audio_quality", it); _audioQuality.value = it }
                backupMap["crossfade_enabled"]?.let { runCatching { it.toBooleanStrict() }.getOrDefault(it.toBoolean()); editor.putBoolean("crossfade_enabled", it.toBoolean()); _crossfadeEnabled.value = it.toBoolean() }
                backupMap["crossfade_duration"]?.let { runCatching { it.toFloat() }.getOrNull()?.let { f -> editor.putFloat("crossfade_duration", f); _crossfadeDuration.value = f } }
                backupMap["download_quality"]?.let { editor.putString("download_quality", it); _downloadQuality.value = it }
                backupMap["download_wifi_only"]?.let { editor.putBoolean("download_wifi_only", it.toBoolean()); _downloadWifiOnly.value = it.toBoolean() }
                backupMap["dark_theme"]?.let { editor.putBoolean("dark_theme", it.toBoolean()); _isDarkTheme.value = it.toBoolean() }
                backupMap["normalization_enabled"]?.let { editor.putBoolean("normalization_enabled", it.toBoolean()); _normalizationEnabled.value = it.toBoolean() }
                backupMap["gapless_enabled"]?.let { editor.putBoolean("gapless_enabled", it.toBoolean()); _gaplessEnabled.value = it.toBoolean() }
                backupMap["show_codec"]?.let { editor.putBoolean("show_codec", it.toBoolean()); _showCodec.value = it.toBoolean() }
                backupMap["keep_screen_on"]?.let { editor.putBoolean("keep_screen_on", it.toBoolean()); _keepScreenOn.value = it.toBoolean() }
                backupMap["hide_thumbnail"]?.let { editor.putBoolean("hide_thumbnail", it.toBoolean()); _hideThumbnail.value = it.toBoolean() }
                backupMap["crop_album_art"]?.let { editor.putBoolean("crop_album_art", it.toBoolean()); _cropAlbumArt.value = it.toBoolean() }
                backupMap["pure_black"]?.let { editor.putBoolean("pure_black", it.toBoolean()); _pureBlack.value = it.toBoolean() }
                backupMap["dynamic_colors"]?.let { editor.putBoolean("dynamic_colors", it.toBoolean()); _dynamicColors.value = it.toBoolean() }
                backupMap["ui_density"]?.let { editor.putString("ui_density", it); _uiDensity.value = it }
                backupMap["data_saver"]?.let { editor.putBoolean("data_saver", it.toBoolean()); _dataSaver.value = it.toBoolean() }
                backupMap["bluetooth_autoplay"]?.let { editor.putBoolean("bluetooth_autoplay", it.toBoolean()); _bluetoothAutoPlay.value = it.toBoolean() }
                backupMap["pause_on_mute"]?.let { editor.putBoolean("pause_on_mute", it.toBoolean()); _pauseOnMute.value = it.toBoolean() }
                backupMap["hide_video_songs"]?.let { editor.putBoolean("hide_video_songs", it.toBoolean()); _hideVideoSongs.value = it.toBoolean() }
                backupMap["hide_shorts"]?.let { editor.putBoolean("hide_shorts", it.toBoolean()); _hideYoutubeShorts.value = it.toBoolean() }
                backupMap["high_refresh_rate"]?.let { editor.putBoolean("high_refresh_rate", it.toBoolean()); _highRefreshRate.value = it.toBoolean() }
                backupMap["squiggly_slider"]?.let { editor.putBoolean("squiggly_slider", it.toBoolean()); _squigglySlider.value = it.toBoolean() }
                backupMap["canvas_enabled"]?.let { editor.putBoolean("canvas_enabled", it.toBoolean()); _canvasEnabled.value = it.toBoolean() }
                backupMap["lyrics_karaoke"]?.let { editor.putBoolean("lyrics_karaoke", it.toBoolean()); _lyricsKaraoke.value = it.toBoolean() }
                backupMap["translate_lyrics"]?.let { editor.putBoolean("translate_lyrics", it.toBoolean()); _translateLyrics.value = it.toBoolean() }
                backupMap["queue_reorder"]?.let { editor.putBoolean("queue_reorder", it.toBoolean()); _queueReorder.value = it.toBoolean() }
                backupMap["grid_library"]?.let { editor.putBoolean("grid_library", it.toBoolean()); _gridLibrary.value = it.toBoolean() }
                backupMap["show_stats"]?.let { editor.putBoolean("show_stats", it.toBoolean()); _showStats.value = it.toBoolean() }
                backupMap["cache_limit_mb"]?.let { it.toIntOrNull()?.let { v -> editor.putInt("cache_limit_mb", v); _cacheLimit.value = v } }
                backupMap["recent_limit"]?.let { it.toIntOrNull()?.let { v -> editor.putInt("recent_limit", v); _recentLimit.value = v } }
                backupMap["local_media_scan"]?.let { editor.putBoolean("local_media_scan", it.toBoolean()); _localMediaScan.value = it.toBoolean() }
                backupMap["podcast_enabled"]?.let { editor.putBoolean("podcast_enabled", it.toBoolean()); _podcastEnabled.value = it.toBoolean() }
                backupMap["spotify_import"]?.let { editor.putBoolean("spotify_import", it.toBoolean()); _spotifyImport.value = it.toBoolean() }
                backupMap["listen_together"]?.let { editor.putBoolean("listen_together", it.toBoolean()); _listenTogether.value = it.toBoolean() }
                backupMap["blur_strength"]?.let { it.toFloatOrNull()?.let { v -> editor.putFloat("blur_strength", v); _blurStrength.value = v } }
                backupMap["gradient_overlay"]?.let { editor.putBoolean("gradient_overlay", it.toBoolean()); _gradientOverlay.value = it.toBoolean() }
                backupMap["show_lyrics_player"]?.let { editor.putBoolean("show_lyrics_player", it.toBoolean()); _showLyricsOnPlayer.value = it.toBoolean() }
                backupMap["auto_download_wifi"]?.let { editor.putBoolean("auto_download_wifi", it.toBoolean()); _autoDownloadWifi.value = it.toBoolean() }
                backupMap["delete_after_30d"]?.let { editor.putBoolean("delete_after_30d", it.toBoolean()); _deleteAfter30Days.value = it.toBoolean() }
                backupMap["sort_by"]?.let { editor.putString("sort_by", it); _sortBy.value = it }
                backupMap["group_by"]?.let { editor.putString("group_by", it); _groupBy.value = it }
                backupMap["show_year"]?.let { editor.putBoolean("show_year", it.toBoolean()); _showYear.value = it.toBoolean() }
                backupMap["show_file_size"]?.let { editor.putBoolean("show_file_size", it.toBoolean()); _showFileSize.value = it.toBoolean() }
                backupMap["tempo_pitch"]?.let { editor.putBoolean("tempo_pitch", it.toBoolean()); _tempoPitch.value = it.toBoolean() }
                backupMap["android_auto"]?.let { editor.putBoolean("android_auto", it.toBoolean()); _androidAuto.value = it.toBoolean() }
                // Recent searches: prefer json, fallback pipe
                val recentJson = backupMap["recent_searches_json"] ?: backupMap["recent_searches"]
                recentJson?.let { raw ->
                    try {
                        if (raw.trim().startsWith("[")) {
                            editor.putString("recent_searches_json", raw)
                            editor.remove("recent_searches")
                        } else {
                            val set = raw.split("|").filter { it.isNotBlank() }
                            editor.putString("recent_searches_json", Gson().toJson(set))
                            editor.remove("recent_searches")
                        }
                    } catch (_: Exception) {}
                }
                // Playlists: restore to DB (adjust For You accordingly)
                backupMap["playlists"]?.let { playlistsStr ->
                    if (playlistsStr.isNotBlank() && playlistsStr != "[]") {
                        try {
                            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                            val list: List<Map<String, Any>> = Gson().fromJson(playlistsStr, type) ?: emptyList()
                            // Store count for For You to reflect
                            editor.putInt("backup_playlists_count", list.size)
                            // Actual DB restore handled async below
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val app = getApplication<Application>() as SongLinksApp
                                    for (pl in list) {
                                        val name = pl["name"] as? String ?: continue
                                        // Insert playlist if not exists
                                        try { app.database.playlistDao().createPlaylist(com.songlinks.app.data.local.PlaylistEntity(name = name)) } catch (_: Exception) {}
                                    }
                                } catch (e: Exception) { Log.e(TAG, "playlist restore failed", e) }
                            }
                        } catch (_: Exception) {}
                    }
                }
                editor.apply()
                _lastBackupDate.value = System.currentTimeMillis()
                prefs.edit().putLong("last_backup_date", _lastBackupDate.value).apply()
                Log.d(TAG, "importFromJson: success, restored ${backupMap.size} keys")
            } catch (e: Exception) {
                Log.e(TAG, "importFromJson failed", e)
            }
        }
    }

    fun importBackup() {
        Log.d(TAG, "importBackup: restoring from local backup")
        viewModelScope.launch {
            try {
                val json = prefs.getString("local_backup", null)
                if (json != null) {
                    importFromJson(json)
                } else {
                    Log.w(TAG, "importBackup: no local backup found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "importBackup failed", e)
            }
        }
    }

    fun clearHistory() {
        prefs.edit().remove("play_history").apply()
    }

    fun clearAllData() {
        Log.d(TAG, "clearAllData")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>() as SongLinksApp
                withContext(Dispatchers.IO) {
                    app.database.songDao().deleteAll()
                    app.database.downloadDao().deleteAll()
                    // delete playlists via dao
                    val playlists = app.database.playlistDao().getAllPlaylists()
                    // Use direct queries for bulk delete
                    app.database.clearAllTables()
                }
                // Clear files
                val dlDir = java.io.File(getApplication<Application>().filesDir, "downloads")
                if (dlDir.exists()) dlDir.deleteRecursively()
            } catch (e: Exception) {
                Log.e(TAG, "clearAllData DB clear failed", e)
            }
            prefs.edit().clear().apply()
            _isDarkTheme.value = true
            _audioQuality.value = "auto"
            _crossfadeEnabled.value = false
            _crossfadeDuration.value = 3f
            _downloadQuality.value = "auto"
            _downloadWifiOnly.value = true
            _lastBackupDate.value = 0L
            _normalizationEnabled.value = false
            _gaplessEnabled.value = true
            _showCodec.value = false
            _keepScreenOn.value = false
            _hideThumbnail.value = false
            _cropAlbumArt.value = false
            _pureBlack.value = true
            _dynamicColors.value = false
            _uiDensity.value = "comfortable"
            _dataSaver.value = false
            _bluetoothAutoPlay.value = true
            _pauseOnMute.value = true
            _hideVideoSongs.value = false
            _hideYoutubeShorts.value = false
            _highRefreshRate.value = true
            _squigglySlider.value = false
            _canvasEnabled.value = true
            _lyricsKaraoke.value = false
            _translateLyrics.value = false
            _queueReorder.value = true
            _gridLibrary.value = true
            _showStats.value = true
            _cacheLimit.value = 500
            _recentLimit.value = 10
            _localMediaScan.value = false
            _podcastEnabled.value = false
            _spotifyImport.value = false
            _listenTogether.value = false
            _blurStrength.value = 36f
            _gradientOverlay.value = true
            _showLyricsOnPlayer.value = false
            _autoDownloadWifi.value = false
            _deleteAfter30Days.value = false
            _sortBy.value = "recent"
            _groupBy.value = "none"
            _showYear.value = true
            _showFileSize.value = false
            _tempoPitch.value = false
            _androidAuto.value = true
        }
    }
}
