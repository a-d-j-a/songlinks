package com.songlinks.app.ui.screens.search

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.api.SongApi
import com.songlinks.app.api.SongResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            _query
                .debounce(300L)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { searchQuery ->
                    performSearch(searchQuery)
                }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
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
            viewModelScope.launch {
                performSearch(_query.value)
            }
        }
    }

    private suspend fun performSearch(searchQuery: String) {
        _isLoading.value = true
        _error.value = null
        try {
            val searchResults = api.search(searchQuery, _activeSources.value)
            _results.value = searchResults
            saveToRecent(searchQuery)
        } catch (e: Exception) {
            _error.value = e.message ?: "Search failed"
        } finally {
            _isLoading.value = false
        }
    }

    private fun saveToRecent(query: String) {
        val recent = prefs.getStringSet("recent_searches", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        recent.remove(query)
        recent.add(query)
        val limited = recent.toList().takeLast(5).toSet()
        prefs.edit().putStringSet("recent_searches", limited).apply()
    }

    fun setInitialQuery(initialQuery: String) {
        if (initialQuery.isNotBlank() && _query.value.isEmpty()) {
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
