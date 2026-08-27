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
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "DES decrypt failed", e)
            ""
        }
    }

    suspend fun search(query: String, limit: Int = 10): List<SongResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$JIOSAAVN_API?__call=autocomplete.get&cc=in&includeMetaTags=1&query=$encodedQuery"
        Log.d(TAG, "search() URL: $url")

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
                                val artist = obj.get("singers")?.asString ?: obj.get("music")?.asString ?: ""
                                val album = obj.get("album")?.asString ?: ""
                                val duration = obj.get("duration")?.asString?.toIntOrNull() ?: 0
                                val permaUrl = obj.get("perma_url")?.asString ?: ""

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

                                val encryptedUrl = obj.get("encrypted_media_url")?.asString ?: ""
                                var streamUrl = ""
                                var quality = ""
                                if (encryptedUrl.isNotBlank()) {
                                    val decrypted = decryptDesEcb(encryptedUrl)
                                    if (decrypted.isNotBlank() && decrypted.startsWith("http")) {
                                        streamUrl = decrypted
                                        quality = "320kbps"
                                    }
                                }

                                songs.add(
                                    SongResult(
                                        source = "jiosaavn",
                                        id = "jiosaavn_$songId",
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        duration = duration * 1000,
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
            val url = "$JIOSAAVN_API?__call=song.get&cc=in&includeMediaTags=1&songId=$encodedId"
            Log.d(TAG, "getStreamUrl() URL: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                Log.e(TAG, "getStreamUrl() HTTP ${response.code}")
                return@withContext ""
            }

            val json = try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
            var encryptedUrl = json?.get("encrypted_media_url")?.asString ?: ""
            if (encryptedUrl.isBlank() && json != null) {
                // Try nested: songId -> object, or inside "songs" array
                encryptedUrl = json.getAsJsonObject(songId)?.get("encrypted_media_url")?.asString ?: ""
                if (encryptedUrl.isBlank()) {
                    json.getAsJsonArray("songs")?.forEach { el ->
                        val u = el.asJsonObject.get("encrypted_media_url")?.asString
                        if (!u.isNullOrBlank()) encryptedUrl = u
                    }
                }
            }

            if (encryptedUrl.isNotBlank()) {
                val decrypted = decryptDesEcb(encryptedUrl)
                if (decrypted.isNotBlank() && decrypted.startsWith("http")) {
                    Log.d(TAG, "getStreamUrl() success for songId=$songId")
                    return@withContext decrypted
                }
            }

            Log.w(TAG, "getStreamUrl() no stream URL for songId=$songId")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "getStreamUrl() error for songId=$songId", e)
            ""
        }
    }
}
