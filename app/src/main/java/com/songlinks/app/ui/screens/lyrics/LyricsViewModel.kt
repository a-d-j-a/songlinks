package com.songlinks.app.ui.screens.lyrics

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.api.DirectApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "LyricsViewModel"

class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val _lyricsText = MutableStateFlow<String?>(null)
    val lyricsText: StateFlow<String?> = _lyricsText.asStateFlow()

    private val _syncedLines = MutableStateFlow<List<SyncedLine>>(emptyList())
    val syncedLines: StateFlow<List<SyncedLine>> = _syncedLines.asStateFlow()

    private val _currentLineIndex = MutableStateFlow(-1)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hasSyncedLyrics = MutableStateFlow(false)
    val hasSyncedLyrics: StateFlow<Boolean> = _hasSyncedLyrics.asStateFlow()

    init {
        Log.d(TAG, "init")
    }

    fun fetchLyrics(title: String, artist: String) {
        Log.d(TAG, "fetchLyrics: title=$title, artist=$artist")
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lyricsText.value = null
            _syncedLines.value = emptyList()
            _hasSyncedLyrics.value = false

            try {
                val response = DirectApi.getLyrics(title, artist)

                if (response.lyrics.isNotBlank()) {
                    _lyricsText.value = response.lyrics
                    _hasSyncedLyrics.value = false
                } else if (!response.syncedLyrics.isNullOrBlank()) {
                    val lines = parseSyncedLyrics(response.syncedLyrics)
                    _syncedLines.value = lines
                    _hasSyncedLyrics.value = true
                    _lyricsText.value = lines.joinToString("\n") { it.text }
                } else {
                    _error.value = "No lyrics found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to fetch lyrics"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseSyncedLyrics(syncedLyrics: String): List<SyncedLine> {
        val lines = mutableListOf<SyncedLine>()
        for (line in syncedLyrics.lines()) {
            val match = Regex("""\[(\d+):(\d+\.?\d*)\]\s*(.*)""").matchEntire(line.trim())
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toDoubleOrNull() ?: 0.0
                val text = match.groupValues[3]
                val timeMs = (minutes * 60 * 1000 + (seconds * 1000).toLong())
                lines.add(SyncedLine(timeMs = timeMs, text = text))
            }
        }
        return lines
    }

    fun updateCurrentPosition(positionMs: Long) {
        Log.d(TAG, "updateCurrentPosition: $positionMs")
        val lines = _syncedLines.value
        if (lines.isEmpty()) return

        var index = -1
        for (i in lines.indices) {
            if (positionMs >= lines[i].timeMs) {
                index = i
            } else {
                break
            }
        }
        _currentLineIndex.value = index
    }
}

data class SyncedLine(
    val timeMs: Long,
    val text: String
)
