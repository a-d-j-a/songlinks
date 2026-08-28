package com.songlinks.app.api.sources

import android.util.Log
import com.google.gson.JsonParser
import com.songlinks.app.api.SongResult
import com.songlinks.app.api.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "KugouSource"
object KugouSource {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    suspend fun search(query: String, limit: Int = 5): List<SongResult> = withContext(Dispatchers.IO) {
        try {
            val enc = URLEncoder.encode(query, "UTF-8")
            val url = "https://complexsearch.kugou.com/v2/search/song?callback=&keyword=$enc&page=1&pagesize=$limit&platform=WebFilter"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").header("Referer", "https://www.kugou.com/").get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""; resp.close()
            if (!resp.isSuccessful) { Log.e(TAG, "search HTTP ${resp.code}"); return@withContext emptyList() }
            val jsonText = body.substringAfter("callback(").substringBeforeLast(")")
            val json = try { JsonParser.parseString(if (jsonText.isBlank()) body else jsonText).asJsonObject } catch (_:Exception) { return@withContext emptyList() }
            val data = json.getAsJsonObject("data")?.getAsJsonArray("lists") ?: return@withContext emptyList()
            val out = mutableListOf<SongResult>()
            for (el in data) {
                val o = el.asJsonObject
                val id = o.get("ID")?.asString ?: continue
                val title = o.get("SongName")?.asString ?: continue
                val artist = o.get("SingerName")?.asString ?: ""
                val album = o.get("AlbumName")?.asString ?: ""
                val dur = o.get("Duration")?.asInt ?: 0
                val img = o.get("Image")?.asString?.replace("{size}", "300") ?: ""
                out.add(SongResult(source="kugou", id="kugou_$id", title=title, artist=artist, album=album, duration=dur*1000, cover=img, page="https://www.kugou.com/song/#hash=$id", streams=emptyList(), quality="Kugou", streamUrl=""))
            }
            Log.d(TAG, "search found ${out.size}")
            out
        } catch (e: Exception) { Log.e(TAG, "search error", e); emptyList() }
    }
    // Kugou lyrics via lrclib fallback already covers it, keep for future stream via hash
}
