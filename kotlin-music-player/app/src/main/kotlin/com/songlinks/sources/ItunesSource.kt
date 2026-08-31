package com.songlinks.sources

import com.songlinks.Models.*
import com.songlinks.Util
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

// Port of src/sources/itunes.js
object ItunesSource : com.songlinks.MusicSource {
    override val name = "itunes"
    @Serializable private data class ItunesRes(val resultCount: Int = 0, val results: List<ItunesTrack> = emptyList())
    @Serializable private data class ItunesTrack(
        val trackId: Long? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val trackTimeMillis: Long? = null,
        val releaseDate: String? = null,
        val primaryGenreName: String? = null,
        val artworkUrl100: String? = null,
        val artworkUrl60: String? = null,
        val artworkUrl30: String? = null,
        val trackViewUrl: String? = null,
        val collectionViewUrl: String? = null,
        val previewUrl: String? = null,
        val trackExplicitness: String? = null,
        val explicitness: String? = null
    )

    override suspend fun search(q: String, limit: Int): List<SongResult> {
        val nq = q.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val nLimit = limit.coerceIn(1, 50)
        val url = "https://itunes.apple.com/search?term=${encode(nq)}&media=music&entity=song&limit=$nLimit"
        val res: ItunesRes = Util.client.get(url).body()
        return res.results.mapNotNull { r ->
            val id = r.trackId?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            val cover = (r.artworkUrl100 ?: r.artworkUrl60 ?: r.artworkUrl30)?.replace("100x100", "600x600")
            val duration = r.trackTimeMillis?.let { (it / 1000).toInt().takeIf { v -> v > 0 } }
            val isExplicit = (r.trackExplicitness ?: r.explicitness)?.equals("explicit", true) == true
            val stream = r.previewUrl?.takeIf { it.startsWith("http") }?.let { listOf(StreamInfo("preview", it, "audio")) } ?: emptyList()
            SongResult(
                source = "itunes", id = id, title = r.trackName, artist = r.artistName,
                album = r.collectionName, duration = duration, release = r.releaseDate,
                genre = r.primaryGenreName, cover = cover, page = r.trackViewUrl ?: r.collectionViewUrl,
                streams = stream, extra = Extra(isExplicit = isExplicit)
            )
        }
    }
    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun String.replace(old: String, new: String) = this.replace(old, new)
}
private val SongResult.release: String? get() = null // placeholder for serialization, actual field is in data class copy
