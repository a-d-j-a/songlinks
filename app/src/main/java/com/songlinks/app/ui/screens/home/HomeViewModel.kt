package com.songlinks.app.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.api.SongResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    val featuredSongs = listOf(
        SongResult(
            id = "featured_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            cover = "https://picsum.photos/seed/blinding/300/300",
            source = "featured"
        ),
        SongResult(
            id = "featured_2",
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "\u00f7 (Divide)",
            cover = "https://picsum.photos/seed/shapeofyou/300/300",
            source = "featured"
        ),
        SongResult(
            id = "featured_3",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            cover = "https://picsum.photos/seed/bohemian/300/300",
            source = "featured"
        ),
        SongResult(
            id = "featured_4",
            title = "Billie Jean",
            artist = "Michael Jackson",
            album = "Thriller",
            cover = "https://picsum.photos/seed/billiejean/300/300",
            source = "featured"
        ),
        SongResult(
            id = "featured_5",
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            cover = "https://picsum.photos/seed/hotelcalifornia/300/300",
            source = "featured"
        )
    )

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
    }

    fun loadRecent() {
        val saved = prefs.getStringSet("recent_searches", emptySet()) ?: emptySet()
        _recentSearches.value = saved.toList().take(5)
    }

    fun clearRecent() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent_searches").apply()
    }
}
