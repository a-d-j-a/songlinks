package com.songlinks.app.ui.screens.downloads

import android.app.Application
import android.util.Log
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

private const val TAG = "DownloadsViewModel"

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as SongLinksApp).database.downloadDao()

    val downloads: StateFlow<List<DownloadEntity>> = dao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSize: StateFlow<Long> = downloads
        .let { flow ->
            kotlinx.coroutines.flow.map(flow) { list -> list.sumOf { it.fileSize } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        Log.d(TAG, "init")
    }

    fun deleteDownload(download: DownloadEntity) {
        Log.d(TAG, "deleteDownload: ${download.songId}")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(download.filePath)
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "Failed to delete file ${download.filePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "delete file error", e)
            }
            try { dao.delete(download.songId) } catch (e: Exception) { Log.e(TAG, "dao delete failed", e) }
        }
    }

    fun downloadEntityToResult(entity: DownloadEntity): SongResult {
        Log.d(TAG, "downloadEntityToResult: ${entity.songId}")
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
