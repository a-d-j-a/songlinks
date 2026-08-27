package com.songlinks.app.ui.screens.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.app.SongLinksApp
import com.songlinks.app.api.SongResult
import com.songlinks.app.data.local.DownloadEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as SongLinksApp).database.downloadDao()

    val downloads: StateFlow<List<DownloadEntity>> = dao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize.asStateFlow()

    init {
        loadTotalSize()
    }

    private fun loadTotalSize() {
        viewModelScope.launch {
            _totalSize.value = dao.getTotalSize()
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            val file = java.io.File(download.filePath)
            if (file.exists()) {
                file.delete()
            }
            dao.delete(download.songId)
            _totalSize.value = dao.getTotalSize()
        }
    }

    fun downloadEntityToResult(entity: DownloadEntity): SongResult {
        return SongResult(
            source = entity.source,
            id = entity.songId,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            cover = entity.cover,
            page = entity.page,
            duration = entity.duration,
            streamUrl = entity.filePath
        )
    }
}
