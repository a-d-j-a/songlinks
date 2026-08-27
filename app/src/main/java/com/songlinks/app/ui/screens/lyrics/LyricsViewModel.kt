package com.songlinks.app.ui.screens.lyrics

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

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
    }

    fun fetchLyrics(title: String, artist: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _lyricsText.value = null
            _syncedLines.value = emptyList()
            _hasSyncedLyrics.value = false

            try {
                val baseUrl = getBaseUrl()
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                val url = "$baseUrl/lyrics?title=$encodedTitle&artist=$encodedArtist"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val responseBody = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Server error: ${response.code}")
                        }
                        response.body?.string() ?: throw Exception("Empty response")
                    }
                }

                val json = JsonParser.parseString(responseBody).asJsonObject
                val plainLyrics = json.get("lyrics")?.asString
                val syncedArray = json.getAsJsonArray("syncedLines")

                if (syncedArray != null && syncedArray.size() > 0) {
                    val lines = mutableListOf<SyncedLine>()
                    for (element in syncedArray) {
                        val obj = element.asJsonObject
                        lines.add(
                            SyncedLine(
                                timeMs = obj.get("timeMs")?.asLong ?: 0L,
                                text = obj.get("text")?.asString ?: ""
                            )
                        )
                    }
                    _syncedLines.value = lines
                    _hasSyncedLyrics.value = true
                    _lyricsText.value = lines.joinToString("\n") { it.text }
                } else if (plainLyrics != null) {
                    _lyricsText.value = plainLyrics
                    _hasSyncedLyrics.value = false
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

    fun updateCurrentPosition(positionMs: Long) {
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

    private fun getBaseUrl(): String {
        val prefs = getApplication<Application>().getSharedPreferences(
            "songlinks_prefs", Context.MODE_PRIVATE
        )
        return prefs.getString("server_url", "http://10.0.2.2:3000")
            ?: "http://10.0.2.2:3000"
    }
}

data class SyncedLine(
    val timeMs: Long,
    val text: String
)
