package com.songlinks.app.ui.screens.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.songlinks.app.SongLinksApp
import com.songlinks.app.api.SongResult
import com.songlinks.app.data.local.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as SongLinksApp).database.songDao()
    private val prefs = application.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    val savedSongs: StateFlow<List<SongEntity>> = dao.getAllSaved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _history = MutableStateFlow<List<SongResult>>(emptyList())
    val history: StateFlow<List<SongResult>> = _history.asStateFlow()

    val stats: StateFlow<LibraryStats> = _history.map { historyList ->
        val totalSongs = historyList.size
        val topArtists = historyList
            .groupBy { it.artist }
            .map { (artist, songs) -> artist to songs.size }
            .sortedByDescending { it.second }
            .take(5)
        val totalDuration = historyList.sumOf { it.durationMs }
        val sourceBreakdown = historyList
            .groupBy { it.source }
            .map { (source, songs) -> source to songs.size }

        LibraryStats(
            totalSongsPlayed = totalSongs,
            topArtists = topArtists,
            totalDurationMs = totalDuration,
            sourceBreakdown = sourceBreakdown
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    init {
        loadHistory()
    }

    fun saveSong(song: SongResult) {
        viewModelScope.launch {
            dao.insert(
                SongEntity(
                    source = song.source,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    cover = song.cover,
                    page = song.page,
                    duration = song.duration
                )
            )
        }
    }

    fun unsaveSong(entity: SongEntity) {
        viewModelScope.launch {
            dao.delete(entity.songId)
        }
    }

    fun addToHistory(song: SongResult) {
        val current = _history.value.toMutableList()
        current.removeIf { it.id == song.id }
        current.add(0, song)
        if (current.size > 100) current.removeLast()
        _history.value = current
        saveHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("play_history").apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("play_history", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<SongResult>>() {}.type
                _history.value = gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                _history.value = emptyList()
            }
        }
    }

    private fun saveHistory() {
        val json = gson.toJson(_history.value)
        prefs.edit().putString("play_history", json).apply()
    }

    fun songEntityToResult(entity: SongEntity): SongResult {
        return SongResult(
            source = entity.source,
            id = entity.songId,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            cover = entity.cover,
            page = entity.page,
            duration = entity.duration
        )
    }
}

data class LibraryStats(
    val totalSongsPlayed: Int = 0,
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val totalDurationMs: Long = 0L,
    val sourceBreakdown: List<Pair<String, Int>> = emptyList()
)
