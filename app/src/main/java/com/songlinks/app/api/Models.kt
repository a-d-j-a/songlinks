package com.songlinks.app.api

data class SearchResponse(
    val ok: Boolean,
    val query: String,
    val limit: Int,
    val totalResults: Int,
    val tookMs: Long,
    val sources: List<String>,
    val results: List<SongResult>
)

data class SongResult(
    val source: String = "",
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Int? = null,
    val cover: String? = null,
    val page: String? = null,
    val streams: List<Stream> = emptyList(),
    val language: String? = null,
    val playCount: Long? = null,
    val release: String? = null,
    val genre: String? = null,
    val quality: String = "",
    val streamUrl: String = "",
    val isPlaying: Boolean = false,
    val isSaved: Boolean = false
) {
    val coverUrl: String get() = cover ?: ""
    val pageUrl: String get() = page ?: ""
    val durationMs: Long get() = (duration ?: 0).toLong() * 1000L
}

data class Stream(
    val quality: String,
    val url: String,
    val type: String? = null
)

data class StreamResponse(
    val ok: Boolean,
    val videoId: String,
    val formats: List<StreamFormat>
)

data class StreamFormat(
    val quality: String,
    val url: String,
    val type: String? = null,
    val contentLength: Long? = null
)

data class LyricsResponse(
    val title: String = "",
    val artist: String = "",
    val lyrics: String = "",
    val syncedLyrics: String? = null
)

data class BackupData(
    val version: String = "1.0",
    val timestamp: Long = System.currentTimeMillis(),
    val songs: List<SongResult> = emptyList(),
    val history: List<SongResult> = emptyList(),
    val playlists: List<PlaylistBackup> = emptyList()
)

data class PlaylistBackup(
    val name: String,
    val songs: List<SongResult>
)
