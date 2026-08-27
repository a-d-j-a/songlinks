package com.songlinks.app.api.sources

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.songlinks.app.api.SongResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "YtmusicSource"
private const val YT_INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
private const val YT_INNERTUBE_BASE = "https://music.youtube.com/youtubei/v1"
private const val YT_BASE = "https://music.youtube.com"
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

object YtmusicSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 10): List<SongResult> = withContext(Dispatchers.IO) {
        try {
            var results = searchWithParams(query, limit, "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D")
            if (results.isEmpty()) {
                Log.d(TAG, "search() with song filter returned 0, trying without filter")
                results = searchWithParams(query, limit, "")
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "search() error", e)
            emptyList()
        }
    }

    private fun jsonEscape(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    private fun getRuns(flexColumn: JsonElement?): JsonArray? {
        val obj = flexColumn?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer") ?: return null
        return obj.getAsJsonObject("text")?.getAsJsonArray("runs") ?: obj.getAsJsonArray("runs")
    }

    private fun getPageType(run: JsonElement): String? {
        return run.asJsonObject
            ?.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("browseEndpoint")
            ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
            ?.getAsJsonObject("browseEndpointContextMusicConfig")
            ?.get("pageType")?.asString
    }

    private fun searchWithParams(query: String, limit: Int, params: String): List<SongResult> {
        val safeQuery = jsonEscape(query)
        val paramsBlock = if (params.isNotBlank()) """, "params": "$params"""" else ""
        val payload = """{
            "context": {
                "client": {
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20231030.00.00",
                    "hl": "en",
                    "gl": "US"
                }
            },
            "query": "$safeQuery"$paramsBlock
        }"""

        val request = Request.Builder()
            .url("$YT_INNERTUBE_BASE/search?key=$YT_INNERTUBE_KEY")
            .header("User-Agent", MOBILE_UA)
            .header("Content-Type", "application/json")
            .header("Origin", YT_BASE)
            .header("Referer", "$YT_BASE/")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()

        Log.d(TAG, "searchWithParams() HTTP ${response.code}, body ${body.length} chars, params='${params.take(20)}...'")

        if (!response.isSuccessful) {
            Log.e(TAG, "searchWithParams() HTTP ${response.code}")
            return emptyList()
        }

        val json = JsonParser.parseString(body).asJsonObject
        val allItems = collectMusicItems(json)
        Log.d(TAG, "searchWithParams() found ${allItems.size} musicResponsiveListItemRenderer items")

        val songs = mutableListOf<SongResult>()
        for (item in allItems) {
            val columns = item.getAsJsonArray("flexColumns") ?: continue
            if (columns.size() == 0) continue

            // Title + videoId from first column (handles both text.runs and runs)
            val runs0 = getRuns(columns[0])
            val title = runs0?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
            if (title.isBlank()) continue

            val videoId = runs0?.mapNotNull { it.asJsonObject }
                ?.firstOrNull { it.getAsJsonObject("navigationEndpoint")?.has("watchEndpoint") == true }
                ?.getAsJsonObject("navigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")
                ?.get("videoId")?.asString ?: ""

            if (videoId.isBlank()) continue

            // Second column contains artists, album, duration interleaved with " • " separators in text.runs
            // Parse robustly: artist = pageType ARTIST, album = pageType ALBUM, duration = \d+:\d+ pattern
            var artist = ""
            var album = ""
            var durationSec = 0

            if (columns.size() > 1) {
                val runs1 = getRuns(columns[1])
                if (runs1 != null) {
                    val artistParts = mutableListOf<String>()
                    val albumParts = mutableListOf<String>()
                    var durationText = ""
                    for (elem in runs1) {
                        val obj = elem.asJsonObject
                        val text = obj.get("text")?.asString ?: ""
                        if (text == " • " || text == " & " || text.isBlank()) continue
                        val pageType = getPageType(elem)
                        when (pageType) {
                            "MUSIC_PAGE_TYPE_ARTIST" -> artistParts.add(text)
                            "MUSIC_PAGE_TYPE_ALBUM" -> albumParts.add(text)
                            else -> {
                                // Check if it's duration (e.g., "6:56" or "3:12")
                                if (text.matches(Regex("\\d+:\\d+(:\\d+)?"))) {
                                    durationText = text
                                } else if (artistParts.isEmpty() && albumParts.isEmpty()) {
                                    // Fallback: if no pageType, first non-duration is likely artist (handles older layout where column split)
                                    // But for new layout, runs without pageType that aren't duration are separators, so skip
                                }
                            }
                        }
                    }
                    // Fallback for older layout where artist was separate column without pageType handling
                    if (artistParts.isEmpty()) {
                        // Try to get artist as all non-album, non-duration text joined
                        val fallbackArtist = runs1
                            .filter { elem ->
                                val t = elem.asJsonObject.get("text")?.asString ?: ""
                                t != " • " && !t.matches(Regex("\\d+:\\d+.*")) && getPageType(elem) != "MUSIC_PAGE_TYPE_ALBUM"
                            }
                            .joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
                            .split(" • ")[0].trim()
                        if (fallbackArtist.isNotEmpty() && !fallbackArtist.contains(":") ) {
                            artist = fallbackArtist
                        } else {
                            artist = artistParts.joinToString(", ")
                        }
                    } else {
                        artist = artistParts.joinToString(", ")
                    }
                    album = albumParts.joinToString("")
                    if (durationText.isNotBlank()) durationSec = parseDuration(durationText)
                    // If still no duration, try last column
                    if (durationSec == 0 && columns.size() > 2) {
                        val lastRuns = getRuns(columns[columns.size() - 1])
                        val lastText = lastRuns?.lastOrNull()?.asJsonObject?.get("text")?.asString ?: ""
                        if (lastText.matches(Regex("\\d+:\\d+.*"))) durationSec = parseDuration(lastText)
                        // Also if duration still 0, maybe duration is in column 1's last run
                        if (durationSec == 0 && durationText.isBlank()) {
                            val maybeDur = lastRuns?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
                            if (maybeDur.contains(":")) durationSec = parseDuration(maybeDur.split(" • ").last().trim())
                        }
                    }
                    // Final fallback: if we have durationText from earlier, use it
                    if (durationSec == 0 && durationText.isNotBlank()) durationSec = parseDuration(durationText)
                }
            }

            // Fallback for older API where album in column 2
            if (album.isBlank() && columns.size() > 2) {
                val albumRuns = getRuns(columns[2])
                if (albumRuns != null) {
                    val a = albumRuns.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
                    if (a.isNotBlank() && !a.matches(Regex("\\d+:\\d+.*"))) album = a
                }
            }

            // Duration fallback if still 0 and we have 3 columns
            if (durationSec == 0) {
                val durRuns = getRuns(columns[columns.size() - 1])
                val durText = durRuns?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
                // Extract last "M:SS" pattern
                val match = Regex("""(\d+:\d+(?::\d+)?)""").find(durText)
                if (match != null) durationSec = parseDuration(match.value)
            }

            val thumbnails = item.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
            var coverUrl = ""
            if (thumbnails != null && thumbnails.size() > 0) {
                coverUrl = thumbnails[thumbnails.size() - 1].asJsonObject.get("url")?.asString ?: ""
                if (coverUrl.isNotBlank() && !coverUrl.startsWith("http")) {
                    coverUrl = "https:$coverUrl"
                }
            }

            songs.add(
                SongResult(
                    source = "ytmusic",
                    id = "ytmusic_$videoId",
                    title = title,
                    artist = artist,
                    album = album,
                    duration = durationSec * 1000,
                    cover = coverUrl,
                    page = "$YT_BASE/watch?v=$videoId",
                    streams = emptyList(),
                    quality = "AAC",
                    streamUrl = ""
                )
            )
        }

        val result = songs.take(limit)
        Log.d(TAG, "searchWithParams() returning ${result.size} songs")
        return result
    }

    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "getStreamUrl() for videoId=$videoId")
        val clients = listOf(
            Triple("ANDROID_MUSIC", "5.16.51", "com.google.android.apps.youtube.music/5.16.51 (Linux; U; Android 13) gzip"),
            Triple("ANDROID", "19.09.37", "com.google.android.youtube/19.09.37 (Linux; U; Android 13) gzip"),
            Triple("WEB_REMIX", "1.20231030.00.00", MOBILE_UA)
        )

        val safeVideoId = jsonEscape(videoId)
        for ((clientName, clientVersion, userAgent) in clients) {
            try {
                val sdk = if (clientName.startsWith("ANDROID")) 30 else 0
                val payload = buildString {
                    append("""{"context":{"client":{"clientName":"$clientName","clientVersion":"$clientVersion","hl":"en","gl":"US"""")
                    if (sdk > 0) append(""","androidSdkVersion":$sdk""")
                    append("""}},"videoId":"$safeVideoId","contentCheckOk":true,"racyCheckOk":true}""")
                }

                val request = Request.Builder()
                    .url("$YT_INNERTUBE_BASE/player?key=$YT_INNERTUBE_KEY")
                    .header("User-Agent", userAgent)
                    .header("Content-Type", "application/json")
                    .header("Origin", YT_BASE)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                response.close()

                if (!response.isSuccessful) {
                    Log.d(TAG, "getStreamUrl() $clientName: HTTP ${response.code}")
                    continue
                }

                val json = JsonParser.parseString(body).asJsonObject
                val streamingData = json.getAsJsonObject("streamingData")
                if (streamingData == null) {
                    Log.d(TAG, "getStreamUrl() $clientName: no streamingData")
                    continue
                }

                val formats = mutableListOf<com.google.gson.JsonObject>()
                streamingData.getAsJsonArray("adaptiveFormats")?.let { formats.addAll(it.map { it.asJsonObject }) }
                streamingData.getAsJsonArray("formats")?.let { formats.addAll(it.map { it.asJsonObject }) }

                val audioFormat = formats
                    .filter { it.get("mimeType")?.asString?.startsWith("audio/") == true }
                    .maxByOrNull { it.get("bitrate")?.asInt ?: 0 }

                val url = audioFormat?.get("url")?.asString
                if (!url.isNullOrBlank()) {
                    Log.d(TAG, "getStreamUrl() success with $clientName for videoId=$videoId")
                    return@withContext url
                }
                Log.d(TAG, "getStreamUrl() $clientName: no direct URL (cipher required)")
            } catch (e: Exception) {
                Log.e(TAG, "getStreamUrl() $clientName failed", e)
            }
        }

        Log.w(TAG, "getStreamUrl() all clients failed for videoId=$videoId")
        ""
    }

    private fun collectMusicItems(obj: com.google.gson.JsonObject): List<com.google.gson.JsonObject> {
        val result = mutableListOf<com.google.gson.JsonObject>()
        for (key in obj.keySet()) {
            val element = obj.get(key) ?: continue
            when {
                key == "musicResponsiveListItemRenderer" -> {
                    result.add(element.asJsonObject)
                }
                element.isJsonObject -> {
                    result.addAll(collectMusicItems(element.asJsonObject))
                }
                element.isJsonArray -> {
                    for (item in element.asJsonArray) {
                        if (item.isJsonObject) {
                            result.addAll(collectMusicItems(item.asJsonObject))
                        }
                    }
                }
            }
        }
        return result
    }

    private fun parseDuration(text: String): Int {
        val clean = text.trim().split(" • ").last().trim()
        val parts = clean.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }
}
