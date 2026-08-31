package com.songlinks

import kotlinx.serialization.Serializable

@Serializable
data class StreamInfo(val quality: String = "unknown", val url: String = "", val type: String = "audio")

@Serializable
data class SongResult(
    val source: String,
    val id: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int? = null, // seconds
    val cover: String? = null,
    val page: String? = null,
    val streams: List<StreamInfo> = emptyList(),
    val genre: String? = null,
    val language: String? = null,
    val playCount: Int? = null,
    val extra: Extra? = null
)

@Serializable
data class Extra(val isExplicit: Boolean = false, val isDolby: Boolean = false)

@Serializable
data class SearchResponse(
    val ok: Boolean,
    val query: String,
    val limit: Int,
    val totalResults: Int,
    val tookMs: Long,
    val sources: List<String>,
    val perSource: List<PerSource>? = null,
    val warnings: List<Map<String,String>>? = null,
    val results: List<SongResult>
)

@Serializable
data class PerSource(val source: String, val ok: Boolean, val count: Int, val tookMs: Long, val error: String? = null)

interface MusicSource {
    val name: String
    suspend fun search(q: String, limit: Int): List<SongResult>
}
