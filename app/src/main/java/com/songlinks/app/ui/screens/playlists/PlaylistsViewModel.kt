package com.songlinks.app.ui.screens.playlists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.SongLinksApp
import com.songlinks.app.api.SongResult
import com.songlinks.app.data.local.PlaylistEntity
import com.songlinks.app.data.local.PlaylistSongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as SongLinksApp).database.playlistDao()

    val playlists: StateFlow<List<PlaylistEntity>> = dao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist: StateFlow<PlaylistEntity?> = _selectedPlaylist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<PlaylistSongEntity>>(emptyList())
    val playlistSongs: StateFlow<List<PlaylistSongEntity>> = _playlistSongs.asStateFlow()

    private val _playlistCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playlistCounts: StateFlow<Map<Long, Int>> = _playlistCounts.asStateFlow()

    init {
        loadPlaylistCounts()
    }

    private fun loadPlaylistCounts() {
        viewModelScope.launch {
            val counts = mutableMapOf<Long, Int>()
            playlists.value.forEach { playlist ->
                counts[playlist.id] = dao.getPlaylistCount(playlist.id)
            }
            _playlistCounts.value = counts
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val id = dao.insertPlaylist(PlaylistEntity(name = name))
            _playlistCounts.value = _playlistCounts.value + (id to 0)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            dao.deletePlaylist(playlist.id)
            _playlistCounts.value = _playlistCounts.value - playlist.id
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = null
                _playlistSongs.value = emptyList()
            }
        }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        viewModelScope.launch {
            dao.updatePlaylist(playlist.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity) {
        _selectedPlaylist.value = playlist
        viewModelScope.launch {
            dao.getSongsInPlaylist(playlist.id).collect { songs ->
                _playlistSongs.value = songs
                _playlistCounts.value = _playlistCounts.value + (playlist.id to songs.size)
            }
        }
    }

    fun deselectPlaylist() {
        _selectedPlaylist.value = null
        _playlistSongs.value = emptyList()
    }

    fun addToPlaylist(playlistId: Long, song: SongResult) {
        viewModelScope.launch {
            val maxPos = _playlistSongs.value.maxOfOrNull { it.position } ?: 0
            dao.insertSongToPlaylist(
                PlaylistSongEntity(
                    playlistId = playlistId,
                    source = song.source,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    cover = song.cover,
                    page = song.page,
                    duration = song.duration,
                    position = maxPos + 1
                )
            )
            _playlistCounts.value = _playlistCounts.value + (playlistId to (dao.getPlaylistCount(playlistId)))
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            dao.removeSongFromPlaylist(playlistId, songId)
            _playlistCounts.value = _playlistCounts.value + (playlistId to (dao.getPlaylistCount(playlistId)))
        }
    }

    fun playlistSongToResult(entity: PlaylistSongEntity): SongResult {
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
