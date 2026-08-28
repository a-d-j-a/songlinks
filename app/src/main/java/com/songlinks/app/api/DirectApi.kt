package com.songlinks.app.api

import android.util.Log
import com.songlinks.app.api.sources.ItunesSource
import com.songlinks.app.api.sources.JiosaavnSource
import com.songlinks.app.api.sources.YtmusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "DirectApi"

object DirectApi {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, sources: Set<String>, limit: Int = 10): List<SongResult> {
        Log.d(TAG, "search() query=$query, sources=$sources")
        return coroutineScope {
            val jobs = sources.map { source ->
                async(Dispatchers.IO) {
                    try {
                        when (source) {
                            "itunes" -> ItunesSource.search(query, limit)
                            "jiosaavn" -> JiosaavnSource.search(query, limit)
                            "ytmusic" -> YtmusicSource.search(query, limit)
                            else -> emptyList()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Source $source failed", e)
                        emptyList()
                    }
                }
            }
            val results = jobs.awaitAll().flatten()
            val deduped = results.distinctBy { "${it.source}:${it.id.lowercase()}" }
            Log.d(TAG, "search() total=${deduped.size} results")
            deduped
        }
    }

    suspend fun resolveStreamUrl(songId: String, title: String = "", artist: String = ""): String {
        Log.d(TAG, "resolveStreamUrl() id=$songId title=$title artist=$artist")
        return withContext(Dispatchers.IO) {
            when {
                songId.startsWith("ytmusic_") -> {
                    val videoId = songId.removePrefix("ytmusic_")
                    val url = YtmusicSource.getStreamUrl(videoId)
                    if (url.isNotBlank()) url
                    else {
                        // Cipher might have failed, try searching YouTube Music by title+artist
                        if (title.isNotBlank()) {
                            Log.d(TAG, "resolveStreamUrl() YT direct failed, searching by title")
                            val fallback = searchYouTubeForStream(title, artist)
                            fallback
                        } else ""
                    }
                }
                songId.startsWith("jiosaavn_") -> {
                    val id = songId.removePrefix("jiosaavn_")
                    val url = JiosaavnSource.getStreamUrl(id)
                    if (url.isNotBlank()) url
                    else if (title.isNotBlank()) {
                        Log.d(TAG, "resolveStreamUrl() JioSaavn direct failed, searching YouTube")
                        searchYouTubeForStream(title, artist)
                    } else ""
                }
                songId.startsWith("itunes_") -> {
                    // iTunes only provides 30s previews; resolve via YouTube Music for full playback
                    if (title.isNotBlank()) {
                        Log.d(TAG, "resolveStreamUrl() iTunes source, resolving via YouTube for full playback")
                        searchYouTubeForStream(title, artist)
                    } else ""
                }
                else -> {
                    try { JiosaavnSource.getStreamUrl(songId) } catch (_: Exception) { "" }
                }
            }
        }
    }

    private suspend fun searchYouTubeForStream(title: String, artist: String): String {
        return try {
            val query = "$title $artist".trim()
            val results = YtmusicSource.search(query, 5)
            for (result in results) {
                val videoId = result.id.removePrefix("ytmusic_")
                if (videoId.isBlank()) continue
                val url = YtmusicSource.getStreamUrl(videoId)
                if (url.isNotBlank()) {
                    Log.d(TAG, "searchYouTubeForStream() resolved: ${url.take(80)}")
                    return@withContext url
                }
            }
            Log.w(TAG, "searchYouTubeForStream() no playable stream found for: $query")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "searchYouTubeForStream() error", e)
            ""
        }
    }

    suspend fun getLyrics(title: String, artist: String): LyricsResponse {
        Log.d(TAG, "getLyrics() title=$title, artist=$artist")
        return withContext(Dispatchers.IO) {
            try {
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                val url = "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val success = response.isSuccessful
                response.close()

                if (success && body.isNotBlank()) {
                    val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                    LyricsResponse(
                        title = json.get("trackName")?.asString ?: title,
                        artist = json.get("artistName")?.asString ?: artist,
                        lyrics = json.get("plainLyrics")?.asString ?: "",
                        syncedLyrics = json.get("syncedLyrics")?.asString
                    )
                } else {
                    LyricsResponse(title = title, artist = artist)
                }
            } catch (e: Exception) {
                Log.e(TAG, "getLyrics() error", e)
                LyricsResponse(title = title, artist = artist)
            }
        }
    }

    suspend fun checkHealth(): Boolean {
        return true
    }
}
