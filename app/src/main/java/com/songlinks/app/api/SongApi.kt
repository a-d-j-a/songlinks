package com.songlinks.app.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        return prefs.getString("server_url", "http://10.0.2.2:3000")
            ?: "http://10.0.2.2:3000"
    }

    suspend fun search(query: String, sources: Set<String>): List<SongResult> {
        val baseUrl = getBaseUrl().trimEnd('/')
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val sourcesParam = sources.joinToString(",")
        val url = "$baseUrl/search?q=$encodedQuery&sources=$sourcesParam"

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

    suspend fun getRecommendations(history: List<Pair<String, String>>): List<SongResult> {
        val baseUrl = getBaseUrl().trimEnd('/')
        val url = "$baseUrl/recommendations"

        val historyJson = history.map { (title, artist) ->
            mapOf("title" to title, "artist" to artist)
        }
        val requestBody = gson.toJson(mapOf("history" to historyJson))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
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

    suspend fun getLyrics(title: String, artist: String): LyricsResponse {
        val baseUrl = getBaseUrl().trimEnd('/')
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        val url = "$baseUrl/lyrics?title=$encodedTitle&artist=$encodedArtist"

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

                        val lyricsResponse = LyricsResponse(
                            title = json.get("title")?.asString ?: "",
                            artist = json.get("artist")?.asString ?: "",
                            lyrics = json.get("lyrics")?.asString ?: "",
                            syncedLyrics = json.get("syncedLyrics")?.asString
                        )

                        if (continuation.isActive) {
                            continuation.resume(lyricsResponse)
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

    suspend fun getBackupData(): BackupData {
        val baseUrl = getBaseUrl().trimEnd('/')
        val url = "$baseUrl/backup"

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
                        continuation.resume(BackupData())
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            if (continuation.isActive) {
                                continuation.resume(BackupData())
                            }
                            return
                        }

                        val body = response.body?.string() ?: throw IOException("Empty response")
                        val backupData = gson.fromJson(body, BackupData::class.java)

                        if (continuation.isActive) {
                            continuation.resume(backupData)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resume(BackupData())
                        }
                    }
                }
            })
        }
    }

    suspend fun restoreBackup(data: BackupData): Boolean {
        val baseUrl = getBaseUrl().trimEnd('/')
        val url = "$baseUrl/restore"

        val requestBody = gson.toJson(data)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
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
