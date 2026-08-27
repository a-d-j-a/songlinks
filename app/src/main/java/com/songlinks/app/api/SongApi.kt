package com.songlinks.app.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SongApi(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun getBaseUrl(): String {
        val prefs = context.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
        return prefs.getString("server_url", "http://10.0.2.2:8080")
            ?: "http://10.0.2.2:8080"
    }

    suspend fun search(query: String, sources: Set<String>): List<SongResult> {
        val baseUrl = getBaseUrl().trimEnd('/')
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val sourcesParam = sources.joinToString(",")
        val url = "$baseUrl/api/search?q=$encodedQuery&sources=$sourcesParam"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            throw IOException("Server error: ${response.code}")
                        }

                        val body = response.body?.string() ?: throw IOException("Empty response")
                        val json = JsonParser.parseString(body).asJsonObject
                        val resultsArray = json.getAsJsonArray("results")

                        val results = mutableListOf<SongResult>()
                        if (resultsArray != null) {
                            for (element in resultsArray) {
                                val obj = element.asJsonObject
                                val streamsArray = obj.getAsJsonArray("streams")
                                val streams = mutableListOf<Stream>()
                                if (streamsArray != null) {
                                    for (streamEl in streamsArray) {
                                        val streamObj = streamEl.asJsonObject
                                        streams.add(
                                            Stream(
                                                quality = streamObj.get("quality")?.asString ?: "",
                                                url = streamObj.get("url")?.asString ?: "",
                                                type = streamObj.get("type")?.asString
                                            )
                                        )
                                    }
                                }
                                results.add(
                                    SongResult(
                                        source = obj.get("source")?.asString ?: "",
                                        id = obj.get("id")?.asString ?: "",
                                        title = obj.get("title")?.asString ?: "",
                                        artist = obj.get("artist")?.asString ?: "",
                                        album = obj.get("album")?.asString,
                                        duration = obj.get("duration")?.asInt,
                                        cover = obj.get("cover")?.asString
                                            ?: obj.get("albumArtUrl")?.asString,
                                        page = obj.get("page")?.asString,
                                        streams = streams,
                                        language = obj.get("language")?.asString,
                                        playCount = obj.get("playCount")?.asLong,
                                        release = obj.get("release")?.asString,
                                        genre = obj.get("genre")?.asString,
                                        quality = streams.firstOrNull()?.quality ?: "",
                                        streamUrl = streams.firstOrNull()?.url ?: ""
                                    )
                                )
                            }
                        }

                        if (continuation.isActive) {
                            continuation.resume(results)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }

    suspend fun checkHealth(): Boolean {
        val baseUrl = getBaseUrl().trimEnd('/')
        val url = "$baseUrl/health"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    if (continuation.isActive) {
                        continuation.resume(response.isSuccessful)
                    }
                }
            })
        }
    }
}
