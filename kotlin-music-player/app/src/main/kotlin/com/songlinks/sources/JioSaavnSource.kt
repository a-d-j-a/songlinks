package com.songlinks.sources

import com.songlinks.Des
import com.songlinks.Extra
import com.songlinks.MusicSource
import com.songlinks.SongResult
import com.songlinks.StreamInfo
import com.songlinks.Util
import com.songlinks.FetchException
import com.songlinks.HttpFetchException
import com.songlinks.TimeoutException
import com.songlinks.NetworkException
import kotlinx.serialization.Serializable
import java.net.URLEncoder

object JioSaavnSource : MusicSource {
    override val name = "jiosaavn"

    const val SA_AVN_API = "https://www.jiosaavn.com/api.php"
    const val DEFAULT_LIMIT = 10
    const val MIN_LIMIT = 1
    const val MAX_LIMIT = 50
    const val MAX_QUERY_LEN = 300
    val QUALITY_LADDER = listOf("12", "48", "96", "160", "320")

    // --- Serialization models ---

    @Serializable
    private data class SaavnResp(val results: List<SaavnRow>? = null)

    @Serializable
    private data class SaavnRow(
        val id: String? = null,
        val title: String? = null,
        val song: String? = null,
        val subtitle: String? = null,
        val language: String? = null,
        val play_count: String? = null,
        val image: String? = null,
        val perma_url: String? = null,
        val url: String? = null,
        val explicit_content: String? = null,
        val album: String? = null,
        val more_info: SaavnInfo? = null
    )

    @Serializable
    private data class SaavnInfo(
        val album: String? = null,
        val duration: String? = null,
        val encrypted_media_url: String? = null,
        val is_dolby_content: String? = null,
        val singers: String? = null,
        val artist: String? = null,
        val primary_artists: String? = null,
        val artistMap: ArtistMap? = null
    )

    @Serializable
    private data class ArtistMap(
        val primary_artists: List<Artist>? = null,
        val featured_artists: List<Artist>? = null,
        val artists: List<Artist>? = null
    )

    @Serializable
    private data class Artist(
        val name: String? = null,
        val title: String? = null,
        val text: String? = null,
        val subTitle: String? = null
    )

    // --- Helpers ---

    private fun normalizeQuery(q: String): String? {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.length > MAX_QUERY_LEN) trimmed.substring(0, MAX_QUERY_LEN) else trimmed
    }

    private fun normalizeLimit(raw: Int): Int {
        if (raw < MIN_LIMIT) return MIN_LIMIT
        if (raw > MAX_LIMIT) return MAX_LIMIT
        return raw
    }

    private fun parseStringField(value: String?): String? {
        if (value == null) return null
        return try {
            val s = value.trim()
            s.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIntField(value: String?): Int? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val n = trimmed.toInt()
            if (n >= 0) n else null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseDuration(value: String?): Int? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val n = trimmed.toDouble()
            if (n.isFinite() && n > 0) {
                val secs = Math.round(n).toInt()
                if (secs > 0) secs else null
            } else null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseCoverImage(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.replace("150x150", "500x500")
    }

    private fun upgradeMediaUrl(url: String, kbps: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return url
        val target = kbps.trim()
        if (target.isEmpty()) return url
        return try {
            if (Regex("_\\d+\\.mp4", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
                trimmed.replace(Regex("_(\\d+)\\.mp4", RegexOption.IGNORE_CASE), "_${target}.mp4")
            } else {
                val genericMatch = Regex("_(\\d+)\\.([a-z0-9]+)(\\?.*)?$", RegexOption.IGNORE_CASE).find(trimmed)
                if (genericMatch != null) {
                    val ext = genericMatch.groupValues[2]
                    val query = genericMatch.groupValues[3].ifEmpty { "" }
                    trimmed.replace(
                        Regex("_(\\d+)\\.([a-z0-9]+)(\\?.*)?$", RegexOption.IGNORE_CASE),
                        "_${target}.${ext}${query}"
                    )
                } else {
                    url
                }
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun parseArtists(meta: SaavnInfo?, fallbackSubtitle: String?): String? {
        if (meta == null) return parseStringField(fallbackSubtitle)

        val primary = meta.artistMap?.primary_artists.orEmpty()
        val featured = meta.artistMap?.featured_artists.orEmpty()
        val all = meta.artistMap?.artists.orEmpty()

        var combined = primary + featured
        if (combined.isEmpty() && all.isNotEmpty()) combined = all

        val names = combined.mapNotNull { artist ->
            if (artist == null) return@mapNotNull null
            val n = artist.name ?: artist.title ?: artist.text ?: artist.subTitle ?: ""
            try {
                val s = n.trim()
                s.ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }

        val uniq = names.distinct()
        if (uniq.isNotEmpty()) return uniq.joinToString(", ")

        return parseStringField(meta.singers)
            ?: parseStringField(meta.artist)
            ?: parseStringField(meta.primary_artists)
            ?: parseStringField(fallbackSubtitle)
    }

    private fun buildStreams(decrypted: String?): List<StreamInfo> {
        if (decrypted == null) return emptyList()
        val trimmed = decrypted.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.startsWith("http", ignoreCase = true)) return emptyList()

        val ext = when {
            trimmed.contains(".mp4") -> "mp4"
            trimmed.contains(".aac") -> "aac"
            else -> "audio"
        }

        return QUALITY_LADDER.mapNotNull { q ->
            val url = upgradeMediaUrl(trimmed, q)
            if (url.isNotBlank() && url.startsWith("http", ignoreCase = true)) {
                StreamInfo(quality = "${q}kbps", url = url.trim(), type = ext)
            } else null
        }
    }

    private fun mapResult(row: SaavnRow): SongResult? {
        val rawId = row.id
        if (rawId == null) return null
        val idStr = try {
            rawId.trim()
        } catch (_: Exception) {
            return null
        }
        if (idStr.isEmpty() || idStr == "null" || idStr == "undefined" || idStr.equals("nan", ignoreCase = true)) return null

        val meta = row.more_info

        val title = parseStringField(row.title)
            ?: parseStringField(row.song)
            ?: parseStringField(meta?.album)

        val artist = parseArtists(meta, row.subtitle)

        val album = parseStringField(meta?.album) ?: parseStringField(row.album)

        val duration = parseDuration(meta?.duration)

        val language = parseStringField(row.language)

        val playCount = parseIntField(row.play_count)

        val cover = parseCoverImage(row.image)

        val page = parseStringField(row.perma_url) ?: parseStringField(row.url)

        var decrypted: String? = null
        val enc = meta?.encrypted_media_url
        if (enc != null) {
            val encTrimmed = enc.trim()
            if (encTrimmed.isNotEmpty()) {
                try {
                    val result = Des.decryptBase64(encTrimmed)
                    val resultTrimmed = result.trim()
                    if (resultTrimmed.isNotEmpty() && resultTrimmed.startsWith("http", ignoreCase = true)) {
                        decrypted = resultTrimmed
                    }
                } catch (_: Exception) {
                    decrypted = null
                }
            }
        }

        val streams = buildStreams(decrypted)

        val isExplicitRaw = row.explicit_content
        val isExplicit = isExplicitRaw == "1" || isExplicitRaw == "true"

        val isDolbyRaw = meta?.is_dolby_content
        val isDolby = isDolbyRaw == "true" || isDolbyRaw == "1"

        return SongResult(
            source = "jiosaavn",
            id = idStr,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            language = language,
            playCount = playCount,
            cover = cover,
            page = page,
            streams = streams,
            extra = Extra(isExplicit = isExplicit, isDolby = isDolby)
        )
    }

    // --- Main search ---

    override suspend fun search(q: String, limit: Int): List<SongResult> {
        val normalizedQ = normalizeQuery(q) ?: return emptyList()
        val normalizedLimit = normalizeLimit(limit)

        val params = listOf(
            "__call" to "search.getResults",
            "q" to normalizedQ,
            "_format" to "json",
            "_marker" to "0",
            "api_version" to "4",
            "ctx" to "web6dot0",
            "p" to "1",
            "n" to normalizedLimit.toString()
        )
        val queryString = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val url = "$SA_AVN_API?$queryString"

        val json: SaavnResp = try {
            Util.fetchJson(url)
        } catch (err: Exception) {
            val msg = err.message ?: err.toString()
            val wrapped = FetchException(
                "jiosaavn search failed for \"${normalizedQ.take(50)}\": $msg",
                err
            )
            when (err) {
                is HttpFetchException -> throw FetchException(
                    "jiosaavn search failed for \"${normalizedQ.take(50)}\": ${err.message}",
                    err
                )
                is TimeoutException -> throw FetchException(
                    "jiosaavn search failed for \"${normalizedQ.take(50)}\": ${err.message}",
                    err
                )
                is NetworkException -> throw FetchException(
                    "jiosaavn search failed for \"${normalizedQ.take(50)}\": ${err.message}",
                    err
                )
                else -> throw wrapped
            }
        }

        val resultsArray = json.results ?: return emptyList()

        return resultsArray.mapNotNull { row ->
            try {
                mapResult(row)
            } catch (_: Exception) {
                null
            }
        }
    }
}
