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

    private fun buildBackupMap(): Map<String, String> {
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
            "dark_theme" to _isDarkTheme.value.toString()
        )
    }

    fun exportBackup() {
        Log.d(TAG, "exportBackup: saving local data to SharedPreferences backup")
        viewModelScope.launch {
            try {
                val backupMap = buildBackupMap()
                val json = Gson().toJson(backupMap)
                prefs.edit().putString("local_backup", json).apply()
                _lastBackupDate.value = System.currentTimeMillis()
                prefs.edit().putLong("last_backup_date", _lastBackupDate.value).apply()
                Log.d(TAG, "exportBackup: success, timestamp=${_lastBackupDate.value}")
            } catch (e: Exception) {
                Log.e(TAG, "exportBackup failed", e)
            }
        }
    }

    fun getExportJson(): String {
        Log.d(TAG, "getExportJson")
        return Gson().toJson(buildBackupMap())
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
        }
    }
}
