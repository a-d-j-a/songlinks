package com.songlinks.app.ui.screens.foryou

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.songlinks.app.api.SongApi
import com.songlinks.app.api.SongResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import kotlinx.coroutines.launch

private const val TAG = "ForYouViewModel"

class ForYouViewModel(application: Application) : AndroidViewModel(application) {

    private val api = SongApi(application)
    private val prefs = application.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _recommendations = MutableStateFlow<List<SongResult>>(emptyList())
    val recommendations: StateFlow<List<SongResult>> = _recommendations.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<SongResult>>(emptyList())
    val recentlyPlayed: StateFlow<List<SongResult>> = _recentlyPlayed.asStateFlow()

    private val _topArtists = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val topArtists: StateFlow<List<Pair<String, Int>>> = _topArtists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _playlists = MutableStateFlow<List<String>>(emptyList())
    val playlists: StateFlow<List<String>> = _playlists.asStateFlow()

    init {
        loadHistory()
        loadRecommendations()
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val app = getApplication() as com.songlinks.app.SongLinksApp
                val pls = app.database.playlistDao().getAllPlaylists()
                _playlists.value = pls.map { it.name }
            } catch (_: Exception) {}
        }
    }

    private fun loadHistory() {
        val json = prefs.getString("play_history", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<SongResult>>() {}.type
                val history: List<SongResult> = gson.fromJson(json, type) ?: emptyList()
                _recentlyPlayed.value = history.take(10)
                computeTopArtists(history)
                Log.d(TAG, "loadHistory: historySize=${history.size}, topArtistsCount=${_topArtists.value.size}")
            } catch (e: Exception) {
                _recentlyPlayed.value = emptyList()
                _topArtists.value = emptyList()
                Log.e(TAG, "loadHistory: failed to parse history", e)
            }
        } else {
            Log.d(TAG, "loadHistory: no history found")
        }
    }

    private fun computeTopArtists(history: List<SongResult>) {
        val artists = history
            .groupBy { it.artist }
            .map { (artist, songs) -> artist to songs.size }
            .sortedByDescending { it.second }
            .take(5)
        _topArtists.value = artists
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val topArtistsList = _topArtists.value
                if (topArtistsList.isEmpty()) {
                    _recommendations.value = emptyList()
                    _isLoading.value = false
                    return@launch
                }

                val queries = topArtistsList.take(3).map { it.first }
                val allResults = kotlinx.coroutines.coroutineScope {
                    val deferred = queries.map { query ->
                        async {
                            try {
                                api.search(query, setOf("itunes", "jiosaavn"))
                            } catch (e: Exception) {
                                Log.e(TAG, "recommendation query failed: $query", e)
                                emptyList<SongResult>()
                            }
                        }
                    }
                    deferred.awaitAll().flatten().filter { song ->
                        _recentlyPlayed.value.none { it.id == song.id }
                    }
                }
                _recommendations.value = allResults.distinctBy { it.id }.take(20)
                Log.d(TAG, "loadRecommendations: topArtists=${queries.size}, recommendationsCount=${_recommendations.value.size}")
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load recommendations"
                Log.e(TAG, "loadRecommendations: failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        Log.d(TAG, "refresh: refreshing data")
        viewModelScope.launch {
            _isRefreshing.value = true
            loadHistory()
            _isLoading.value = true
            _error.value = null
            try {
                val topArtistsList = _topArtists.value
                if (topArtistsList.isEmpty()) {
                    _recommendations.value = emptyList()
                } else {
                    val queries = topArtistsList.take(3).map { it.first }
                    val allResults = kotlinx.coroutines.coroutineScope {
                        val deferred = queries.map { query ->
                            async {
                                try { api.search(query, setOf("itunes", "jiosaavn")) } catch (_: Exception) { emptyList<SongResult>() }
                            }
                        }
                        deferred.awaitAll().flatten().filter { song -> _recentlyPlayed.value.none { it.id == song.id } }
                    }
                    _recommendations.value = allResults.distinctBy { it.id }.take(20)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load recommendations"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }
}
