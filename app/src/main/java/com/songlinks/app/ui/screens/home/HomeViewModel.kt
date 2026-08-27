package com.songlinks.app.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.api.SongResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import kotlinx.coroutines.launch

private const val TAG = "HomeViewModel"

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    init {
        loadRecent()
    }

    fun saveSearch(query: String) {
        if (query.isBlank()) return
        val current = _recentSearches.value.toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > 5) current.removeLast()
        _recentSearches.value = current
        prefs.edit().putStringSet("recent_searches", current.toSet()).apply()
        Log.d(TAG, "saveSearch: query=$query, recentCount=${_recentSearches.value.size}")
    }

    fun loadRecent() {
        val saved = prefs.getStringSet("recent_searches", emptySet()) ?: emptySet()
        _recentSearches.value = saved.toList().take(5)
        Log.d(TAG, "loadRecent: loaded ${_recentSearches.value.size} recent searches")
    }

    fun clearRecent() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent_searches").apply()
        Log.d(TAG, "clearRecent: recent searches cleared")
    }
}
