package com.songlinks.app.api.sources

import android.util.Log
import com.google.gson.JsonParser
import com.songlinks.app.api.SongResult
import com.songlinks.app.api.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "YtmusicSource"
private const val YT_INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
private const val YT_INNERTUBE_BASE = "https://music.youtube.com/youtubei/v1"
private const val YT_BASE = "https://music.youtube.com"
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
private const val ANDROID_UA = "com.google.android.youtube/19.09.37 (Linux; U; Android 13) gzip"

object YtmusicSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 10): List<SongResult> = withContext(Dispatchers.IO) {
        try {
            val payload = """{
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20231030.00.00",
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "query": "$query",
                "params": "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D"
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

            if (!response.isSuccessful) {
                Log.e(TAG, "search() HTTP ${response.code}")
                return@withContext emptyList()
            }

            val json = JsonParser.parseString(body).asJsonObject
            val contents = json
                .getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")

            val songs = mutableListOf<SongResult>()
            if (contents != null) {
                for (section in contents) {
                    val shelf = section.asJsonObject?.getAsJsonObject("musicShelfRenderer") ?: continue
                    val shelfContents = shelf.getAsJsonArray("contents") ?: continue
                    for (item in shelfContents) {
                        val flex = item.asJsonObject?.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue
                        val columns = flex.getAsJsonArray("flexColumns") ?: continue

                        // Video ID from first column
                        val videoId = columns[0].asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonArray("runs")
                            ?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("navigationEndpoint")
                            ?.getAsJsonObject("watchEndpoint")
                            ?.get("videoId")?.asString ?: ""

                        if (videoId.isBlank()) continue

                        // Title from first column
                        val title = columns[0].asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonArray("runs")
                            ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""

                        if (title.isBlank()) continue

                        // Artist from second column
                        val artist = columns.getOrNull(1)?.asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonArray("runs")
                            ?.filter { it.asJsonObject.has("navigationEndpoint") }
                            ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""

                        // Album from third column (if exists)
                        val album = columns.getOrNull(2)?.asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonArray("runs")
                            ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""

                        // Duration from last column
                        val durationText = columns.lastOrNull()?.asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonArray("runs")
                            ?.firstOrNull()?.asJsonObject
                            ?.get("text")?.asString ?: "0:00"
                        val durationSec = parseDuration(durationText)

                        // Thumbnail
                        val thumbnails = flex.getAsJsonObject("thumbnail")
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
                }
            }

            val result = songs.take(limit)
            Log.d(TAG, "search() found ${result.size} songs")
            result
        } catch (e: Exception) {
            Log.e(TAG, "search() error", e)
            emptyList()
        }
    }

    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        try {
            val payload = """{
                "context": {
                    "client": {
                        "clientName": "ANDROID",
                        "clientVersion": "19.09.37",
                        "androidSdkVersion": 30,
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "videoId": "$videoId",
                "contentCheckOk": true,
                "racyCheckOk": true
            }"""

            val request = Request.Builder()
                .url("$YT_INNERTUBE_BASE/player?key=$YT_INNERTUBE_KEY")
                .header("User-Agent", ANDROID_UA)
                .header("Content-Type", "application/json")
                .header("Origin", YT_BASE)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                Log.e(TAG, "getStreamUrl() HTTP ${response.code}")
                return@withContext ""
            }

            val json = JsonParser.parseString(body).asJsonObject
            val streamingData = json.getAsJsonObject("streamingData") ?: return@withContext ""

            val formats = mutableListOf<com.google.gson.JsonObject>()
            streamingData.getAsJsonArray("adaptiveFormats")?.let { formats.addAll(it.map { it.asJsonObject }) }
            streamingData.getAsJsonArray("formats")?.let { formats.addAll(it.map { it.asJsonObject }) }

            val audioFormat = formats
                .filter { it.get("mimeType")?.asString?.startsWith("audio/") == true }
                .maxByOrNull { it.get("bitrate")?.asInt ?: 0 }

            val url = audioFormat?.get("url")?.asString
            if (url.isNullOrBlank()) {
                Log.w(TAG, "getStreamUrl() no direct URL found (may need cipher)")
                return@withContext ""
            }

            Log.d(TAG, "getStreamUrl() success for videoId=$videoId")
            url
        } catch (e: Exception) {
            Log.e(TAG, "getStreamUrl() error for videoId=$videoId", e)
            ""
        }
    }

    private fun parseDuration(text: String): Int {
        val parts = text.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }
}
