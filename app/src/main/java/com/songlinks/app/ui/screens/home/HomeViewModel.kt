package com.songlinks.app.ui.screens.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        prefs.edit().putString("recent_searches_json", Gson().toJson(current)).apply()
        prefs.edit().remove("recent_searches").apply()
        Log.d(TAG, "saveSearch: query=$query, recentCount=${_recentSearches.value.size}")
    }

    fun loadRecent() {
        val json = prefs.getString("recent_searches_json", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val list: List<String> = Gson().fromJson(json, type) ?: emptyList()
                _recentSearches.value = list.take(5)
                Log.d(TAG, "loadRecent: loaded ${_recentSearches.value.size} recent searches")
                return
            } catch (e: Exception) {
                Log.e(TAG, "loadRecent json parse failed", e)
            }
        }
        // Fallback migrate old set
        val saved = prefs.getStringSet("recent_searches", emptySet()) ?: emptySet()
        _recentSearches.value = saved.toList().take(5)
        Log.d(TAG, "loadRecent: loaded ${_recentSearches.value.size} recent searches (legacy)")
    }

    fun clearRecent() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent_searches_json").remove("recent_searches").apply()
        Log.d(TAG, "clearRecent: recent searches cleared")
    }
}
