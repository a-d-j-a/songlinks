package com.songlinks.app.ui.screens.playlists

import android.app.Application
import android.util.Log
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

private const val TAG = "PlaylistsViewModel"

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

    private var songsCollectJob: kotlinx.coroutines.Job? = null

    init {
        Log.d(TAG, "init")
        viewModelScope.launch {
            playlists.collect { list ->
                val counts = mutableMapOf<Long, Int>()
                for (pl in list) {
                    try { counts[pl.id] = dao.getPlaylistCount(pl.id) } catch (_: Exception) {}
                }
                _playlistCounts.value = counts
            }
        }
    }

    fun createPlaylist(name: String) {
        Log.d(TAG, "createPlaylist: $name")
        viewModelScope.launch {
            val id = dao.insertPlaylist(PlaylistEntity(name = name))
            _playlistCounts.value = _playlistCounts.value + (id to 0)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        Log.d(TAG, "deletePlaylist: ${playlist.id}")
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
        Log.d(TAG, "renamePlaylist: ${playlist.id} -> $newName")
        viewModelScope.launch {
            val updated = playlist.copy(name = newName, updatedAt = System.currentTimeMillis())
            dao.updatePlaylist(updated)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = updated
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity) {
        Log.d(TAG, "selectPlaylist: ${playlist.id}")
        songsCollectJob?.cancel()
        _selectedPlaylist.value = playlist
        songsCollectJob = viewModelScope.launch {
            dao.getSongsInPlaylist(playlist.id).collect { songs ->
                _playlistSongs.value = songs
                _playlistCounts.value = _playlistCounts.value + (playlist.id to songs.size)
            }
        }
    }

    fun deselectPlaylist() {
        Log.d(TAG, "deselectPlaylist")
        songsCollectJob?.cancel()
        songsCollectJob = null
        _selectedPlaylist.value = null
        _playlistSongs.value = emptyList()
    }

    fun addToPlaylist(playlistId: Long, song: SongResult) {
        Log.d(TAG, "addToPlaylist: playlistId=$playlistId, songId=${song.id}")
        viewModelScope.launch {
            try {
                // Get max position for target playlist, not selected one
                val currentSongs = if (_selectedPlaylist.value?.id == playlistId) _playlistSongs.value else emptyList()
                val maxPos = if (currentSongs.isNotEmpty()) currentSongs.maxOf { it.position } else {
                    // Fallback query if not selected
                    try { dao.getPlaylistCount(playlistId) } catch (_: Exception) { 0 }
                }
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
            } catch (e: Exception) {
                // Unique constraint -> already in playlist
                Log.w(TAG, "addToPlaylist failed (duplicate?)", e)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        Log.d(TAG, "removeSongFromPlaylist: playlistId=$playlistId, songId=$songId")
        viewModelScope.launch {
            dao.removeSongFromPlaylist(playlistId, songId)
            _playlistCounts.value = _playlistCounts.value + (playlistId to (dao.getPlaylistCount(playlistId)))
        }
    }

    fun playlistSongToResult(entity: PlaylistSongEntity): SongResult {
        Log.d(TAG, "playlistSongToResult: ${entity.songId}")
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
