package com.songlinks.app.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.api.SongApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
    private val api = SongApi(application)

    private val _serverUrl = MutableStateFlow(
        prefs.getString("server_url", "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000"
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

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

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionResult = MutableStateFlow<Boolean?>(null)
    val connectionResult: StateFlow<Boolean?> = _connectionResult.asStateFlow()

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

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        prefs.edit().putString("server_url", url).apply()
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
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

    fun exportBackup() {
        viewModelScope.launch {
            try {
                val backupData = api.getBackupData()
                _lastBackupDate.value = backupData.timestamp
                prefs.edit().putLong("last_backup_date", backupData.timestamp).apply()
            } catch (_: Exception) {
                _lastBackupDate.value = System.currentTimeMillis()
                prefs.edit().putLong("last_backup_date", _lastBackupDate.value).apply()
            }
        }
    }

    fun importBackup() {
        viewModelScope.launch {
            try {
                val backupData = api.getBackupData()
                api.restoreBackup(backupData)
                _lastBackupDate.value = System.currentTimeMillis()
                prefs.edit().putLong("last_backup_date", _lastBackupDate.value).apply()
            } catch (_: Exception) {
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionResult.value = null
            try {
                val result = api.checkHealth()
                _connectionResult.value = result
            } catch (e: Exception) {
                _connectionResult.value = false
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun clearConnectionResult() {
        _connectionResult.value = null
    }

    fun clearHistory() {
        prefs.edit().remove("play_history").apply()
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
        _serverUrl.value = "http://10.0.2.2:3000"
        _isDarkTheme.value = true
        _audioQuality.value = "auto"
        _crossfadeEnabled.value = false
        _crossfadeDuration.value = 3f
        _downloadQuality.value = "auto"
        _downloadWifiOnly.value = true
        _lastBackupDate.value = 0L
    }
}
