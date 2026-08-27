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
        prefs.getString("server_url", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080"
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
        _serverUrl.value = "http://10.0.2.2:8080"
        _isDarkTheme.value = true
        _audioQuality.value = "auto"
        _crossfadeEnabled.value = false
        _crossfadeDuration.value = 3f
    }
}
