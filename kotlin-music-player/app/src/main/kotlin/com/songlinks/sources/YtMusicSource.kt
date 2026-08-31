package com.songlinks.sources

import com.songlinks.Models.*
import com.songlinks.Util
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

// Port of src/sources/ytmusic.js — Innertube WEB_REMIX search + ANDROID 20.10.38 player (1M cap), mp4-prefer
object YtMusicSource : com.songlinks.MusicSource {
    override val name = "ytmusic"
    private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val WEB_VERSION = "1.20241202.01.00"
    private const val ANDROID_VERSION = "20.10.38"
    private val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?key=$API_KEY&prettyPrint=false"
    private val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?key=$API_KEY"

    override suspend fun search(q: String, limit: Int): List<SongResult> {
        val nq = q.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val nLimit = limit.coerceIn(1, 50)
        val body = buildJsonObject {
            putJsonObject("context"){ putJsonObject("client"){ put("clientName","WEB_REMIX"); put("clientVersion",WEB_VERSION); put("hl","en"); put("gl","US") } }
            put("query", nq)
        }
        val res: JsonElement = Util.client.post(SEARCH_URL){ contentType(ContentType.Application.Json); setBody(body) }.body()
        return extract(res).take(nLimit)
    }

    private fun extract(json: JsonElement): List<SongResult> {
        val out = mutableListOf<SongResult>()
        fun traverse(el: JsonElement) {
            if (el is JsonObject) {
                val r = el["musicResponsiveListItemRenderer"] as? JsonObject
                if (r != null) {
                    val vid = r["overlay"]?.jsonObject?.get("musicItemThumbnailOverlayRenderer")?.jsonObject?.get("content")?.jsonObject?.get("musicPlayButtonRenderer")?.jsonObject?.get("playNavigationEndpoint")?.jsonObject?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                        ?: r["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                    if (vid != null && vid.length in 5..100) {
                        val flex = r["flexColumns"]?.jsonArray
                        val title = flex?.getOrNull(0)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject?.get("text")?.jsonObject?.get("runs")?.jsonArray?.joinToString(""){ it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }?.trim()
                        val sub = flex?.getOrNull(1)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject?.get("text")?.jsonObject?.get("runs")?.jsonArray?.joinToString(""){ it.jsonObject["text"]?.jsonPrimitive?.content ?: "" } ?: ""
                        val artist = sub.split("•").getOrNull(1)?.trim() ?: sub.trim().takeIf{ it.isNotEmpty() }
                        val thumb = r["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        // duration from fixedColumns or sub
                        val durStr = r["fixedColumns"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject?.get("text")?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                        val dur = durStr?.let { parseDuration(it) } ?: parseDuration(sub)
                        out.add(SongResult(source="ytmusic", id=vid, title=title, artist=artist, duration=dur, cover=thumb, page="https://music.youtube.com/watch?v=$vid"))
                    }
                }
                el.values.forEach { traverse(it) }
            } else if (el is JsonArray) el.forEach { traverse(it) }
        }
        traverse(json)
        return out.distinctBy { it.id }
    }

    private fun parseDuration(s: String): Int? {
        val parts = s.split("•").map { it.trim() }.reversed()
        for (p in parts) {
            val hms = Regex("""(\d+):(\d+):(\d+)""").find(p)
            if (hms != null) { val (h,m,sec)=hms.destructured; return h.toInt()*3600+m.toInt()*60+sec.toInt() }
            val ms = Regex("""(\d+):(\d+)""").find(p)
            if (ms != null) { val (m,sec)=ms.destructured; if(sec.toInt()<60) return m.toInt()*60+sec.toInt() }
        }
        return null
    }

    // Full ytmusic stream via ANDROID player — returns mp4-preferred, 1M cap handling done server-side via /proxy
    suspend fun stream(videoId: String): List<StreamInfo> {
        val vid = videoId.removePrefix("yt:").trim()
        require(vid.length in 5..100 && Regex("""[a-zA-Z0-9_-]+""").matches(vid)) { "Invalid id" }
        val body = buildJsonObject {
            putJsonObject("context"){ putJsonObject("client"){ put("clientName","ANDROID"); put("clientVersion",ANDROID_VERSION); put("androidSdkVersion","30"); put("hl","en"); put("gl","US") } }
            put("videoId", vid); put("racyCheckOk", true); put("contentCheckOk", true)
        }
        val res: JsonElement = Util.client.post(PLAYER_URL){ contentType(ContentType.Application.Json); setBody(body) }.body()
        val obj = res.jsonObject
        val status = obj["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
        if (status != null && status != "OK") {
            val reason = obj["playabilityStatus"]?.jsonObject?.get("reason")?.jsonPrimitive?.content ?: status
            throw IllegalStateException("ytmusic stream: $status: $reason")
        }
        val sd = obj["streamingData"]?.jsonObject ?: throw IllegalStateException("no streamingData")
        val fmts = (sd["adaptiveFormats"]?.jsonArray.orEmpty() + sd["formats"]?.jsonArray.orEmpty()).mapNotNull { it.jsonObject }
        val audios = fmts.filter { it["mimeType"]?.jsonPrimitive?.content?.contains("audio", true) == true }
        val mapped = audios.mapNotNull { f ->
            val url = f["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!url.startsWith("http")) return@mapNotNull null
            if (f["signatureCipher"] != null) return@mapNotNull null // needs decipher
            val br = (f["bitrate"] ?: f["averageBitrate"])?.jsonPrimitive?.intOrNull ?: 0
            val q = if(br>0) "${br/1000}kbps" else f["qualityLabel"]?.jsonPrimitive?.content ?: "audio"
            val type = f["mimeType"]?.jsonPrimitive?.content?.split(";")?.firstOrNull()?.trim() ?: "audio"
            StreamInfo(q, url, type)
        }
        if (mapped.isEmpty()) throw IllegalStateException("no playable audio URLs for $vid")
        return mapped.sortedWith(compareByDescending<StreamInfo> { if(it.type.contains("mp4")) 1 else 0 }.thenByDescending { it.quality.filter{ c->c.isDigit() }.toIntOrNull() ?: 0 })
    }
}
