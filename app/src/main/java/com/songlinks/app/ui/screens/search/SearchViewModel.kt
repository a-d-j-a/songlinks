package com.songlinks.app.ui.screens.search

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.api.SongApi
import com.songlinks.app.api.SongResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "SearchViewModel"

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val api = SongApi(application)
    private val prefs = application.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SongResult>>(emptyList())
    val results: StateFlow<List<SongResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activeSources = MutableStateFlow(setOf("itunes", "jiosaavn", "ytmusic"))
    val activeSources: StateFlow<Set<String>> = _activeSources.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _query
                .debounce(300L)
                .distinctUntilChanged()
                .collectLatest { searchQuery ->
                    if (searchQuery.isBlank()) {
                        _results.value = emptyList()
                        _error.value = null
                        _isLoading.value = false
                    } else {
                        performSearch(searchQuery)
                    }
                }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _results.value = emptyList()
            _error.value = null
        }
    }

    fun toggleSource(source: String) {
        val current = _activeSources.value.toMutableSet()
        if (current.contains(source)) {
            if (current.size > 1) current.remove(source)
        } else {
            current.add(source)
        }
        _activeSources.value = current

        if (_query.value.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(_query.value)
            }
        }
    }

    private suspend fun performSearch(searchQuery: String) {
        _isLoading.value = true
        _error.value = null
        _results.value = emptyList()
        try {
            val searchResults = api.search(searchQuery, _activeSources.value)
            _results.value = searchResults
            if (searchResults.isNotEmpty()) saveToRecent(searchQuery)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            val msg = e.message ?: ""
            _error.value = when {
                msg.contains("Unable to resolve host", true) || msg.contains("UnknownHost", true) ->
                    "No internet connection. Check your network."
                msg.contains("connect", true) || msg.contains("timeout", true) || msg.contains("Socket", true) ->
                    "Cannot reach network. Please check your internet connection."
                else -> msg.ifBlank { "Search failed" }
            }
        } finally {
            _isLoading.value = false
        }
    }

    private fun saveToRecent(query: String) {
        try {
            val json = prefs.getString("recent_searches_json", null)
            val type = object : TypeToken<MutableList<String>>() {}.type
            val recent: MutableList<String> = if (json != null) Gson().fromJson(json, type) ?: mutableListOf() else mutableListOf()
            // Migrate old set format if exists
            if (recent.isEmpty()) {
                prefs.getStringSet("recent_searches", null)?.let { set ->
                    recent.addAll(set)
                }
            }
            recent.remove(query)
            recent.add(0, query)
            val limited = recent.take(10)
            prefs.edit().putString("recent_searches_json", Gson().toJson(limited)).apply()
            prefs.edit().remove("recent_searches").apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveToRecent failed", e)
        }
    }

    fun setInitialQuery(initialQuery: String) {
        if (initialQuery.isNotBlank()) {
            _query.value = initialQuery
        }
    }

    fun retry() {
        if (_query.value.isNotBlank()) {
            viewModelScope.launch {
                performSearch(_query.value)
            }
        }
    }
}
