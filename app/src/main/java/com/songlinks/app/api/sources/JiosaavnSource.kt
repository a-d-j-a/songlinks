package com.songlinks.app.api.sources

import android.util.Log
import com.google.gson.JsonParser
import com.songlinks.app.api.SongResult
import com.songlinks.app.api.Stream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

private const val TAG = "JiosaavnSource"
private const val JIOSAAVN_API = "https://www.jiosaavn.com/api.php"
private const val DES_KEY = "38346591"

object JiosaavnSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun decryptDesEcb(encryptedBase64: String): String {
        return try {
            val keyBytes = DES_KEY.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val encryptedBytes = try {
                android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT)
            } catch (_: Exception) {
                java.util.Base64.getDecoder().decode(encryptedBase64)
            }
            val decrypted = String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
            // If decryption produces something that doesn't look like a URL,
            // the API may have sent a plain URL already — return null to trigger fallback
            return if (decrypted.isNotBlank() && decrypted.startsWith("http")) decrypted else ""
        } catch (e: Exception) {
            Log.e(TAG, "DES decrypt failed", e)
            ""
        }
    }

    suspend fun search(query: String, limit: Int = 10): List<SongResult> {
        // Primary: search.getResults gives full encrypted_media_url (320kbps), autocomplete only gives preview vlink
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val primaryUrl = "$JIOSAAVN_API?__call=search.getResults&cc=in&_format=json&_marker=0&api_version=4&includeMetaTags=1&q=$encodedQuery&p=1&n=$limit"
        val fallbackUrl = "$JIOSAAVN_API?__call=autocomplete.get&cc=in&includeMetaTags=1&query=$encodedQuery"
        Log.d(TAG, "search() primary URL: $primaryUrl")
        val primaryResult = searchWithUrl(primaryUrl, limit)
        if (primaryResult.isNotEmpty() && primaryResult.any { it.streamUrl.isNotBlank() && it.quality != "preview" }) {
            return primaryResult
        }
        Log.d(TAG, "search() primary gave ${primaryResult.size} with no full stream, trying autocomplete fallback")
        val fallback = searchWithUrl(fallbackUrl, limit)
        return if (fallback.isNotEmpty()) fallback else primaryResult
    }

    private suspend fun searchWithUrl(url: String, limit: Int): List<SongResult> {
        Log.d(TAG, "searchWithUrl() URL: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
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
                        val body = response.body?.string() ?: ""
                        val code = response.code
                        response.close()
                        if (!response.isSuccessful) {
                            Log.e(TAG, "search() HTTP $code")
                            if (continuation.isActive) continuation.resume(emptyList())
                            return
                        }
                        val json = JsonParser.parseString(body).asJsonObject

                        val songsData = json.getAsJsonObject("songs")?.getAsJsonArray("data")
                            ?: json.getAsJsonArray("results")

                        val songs = mutableListOf<SongResult>()
                        if (songsData != null) {
                            for (element in songsData) {
                                val obj = element.asJsonObject
                                val songId = obj.get("id")?.asString ?: obj.get("songid")?.asString ?: continue
                                val title = obj.get("song")?.asString ?: obj.get("title")?.asString ?: continue
                                val moreInfo = obj.getAsJsonObject("more_info")
                                val artist = obj.get("singers")?.asString ?: moreInfo?.get("singers")?.asString ?: obj.get("music")?.asString ?: moreInfo?.get("primary_artists")?.asString ?: ""
                                val album = obj.get("album")?.asString ?: ""
                                val duration = obj.get("duration")?.asString?.toIntOrNull() ?: moreInfo?.get("duration")?.asString?.toIntOrNull() ?: 0
                                val permaUrl = obj.get("perma_url")?.asString ?: obj.get("url")?.asString ?: ""

                                val coverRaw = obj.get("image")?.asString ?: ""
                                var coverUrl = ""
                                if (coverRaw.startsWith("http")) {
                                    coverUrl = coverRaw
                                } else if (coverRaw.isNotBlank()) {
                                    try {
                                        val coverJson = JsonParser.parseString(coverRaw).asJsonArray
                                        if (coverJson.size() > 0) {
                                            coverUrl = coverJson[coverJson.size() - 1].asJsonObject.get("link")?.asString ?: ""
                                        }
                                    } catch (_: Exception) {
                                        coverUrl = coverRaw
                                    }
                                }

                                val encryptedUrl = obj.get("encrypted_media_url")?.asString
                                    ?: moreInfo?.get("encrypted_media_url")?.asString ?: ""
                            var streamUrl = ""
                            var quality = ""
                            if (encryptedUrl.isNotBlank()) {
                                // Try DES-ECB decryption first
                                val decrypted = decryptDesEcb(encryptedUrl)
                                if (decrypted.isNotBlank() && decrypted.startsWith("http")) {
                                    streamUrl = decrypted
                                    quality = "320kbps"
                                } else if (encryptedUrl.startsWith("http")) {
                                    // API may have sent plain URL; use directly
                                    streamUrl = encryptedUrl
                                    quality = "320kbps"
                                }
                            }
                            // Fallback to preview vlink (30s preview) for autocomplete results which lack encrypted_media_url
                            if (streamUrl.isBlank()) {
                                val vlink = moreInfo?.get("vlink")?.asString ?: ""
                                if (vlink.isNotBlank() && vlink.startsWith("http")) {
                                    streamUrl = vlink
                                    quality = "preview"
                                }
                            }

                                songs.add(
                                    SongResult(
                                        source = "jiosaavn",
                                        id = "jiosaavn_$songId",
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        duration = duration,
                                        cover = coverUrl,
                                        page = permaUrl,
                                        streams = if (streamUrl.isNotBlank()) listOf(Stream(quality = quality, url = streamUrl)) else emptyList(),
                                        quality = quality,
                                        streamUrl = streamUrl
                                    )
                                )
                            }
                        }

                        Log.d(TAG, "search() found ${songs.size} songs")
                        if (continuation.isActive) continuation.resume(songs)
                    } catch (e: Exception) {
                        Log.e(TAG, "search() parse error", e)
                        try { response.close() } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                }
            })
        }
    }

    suspend fun getStreamUrl(songId: String): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val encodedId = URLEncoder.encode(songId, "UTF-8")
            // Use song.get which returns encrypted_media_url directly
            val url = "$JIOSAAVN_API?__call=song.get&cc=in&includeMediaTags=1&songId=$encodedId&_format=json&_marker=0"
            Log.d(TAG, "getStreamUrl() URL: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            Log.d(TAG, "getStreamUrl() HTTP ${response.code}, body length=${body.length}")

            if (!response.isSuccessful) {
                Log.e(TAG, "getStreamUrl() HTTP ${response.code}")
                return@withContext ""
            }

            val json = try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
            if (json == null) {
                Log.e(TAG, "getStreamUrl() failed to parse JSON")
                return@withContext ""
            }

            // song.get can return the song object directly at the top level
            var encryptedUrl = json.get("encrypted_media_url")?.asString ?: ""

            // Try nested under songId key
            if (encryptedUrl.isBlank()) {
                encryptedUrl = json.getAsJsonObject(songId)?.get("encrypted_media_url")?.asString ?: ""
            }

            // Try inside "songs" array
            if (encryptedUrl.isBlank()) {
                json.getAsJsonArray("songs")?.forEach { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("songid")?.asString ?: ""
                    if (id == songId || id.isBlank()) {
                        val u = obj.get("encrypted_media_url")?.asString
                        if (!u.isNullOrBlank()) encryptedUrl = u
                    }
                }
            }

            // Try more_info
            if (encryptedUrl.isBlank()) {
                json.getAsJsonObject("more_info")?.get("encrypted_media_url")?.asString?.let {
                    if (it.isNotBlank()) encryptedUrl = it
                }
            }

            if (encryptedUrl.isNotBlank()) {
                val decrypted = decryptDesEcb(encryptedUrl)
                if (decrypted.isNotBlank() && decrypted.startsWith("http")) {
                    Log.d(TAG, "getStreamUrl() success for songId=$songId")
                    return@withContext decrypted
                } else if (encryptedUrl.startsWith("http")) {
                    // API may have sent plain URL; use directly
                    Log.d(TAG, "getStreamUrl() using plain URL for songId=$songId")
                    return@withContext encryptedUrl
                }
                Log.w(TAG, "getStreamUrl() decrypted URL invalid: ${decrypted.take(80)}")
            }

            // Fallback: try vlink from more_info (30s preview)
            val vlink = json.getAsJsonObject("more_info")?.get("vlink")?.asString ?: ""
            if (vlink.isNotBlank() && vlink.startsWith("http")) {
                Log.d(TAG, "getStreamUrl() using vlink preview for songId=$songId")
                return@withContext vlink
            }

            // Final fallback: if more_info has an encrypted_media_url that we couldn't decrypt,
            // check if it's already a plain URL
            if (encryptedUrl.isNotBlank() && encryptedUrl.startsWith("http") && encryptedUrl != json.get("encrypted_media_url")?.asString ?: "") {
                Log.d(TAG, "getStreamUrl() using plain encrypted_media_url fallback for songId=$songId")
                return@withContext encryptedUrl
            }

            Log.w(TAG, "getStreamUrl() no stream URL for songId=$songId, encryptedUrl=${encryptedUrl.take(20)}")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "getStreamUrl() error for songId=$songId", e)
            ""
        }
    }
}
