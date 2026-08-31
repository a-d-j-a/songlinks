package com.songlinks.sources

import com.songlinks.Extra
import com.songlinks.SongResult
import com.songlinks.StreamInfo
import com.songlinks.Util
import kotlinx.serialization.Serializable

object ItunesSource : com.songlinks.MusicSource {
    override val name = "itunes"

    private const val ITUNES_BASE = "https://itunes.apple.com/search"
    private const val DEFAULT_LIMIT = 10
    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 50
    private const val MAX_QUERY_LEN = 300
    private const val ARTWORK_SMALL = "100x100"
    private const val ARTWORK_LARGE = "600x600"

    @Serializable
    private data class ItunesRes(
        val resultCount: Int = 0,
        val results: List<ItunesTrack> = emptyList()
    )

    @Serializable
    private data class ItunesTrack(
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
        val explicitness: String? = null,
        val collectionExplicitness: String? = null
    )

    // -- helpers --

    private fun normalizeQuery(q: String): String? {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.length > MAX_QUERY_LEN) trimmed.substring(0, MAX_QUERY_LEN) else trimmed
    }

    private fun normalizeLimit(raw: Int): Int {
        val n = raw.coerceIn(MIN_LIMIT, MAX_LIMIT)
        return if (n < MIN_LIMIT) MIN_LIMIT else if (n > MAX_LIMIT) MAX_LIMIT else n
    }

    private fun upgradeArtwork(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.replace(ARTWORK_SMALL, ARTWORK_LARGE)
    }

    private fun resolveArtwork(r: ItunesTrack): String? {
        val candidate = listOf(r.artworkUrl100, r.artworkUrl60, r.artworkUrl30)
            .firstOrNull { !it.isNullOrBlank() && it.trim().isNotEmpty() }
            ?: return null
        return upgradeArtwork(candidate) ?: candidate.trim()
    }

    private fun parseDuration(trackTimeMillis: Long?): Int? {
        if (trackTimeMillis == null) return null
        val num = trackTimeMillis.toDouble()
        if (!num.isFinite() || num <= 0) return null
        val secs = Math.round(num / 1000.0).toInt()
        return if (secs > 0) secs else null
    }

    private fun parseStringField(val_: String?): String? {
        if (val_ == null) return null
        return val_.trim().ifEmpty { null }
    }

    private fun isExplicit(r: ItunesTrack): Boolean {
        val v = r.trackExplicitness ?: r.explicitness ?: r.collectionExplicitness ?: return false
        return v.trim().equals("explicit", ignoreCase = true)
    }

    private fun buildStreams(r: ItunesTrack): List<StreamInfo> {
        val url = r.previewUrl?.trim() ?: return emptyList()
        if (url.isEmpty()) return emptyList()
        if (!url.startsWith("http", ignoreCase = true)) return emptyList()
        return listOf(StreamInfo(quality = "preview", url = url, type = "audio"))
    }

    private fun mapResult(r: ItunesTrack): SongResult? {
        val rawId = r.trackId ?: return null
        val idStr = rawId.toString().trim()
        if (idStr.isEmpty() || idStr.equals("null", ignoreCase = true) ||
            idStr.equals("undefined", ignoreCase = true) || idStr.equals("nan", ignoreCase = true)
        ) return null

        return SongResult(
            source = "itunes",
            id = idStr,
            title = parseStringField(r.trackName),
            artist = parseStringField(r.artistName),
            album = parseStringField(r.collectionName),
            duration = parseDuration(r.trackTimeMillis),
            cover = resolveArtwork(r),
            page = parseStringField(r.trackViewUrl) ?: parseStringField(r.collectionViewUrl),
            streams = buildStreams(r),
            genre = parseStringField(r.primaryGenreName),
            release = parseStringField(r.releaseDate),
            extra = Extra(isExplicit = isExplicit(r))
        )
    }

    override suspend fun search(q: String, limit: Int): List<SongResult> {
        val normalizedQ = normalizeQuery(q) ?: return emptyList()
        val normalizedLimit = normalizeLimit(limit)

        val encoded = java.net.URLEncoder.encode(normalizedQ, "UTF-8")
        val url = "$ITUNES_BASE?term=$encoded&media=music&entity=song&limit=$normalizedLimit"

        val json = try {
            Util.fetchJson<ItunesRes>(url, timeoutMs = Util.DEFAULT_TIMEOUT_MS)
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            throw IllegalStateException(
                "itunes search failed for \"${normalizedQ.take(50)}\": $msg", e
            )
        }

        if (json == null) return emptyList()

        val resultsArray = json.results
        if (resultsArray.isEmpty()) return emptyList()

        val mapped = mutableListOf<SongResult>()
        for (r in resultsArray) {
            try {
                val m = mapResult(r)
                if (m != null) mapped.add(m)
            } catch (_: Exception) {
                continue
            }
        }
        return mapped
    }
}
