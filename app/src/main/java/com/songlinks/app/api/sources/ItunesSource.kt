package com.songlinks.app.api.sources

import android.util.Log
import com.google.gson.JsonParser
import com.songlinks.app.api.SongResult
import com.songlinks.app.api.Stream
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "ItunesSource"
private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"

object ItunesSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 10): List<SongResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$ITUNES_SEARCH_URL?term=$encodedQuery&media=music&entity=song&limit=$limit&country=US"
        Log.d(TAG, "search() URL: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "search() failed", e)
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            Log.e(TAG, "search() HTTP ${response.code}")
                            if (continuation.isActive) continuation.resume(emptyList())
                            return
                        }

                        val body = response.body?.string() ?: ""
                        val json = JsonParser.parseString(body).asJsonObject
                        val results = json.getAsJsonArray("results") ?: run {
                            if (continuation.isActive) continuation.resume(emptyList())
                            return
                        }

                        val songs = mutableListOf<SongResult>()
                        for (element in results) {
                            val obj = element.asJsonObject
                            val kind = obj.get("kind")?.asString ?: ""
                            val wrapperType = obj.get("wrapperType")?.asString ?: ""
                            if (kind != "song" && wrapperType != "track") continue

                            val trackId = obj.get("trackId")?.asLong ?: continue
                            val trackName = obj.get("trackName")?.asString ?: continue
                            val artistName = obj.get("artistName")?.asString ?: ""
                            val collectionName = obj.get("collectionName")?.asString ?: ""
                            val durationMs = obj.get("trackTimeMillis")?.asInt ?: 0
                            val artworkUrl = (obj.get("artworkUrl100")?.asString ?: "").replace("100x100bb", "300x300bb")
                            val previewUrl = obj.get("previewUrl")?.asString ?: ""
                            val pageUrl = obj.get("trackViewUrl")?.asString ?: ""

                            songs.add(
                                SongResult(
                                    source = "itunes",
                                    id = "itunes_$trackId",
                                    title = trackName,
                                    artist = artistName,
                                    album = collectionName,
                                    duration = durationMs,
                                    cover = artworkUrl,
                                    page = pageUrl,
                                    streams = if (previewUrl.isNotBlank()) listOf(Stream(quality = "AAC", url = previewUrl)) else emptyList(),
                                    quality = if (previewUrl.isNotBlank()) "AAC" else "",
                                    streamUrl = previewUrl
                                )
                            )
                        }

                        Log.d(TAG, "search() found ${songs.size} songs")
                        if (continuation.isActive) continuation.resume(songs)
                    } catch (e: Exception) {
                        Log.e(TAG, "search() parse error", e)
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                }
            })
        }
    }
}
