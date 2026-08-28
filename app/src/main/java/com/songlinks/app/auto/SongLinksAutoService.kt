package com.songlinks.app.auto

import android.os.Bundle
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.common.MediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaSession

class SongLinksAutoService : MediaLibraryService() {
    private var librarySession: MediaLibrarySession? = null
    override fun onCreate() {
        super.onCreate()
        val p = PlayerProvider.player ?: return
        librarySession = MediaLibrarySession.Builder(this, p, LibrarySessionCallback()).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession
    override fun onDestroy() {
        librarySession?.release(); librarySession = null; super.onDestroy()
    }
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder().setMediaId("root").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle("SongLinks").build()).build()
            return androidx.concurrent.futures.CallbackToFutureAdapter.getFuture { completer -> completer.set(LibraryResult.ofItem(root, params)); "autoRoot" }
        }
        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return androidx.concurrent.futures.CallbackToFutureAdapter.getFuture { completer -> completer.set(LibraryResult.ofItemList(ImmutableList.of(), params)); "autoChildren" }
        }
    }
    object PlayerProvider { var player: androidx.media3.exoplayer.ExoPlayer? = null }
}
