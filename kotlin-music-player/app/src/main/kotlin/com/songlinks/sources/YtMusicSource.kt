package com.songlinks.sources

import com.songlinks.FetchException
import com.songlinks.MusicSource
import com.songlinks.SongResult
import com.songlinks.StreamInfo
import com.songlinks.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object YtMusicSource : MusicSource {
    override val name = "ytmusic"

    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val WEB_VERSION = "1.20241202.01.00"
    private const val ANDROID_VERSION = "20.10.38"
    private const val IOS_VERSION = "20.10.38"
    private const val SDK_VERSION = "34"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val javaClient = java.net.http.HttpClient.newHttpClient()

    private fun post(url: String, body: String, ua: String = "Mozilla/5.0"): String {
        val req = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("User-Agent", ua)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val resp = javaClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) throw FetchException("HTTP ${resp.statusCode()} from $url: ${resp.body().take(300)}")
        return resp.body()
    }

    // ── SEARCH ────────────────────────────────────────────────────────

    override suspend fun search(q: String, limit: Int): List<SongResult> = withContext(Dispatchers.IO) {
        val nq = q.trim().takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
        val nLimit = limit.coerceIn(1, 50)

        val body = """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"$WEB_VERSION","hl":"en","gl":"US"}},"query":"${nq.replace("\"", "\\\"")}"}"""
        val raw = try {
            post("$SEARCH_URL&prettyPrint=false", body)
        } catch (e: Exception) {
            throw FetchException("ytmusic search failed for \"${nq.take(50)}\": ${e.message}", e)
        }

        val root = try { json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { return@withContext emptyList() }
        val results = mutableListOf<SongResult>()
        extractSearchItems(root, results)
        results.distinctBy { it.id }.take(nLimit)
    }

    private fun extractSearchItems(el: JsonElement, out: MutableList<SongResult>) {
        if (el !is JsonObject) return

        // Direct musicResponsiveListItemRenderer
        val renderer = el["musicResponsiveListItemRenderer"]?.jsonObject
        if (renderer != null) {
            val item = parseSearchItem(renderer)
            if (item != null) out.add(item)
            return
        }

        // Recurse into arrays and objects
        for (v in el.values) {
            when (v) {
                is JsonArray -> v.forEach { extractSearchItems(it, out) }
                is JsonObject -> extractSearchItems(v, out)
            }
        }
    }

    private fun parseSearchItem(r: JsonObject): SongResult? {
        // videoId from overlay or playlistItemData
        val vid = r["overlay"]?.jsonObject
            ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("musicPlayButtonRenderer")?.jsonObject
            ?.get("playNavigationEndpoint")?.jsonObject
            ?.get("watchEndpoint")?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.content
            ?: r["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
            ?: return null

        if (vid.length !in 5..100 || vid.contains(Regex("\\s"))) return null

        // flexColumns
        val flex = r["flexColumns"]?.jsonArray ?: return null

        // Title from col[0]
        val titleRuns = flex.getOrNull(0)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
        val title = titleRuns?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }?.trim()?.ifEmpty { null }

        // Artist from col[1] — split by " • ", take first non-metadata part
        val subRuns = flex.getOrNull(1)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
        val subText = subRuns?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" } ?: ""
        val artist = parseArtistFromSubtitle(subText)

        // Duration from col[1] — last part after " • " that matches M:SS
        val duration = parseDurationFromSubtitle(subText)

        // Thumbnail
        val thumbs = r["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray
        val cover = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

        return SongResult(
            source = "ytmusic",
            id = vid,
            title = title,
            artist = artist,
            duration = duration,
            cover = cover,
            page = "https://music.youtube.com/watch?v=$vid"
        )
    }

    private fun parseArtistFromSubtitle(sub: String): String? {
        if (sub.isEmpty()) return null
        val parts = sub.split("•").map { it.trim() }.filter { it.isNotEmpty() }
        val durationRe = Regex("^(\\d+:)?\\d+:\\d+$")
        val yearRe = Regex("^(19|20)\\d{2}$")
        val viewsRe = Regex("^\\d+.*\\s*(views|plays|listeners)", RegexOption.IGNORE_CASE)
        for (p in parts) {
            if (durationRe.matches(p)) continue
            if (yearRe.matches(p)) continue
            if (viewsRe.containsMatchIn(p)) continue
            return p
        }
        return parts.firstOrNull()
    }

    private fun parseDurationFromSubtitle(sub: String): Int? {
        val parts = sub.split("•").map { it.trim() }.reversed()
        for (p in parts) {
            val hms = Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})$").find(p)
            if (hms != null) {
                val (h, m, s) = hms.destructured
                return h.toInt() * 3600 + m.toInt() * 60 + s.toInt()
            }
            val ms = Regex("^(\\d+):(\\d{1,2})$").find(p)
            if (ms != null) {
                val (m, s) = ms.destructured
                if (s.toInt() < 60) return m.toInt() * 60 + s.toInt()
            }
        }
        return null
    }

    // ── STREAM ────────────────────────────────────────────────────────

    suspend fun stream(videoId: String): List<StreamInfo> = withContext(Dispatchers.IO) {
        val vid = videoId.removePrefix("yt:").trim()
        require(vid.length in 5..100 && Regex("^[a-zA-Z0-9_-]+$").matches(vid)) { "Invalid id: $vid" }

        // Try ANDROID first, then IOS
        val clients = listOf(
            Triple("ANDROID", ANDROID_VERSION, "com.google.android.youtube/$ANDROID_VERSION (Linux; U; Android 14)"),
            Triple("IOS", IOS_VERSION, "com.google.ios.youtube/$IOS_VERSION (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)")
        )

        var lastError: String? = null
        for ((clientName, clientVersion, ua) in clients) {
            try {
                val sdk = if (clientName == "ANDROID") ",\"androidSdkVersion\":\"$SDK_VERSION\"" else ""
                val body = """{"context":{"client":{"clientName":"$clientName","clientVersion":"$clientVersion"$sdk,"hl":"en","gl":"US"}},"videoId":"$vid","racyCheckOk":true,"contentCheckOk":true}"""
                val raw = post(PLAYER_URL, body, ua)
                val root = json.parseToJsonElement(raw).jsonObject

                // Check playability
                val status = root["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
                if (status != "OK") {
                    val reason = root["playabilityStatus"]?.jsonObject?.get("reason")?.jsonPrimitive?.content ?: status
                    lastError = "$clientName: $status - $reason"
                    continue
                }

                val sd = root["streamingData"]?.jsonObject ?: run {
                    lastError = "$clientName: no streamingData"
                    continue
                }

                val adaptive = sd["adaptiveFormats"]?.jsonArray ?: JsonArray.Empty
                val formats = sd["formats"]?.jsonArray ?: JsonArray.Empty
                val allFormats = mutableListOf<JsonObject>()
                formats.forEach { it.jsonObject?.let { o -> allFormats.add(o) } }
                adaptive.forEach { it.jsonObject?.let { o -> allFormats.add(o) } }

                val audios = allFormats.filter { f ->
                    f["mimeType"]?.jsonPrimitive?.content?.contains("audio", true) == true
                }

                val mapped = audios.mapNotNull { f ->
                    val url = f["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    if (!url.startsWith("http")) return@mapNotNull null
                    if (f["signatureCipher"] != null) return@mapNotNull null
                    val bitrate = f["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                    val quality = if (bitrate > 0) "${bitrate / 1000}kbps" else "audio"
                    val type = f["mimeType"]?.jsonPrimitive?.content?.split(";")?.firstOrNull()?.trim() ?: "audio"
                    StreamInfo(quality, url, type)
                }.sortedWith(
                    compareByDescending<StreamInfo> { if (it.type.contains("mp4")) 1 else 0 }
                        .thenByDescending { it.quality.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
                )

                if (mapped.isNotEmpty()) return@withContext mapped
                lastError = "$clientName: no playable audio URLs"
            } catch (e: Exception) {
                lastError = "$clientName: ${e.message}"
            }
        }

        throw FetchException("ytmusic stream: all clients failed for $vid: $lastError")
    }
}
