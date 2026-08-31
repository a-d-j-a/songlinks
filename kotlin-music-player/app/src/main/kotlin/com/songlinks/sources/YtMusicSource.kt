package com.songlinks.sources

import com.songlinks.FetchException
import com.songlinks.MusicSource
import com.songlinks.SongResult
import com.songlinks.StreamInfo
import com.songlinks.Util
import kotlinx.serialization.json.*

object YtMusicSource : MusicSource {
    override val name = "ytmusic"

    private const val INNERTUBE_SEARCH = "https://music.youtube.com/youtubei/v1/search"
    private const val INNERTUBE_PLAYER = "https://www.youtube.com/youtubei/v1/player"
    private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val WEB_CLIENT_VERSION = "1.20241202.01.00"
    private const val ANDROID_CLIENT_VERSION = "20.10.38"
    private const val ANDROID_SDK_VERSION = "30"
    private const val DEFAULT_LIMIT = 10
    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 50
    private const val MAX_QUERY_LEN = 300
    private const val TIMEOUT_MS = 15_000L

    // ── safe accessors ─────────────────────────────────────────────────

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    // ── validation helpers ─────────────────────────────────────────────

    private fun normalizeQuery(q: String): String? {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.length > MAX_QUERY_LEN) trimmed.substring(0, MAX_QUERY_LEN) else trimmed
    }

    private fun normalizeLimit(raw: Int): Int = raw.coerceIn(MIN_LIMIT, MAX_LIMIT)

    private fun parseStringField(value: String?): String? {
        if (value == null) return null
        return try { value.trim().ifEmpty { null } } catch (_: Exception) { null }
    }

    // ── duration parsing ───────────────────────────────────────────────

    private fun parseDurationTextSingle(single: String?): Int? {
        if (single == null) return null
        val str = single.trim()
        if (str.isEmpty()) return null

        val hms = Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})$").find(str)
        if (hms != null) {
            val h = hms.groupValues[1].toIntOrNull() ?: return null
            val m = hms.groupValues[2].toIntOrNull() ?: return null
            val s = hms.groupValues[3].toIntOrNull() ?: return null
            if (m >= 60 || s >= 60) return null
            val total = h * 3600 + m * 60 + s
            return if (total > 0) total else null
        }

        val ms = Regex("^(\\d+):(\\d{1,2})$").find(str)
        if (ms != null) {
            val m = ms.groupValues[1].toIntOrNull() ?: return null
            val s = ms.groupValues[2].toIntOrNull() ?: return null
            if (s >= 60) return null
            val total = m * 60 + s
            return if (total > 0) total else null
        }

        if (Regex("^\\d+$").matches(str)) {
            val n = str.toIntOrNull() ?: return null
            if (n > 0 && n < 36000) return n
        }
        return null
    }

    private fun parseDurationText(text: String?): Int? {
        if (text == null) return null
        val s = text.trim()
        if (s.isEmpty()) return null
        if (s.contains("•")) {
            val parts = s.split("•").map { it.trim() }.filter { it.isNotEmpty() }
            for (i in parts.indices.reversed()) {
                val d = parseDurationTextSingle(parts[i])
                if (d != null) return d
            }
            return null
        }
        return parseDurationTextSingle(s)
    }

    private fun parseDurationMillis(value: Any?): Int? {
        if (value == null) return null
        val n = when (value) {
            is Number -> value.toDouble()
            is String -> { val t = value.trim(); if (t.isEmpty()) return null; t.toDoubleOrNull() ?: return null }
            else -> return null
        }
        if (!n.isFinite() || n <= 0) return null
        val secs = Math.round(n / 1000.0).toInt()
        return if (secs > 0) secs else null
    }

    // ── renderer traversal ─────────────────────────────────────────────

    private fun getFlexColumnRuns(renderer: JsonObject, idx: Int): List<JsonObject> {
        try {
            val cols = renderer["flexColumns"]?.arr() ?: return emptyList()
            val col = cols.getOrNull(idx)?.obj() ?: return emptyList()
            val runs = col["musicResponsiveListItemFlexColumnRenderer"]?.obj()
                ?.get("text")?.obj()
                ?.get("runs")?.arr() ?: return emptyList()
            return runs.mapNotNull { it.obj() }
        } catch (_: Exception) { return emptyList() }
    }

    private fun runsToText(runs: List<JsonObject>): String {
        if (runs.isEmpty()) return ""
        return runs.joinToString("") { it["text"]?.str() ?: "" }
    }

    private fun getFixedColumnText(renderer: JsonObject, idx: Int): String? {
        try {
            val cols = renderer["fixedColumns"]?.arr() ?: return null
            val col = cols.getOrNull(idx)?.obj() ?: return null
            val fcr = col["musicResponsiveListItemFixedColumnRenderer"]?.obj() ?: return null
            val runs = fcr["text"]?.obj()?.get("runs")?.arr()
            if (runs != null && runs.isNotEmpty()) {
                val t = runs.joinToString("") { it.obj()?.get("text")?.str() ?: "" }.trim()
                if (t.isNotEmpty()) return t
            }
            val simple = fcr["text"]?.obj()?.get("simpleText")?.str()
            if (simple != null && simple.trim().isNotEmpty()) return simple.trim()
        } catch (_: Exception) {}
        return null
    }

    private fun resolveThumbnail(renderer: JsonObject): String? {
        try {
            val t1 = renderer["thumbnail"]?.obj()
                ?.get("musicThumbnailRenderer")?.obj()
                ?.get("thumbnail")?.obj()
                ?.get("thumbnails")?.arr()
            if (t1 != null) {
                for (i in t1.indices.reversed()) {
                    val u = t1[i].obj()?.get("url")?.str()?.trim()
                    if (!u.isNullOrEmpty() && u.startsWith("http")) return u
                }
            }
            val t2 = renderer["thumbnailRenderer"]?.obj()
                ?.get("musicThumbnailRenderer")?.obj()
                ?.get("thumbnail")?.obj()
                ?.get("thumbnails")?.arr()
            if (t2 != null) {
                for (i in t2.indices.reversed()) {
                    val u = t2[i].obj()?.get("url")?.str()?.trim()
                    if (!u.isNullOrEmpty() && u.startsWith("http")) return u
                }
            }
            val t3 = renderer["thumbnail"]?.obj()
                ?.get("thumbnails")?.arr()
            if (t3 != null) {
                for (i in t3.indices.reversed()) {
                    val u = t3[i].obj()?.get("url")?.str()?.trim()
                    if (!u.isNullOrEmpty()) return u
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isValidVideoId(v: String): Boolean {
        if (v.length < 5 || v.length > 100) return false
        if (v.contains(Regex("\\s"))) return false
        return Regex("^[a-zA-Z0-9_-]+$").matches(v)
    }

    private fun resolveVideoId(renderer: JsonObject): String? {
        val candidates: List<() -> String?> = listOf(
            { renderer["overlay"]?.obj()?.get("musicItemThumbnailOverlayRenderer")?.obj()
                ?.get("content")?.obj()?.get("musicPlayButtonRenderer")?.obj()
                ?.get("playNavigationEndpoint")?.obj()?.get("watchEndpoint")?.obj()
                ?.get("videoId")?.str() },
            { renderer["overlay"]?.obj()?.get("musicItemThumbnailOverlayRenderer")?.obj()
                ?.get("content")?.obj()?.get("musicPlayButtonRenderer")?.obj()
                ?.get("playNavigationEndpoint")?.obj()?.get("watchPlaylistEndpoint")?.obj()
                ?.get("videoId")?.str() },
            { renderer["playlistItemData"]?.obj()?.get("videoId")?.str() },
            { renderer["navigationEndpoint"]?.obj()?.get("watchEndpoint")?.obj()
                ?.get("videoId")?.str() },
            { renderer["doubleTapCommand"]?.obj()?.get("watchEndpoint")?.obj()
                ?.get("videoId")?.str() },
            {
                val items = renderer["menu"]?.obj()?.get("menuRenderer")?.obj()
                    ?.get("items")?.arr() ?: return@listOf null
                for (item in items) {
                    val o = item.obj() ?: continue
                    val vid = o["menuNavigationItemRenderer"]?.obj()
                        ?.get("navigationEndpoint")?.obj()
                        ?.get("watchEndpoint")?.obj()?.get("videoId")?.str()
                        ?: o["menuServiceItemRenderer"]?.obj()
                            ?.get("serviceEndpoint")?.obj()
                            ?.get("queueAddEndpoint")?.obj()
                            ?.get("queueTarget")?.obj()?.get("videoId")?.str()
                        ?: o["menuServiceItemRenderer"]?.obj()
                            ?.get("serviceEndpoint")?.obj()
                            ?.get("playlistEditEndpoint")?.obj()
                            ?.get("actions")?.arr()?.getOrNull(0)?.obj()
                            ?.get("addedVideoId")?.str()
                        ?: o["toggleMenuServiceItemRenderer"]?.obj()
                            ?.get("defaultServiceEndpoint")?.obj()
                            ?.get("likeEndpoint")?.obj()
                            ?.get("target")?.obj()?.get("videoId")?.str()
                        ?: o["toggleMenuServiceItemRenderer"]?.obj()
                            ?.get("toggledServiceEndpoint")?.obj()
                            ?.get("likeEndpoint")?.obj()
                            ?.get("target")?.obj()?.get("videoId")?.str()
                    if (vid != null) return@listOf vid
                }
                null
            },
            {
                val cols = renderer["flexColumns"]?.arr() ?: return@listOf null
                for (col in cols) {
                    val runs = col.obj()?.get("musicResponsiveListItemFlexColumnRenderer")?.obj()
                        ?.get("text")?.obj()?.get("runs")?.arr() ?: continue
                    for (run in runs) {
                        val ro = run.obj() ?: continue
                        val vid = ro["navigationEndpoint"]?.obj()
                            ?.get("watchEndpoint")?.obj()?.get("videoId")?.str()
                        if (vid != null) return@listOf vid
                        val browseId = ro["navigationEndpoint"]?.obj()
                            ?.get("browseEndpoint")?.obj()?.get("browseId")?.str()
                        if (browseId != null && Regex("^[a-zA-Z0-9_-]{11}$").matches(browseId)) {
                            return@listOf browseId
                        }
                    }
                }
                null
            }
        )

        for (fn in candidates) {
            try {
                val v = fn()
                if (v != null && v.trim().isNotEmpty()) {
                    val trimmed = v.trim()
                    if (isValidVideoId(trimmed)) return trimmed
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun resolveArtist(subtitleRuns: List<JsonObject>): String? {
        if (subtitleRuns.isEmpty()) return null
        val parts = mutableListOf<String>()
        var cur = ""
        for (r in subtitleRuns) {
            val t = r["text"]?.str() ?: ""
            if (t == " • ") { parts.add(cur.trim()); cur = "" }
            else cur += t
        }
        if (cur.isNotEmpty()) parts.add(cur.trim())
        val filtered = parts.map { it.trim() }.filter { it.isNotEmpty() }
        if (filtered.isEmpty()) return null

        val durationRe = Regex("^(\\d+:)?\\d+:\\d+$")
        val yearRe = Regex("^(19|20)\\d{2}$")
        val viewsRe = Regex("^\\d+([,.]\\d+)*\\s*(views|plays|listeners|watching)", RegexOption.IGNORE_CASE)
        val countRe = Regex("^\\d[\\d,.]*$")
        val explicitRe = Regex("^explicit$", RegexOption.IGNORE_CASE)

        for (p in filtered) {
            if (p.isEmpty()) continue
            if (durationRe.matches(p)) continue
            if (yearRe.matches(p)) continue
            if (viewsRe.containsMatchIn(p)) continue
            if (explicitRe.matches(p)) continue
            if (countRe.matches(p) && p.length < 6) continue
            return p
        }
        val nonMeta = filtered.filter { !durationRe.matches(it) && !yearRe.matches(it) && !viewsRe.containsMatchIn(it) }
        return nonMeta.firstOrNull()
    }

    private fun resolveTitle(titleRuns: List<JsonObject>): String? {
        if (titleRuns.isEmpty()) return null
        return runsToText(titleRuns).trim().ifEmpty { null }
    }

    private fun resolveDuration(renderer: JsonObject, subtitleRuns: List<JsonObject>): Int? {
        val fixed = getFixedColumnText(renderer, 0)
        if (fixed != null) {
            val d = parseDurationText(fixed)
            if (d != null) return d
        }
        if (subtitleRuns.isNotEmpty()) {
            val text = runsToText(subtitleRuns)
            if (text.isNotEmpty()) {
                val d = parseDurationText(text)
                if (d != null) return d
            }
            val parts = mutableListOf<String>()
            var cur = ""
            for (r in subtitleRuns) {
                val t = r["text"]?.str() ?: ""
                if (t == " • ") { parts.add(cur); cur = "" }
                else cur += t
            }
            if (cur.isNotEmpty()) parts.add(cur)
            for (i in parts.indices.reversed()) {
                val d = parseDurationTextSingle(parts[i].trim())
                if (d != null) return d
            }
        }
        try {
            val alt = renderer["fixedColumns"]?.arr()?.getOrNull(0)?.obj()
                ?.get("musicResponsiveListItemFixedColumnRenderer")?.obj()
                ?.get("text")?.obj()?.get("runs")?.arr()?.getOrNull(0)?.obj()
                ?.get("text")?.str()
                ?: renderer["lengthText"]?.obj()?.get("runs")?.arr()?.getOrNull(0)?.obj()
                    ?.get("text")?.str()
                ?: renderer["lengthText"]?.obj()?.get("simpleText")?.str()
            if (alt != null) {
                val d = parseDurationText(alt)
                if (d != null) return d
            }
        } catch (_: Exception) {}
        return null
    }

    // ── mapMusicItem ───────────────────────────────────────────────────

    private fun mapMusicItem(renderer: JsonObject): SongResult? {
        if (renderer.isEmpty()) return null

        val videoId = resolveVideoId(renderer) ?: return null
        val vidStr = videoId.trim()
        if (vidStr.isEmpty() || vidStr == "null" || vidStr == "undefined" || vidStr.equals("nan", ignoreCase = true)) return null
        if (vidStr.length > 100 || vidStr.contains(Regex("\\s"))) return null

        val titleRuns = getFlexColumnRuns(renderer, 0)
        val subtitleRuns = getFlexColumnRuns(renderer, 1)

        var title = resolveTitle(titleRuns)
        if (title == null) {
            try {
                val simple = renderer["flexColumns"]?.arr()?.getOrNull(0)?.obj()
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.obj()
                    ?.get("text")?.obj()?.get("simpleText")?.str()
                if (simple != null && simple.trim().isNotEmpty()) title = simple.trim()
            } catch (_: Exception) {}
        }
        title = parseStringField(title)

        var artist = resolveArtist(subtitleRuns)
        if (artist == null) {
            val joined = runsToText(subtitleRuns).trim()
            if (joined.isNotEmpty()) {
                val first = joined.split("•").getOrNull(0)?.trim()
                if (first != null && first != title) {
                    val durationRe = Regex("^(\\d+:)?\\d+:\\d+$")
                    val yearRe = Regex("^(19|20)\\d{2}$")
                    val viewsRe = Regex("^\\d+([,.]\\d+)*\\s*(views|plays|listeners|watching)", RegexOption.IGNORE_CASE)
                    if (!durationRe.matches(first) && !yearRe.matches(first) && !viewsRe.containsMatchIn(first)) {
                        artist = parseStringField(first)
                    }
                }
            }
        } else {
            artist = parseStringField(artist)
            if (artist != null && Regex("^(\\d+:)?\\d+:\\d+$").matches(artist)) artist = null
        }

        var duration = resolveDuration(renderer, subtitleRuns)
        if (duration != null && duration <= 0) duration = null

        val cover = resolveThumbnail(renderer)
        val page = "https://music.youtube.com/watch?v=$vidStr"

        return SongResult(
            source = "ytmusic",
            id = vidStr,
            title = title,
            artist = artist,
            duration = duration,
            cover = cover,
            page = page,
            streams = emptyList()
        )
    }

    // ── extractResults ─────────────────────────────────────────────────

    private fun extractResults(json: JsonElement): List<SongResult> {
        if (json !is JsonObject) return emptyList()

        var contents: JsonArray? = null

        try {
            val tabs = json["contents"]?.obj()?.get("tabbedSearchResultsRenderer")?.obj()
                ?.get("tabs")?.arr()
            if (tabs != null && tabs.isNotEmpty()) {
                for (tab in tabs) {
                    val tr = tab.obj()?.get("tabRenderer")?.obj() ?: continue
                    val c = tr["content"]?.obj()?.get("sectionListRenderer")?.obj()
                        ?.get("contents")?.arr()
                    if (c != null && c.isNotEmpty()) { contents = c; break }
                    val alt = tr["content"]?.obj()?.get("sectionListRenderer")?.obj()
                        ?.get("contents")?.arr()
                    if (alt != null && alt.isNotEmpty()) { contents = alt; break }
                }
            }
        } catch (_: Exception) {}

        if (contents == null) {
            try {
                val sl = json["contents"]?.obj()?.get("sectionListRenderer")?.obj()
                    ?.get("contents")?.arr()
                if (sl != null && sl.isNotEmpty()) contents = sl
            } catch (_: Exception) {}
        }

        if (contents == null) {
            try {
                val raw = json["contents"]?.arr()
                if (raw != null && raw.isNotEmpty()) contents = raw
            } catch (_: Exception) {}
        }

        if (contents == null || contents.isEmpty()) {
            val foundWrappers = mutableListOf<JsonObject>()
            val stack = mutableListOf<JsonElement>(json)
            val seen = mutableSetOf<JsonElement>()
            var steps = 0
            while (stack.isNotEmpty() && steps < 400) {
                val cur = stack.removeLast()
                steps++
                if (cur !is JsonObject || seen.contains(cur)) continue
                seen.add(cur)

                cur["musicShelfRenderer"]?.obj()?.get("contents")?.arr()?.forEach { w ->
                    val wo = w.obj()
                    if (wo != null && wo.containsKey("musicResponsiveListItemRenderer")) foundWrappers.add(wo)
                }
                cur["musicCardShelfRenderer"]?.obj()?.get("contents")?.arr()?.forEach { w ->
                    val wo = w.obj()
                    if (wo != null && wo.containsKey("musicResponsiveListItemRenderer")) foundWrappers.add(wo)
                }
                cur["itemSectionRenderer"]?.obj()?.get("contents")?.arr()?.forEach { w ->
                    val wo = w.obj() ?: return@forEach
                    if (wo.containsKey("musicResponsiveListItemRenderer")) foundWrappers.add(wo)
                    wo["musicShelfRenderer"]?.obj()?.get("contents")?.arr()?.forEach { iw ->
                        val iwo = iw.obj()
                        if (iwo != null && iwo.containsKey("musicResponsiveListItemRenderer")) foundWrappers.add(iwo)
                    }
                }

                for (v in cur.values) {
                    when (v) {
                        is JsonObject -> stack.add(v)
                        is JsonArray -> v.forEach { if (it is JsonObject) stack.add(it) }
                    }
                }
            }
            if (foundWrappers.isNotEmpty()) {
                return foundWrappers.mapNotNull { w ->
                    try {
                        mapMusicItem(w["musicResponsiveListItemRenderer"]?.obj() ?: w)
                    } catch (_: Exception) { null }
                }
            }
            return emptyList()
        }

        val items = mutableListOf<SongResult>()
        for (sec in contents) {
            val o = sec.obj() ?: continue
            val inside = o["musicShelfRenderer"]?.obj()?.get("contents")?.arr()
                ?: o["itemSectionRenderer"]?.obj()?.get("contents")?.arr()
                ?: o["musicCardShelfRenderer"]?.obj()?.get("contents")?.arr()
                ?: continue
            for (wrapped in inside) {
                val wo = wrapped.obj() ?: continue
                val renderer = wo["musicResponsiveListItemRenderer"]?.obj()
                    ?: (if (wo.containsKey("flexColumns")) wo else null) ?: continue
                try { mapMusicItem(renderer)?.let { items.add(it) } } catch (_: Exception) {}
            }
        }
        return items
    }

    // ── stream helpers ─────────────────────────────────────────────────

    private fun validateVideoId(id: String): String {
        var s = id.trim()
        if (s.startsWith("yt:")) s = s.removePrefix("yt:").trim()
        if (s.isEmpty()) throw IllegalArgumentException("ytmusic.stream: id is empty after stripping prefix")
        if (s.length > 100) throw IllegalArgumentException("ytmusic.stream: id too long (max 100)")
        if (s.contains(Regex("\\s"))) throw IllegalArgumentException("ytmusic.stream: id must not contain whitespace")
        if (!Regex("^[a-zA-Z0-9_-]+$").matches(s)) throw IllegalArgumentException("ytmusic.stream: id contains invalid characters")
        if (s.length < 5) throw IllegalArgumentException("ytmusic.stream: id too short")
        return s
    }

    private fun parseBitrate(value: JsonPrimitive?): Int {
        if (value == null) return 0
        val n = value.intOrNull ?: return 0
        return if (n > 0) n else 0
    }

    private fun parseContentLength(value: JsonPrimitive?): Long? {
        if (value == null) return null
        val n = value.longOrNull ?: return null
        return if (n >= 0) n else null
    }

    // ── public API ─────────────────────────────────────────────────────

    override suspend fun search(q: String, limit: Int): List<SongResult> {
        val normalizedQ = normalizeQuery(q) ?: return emptyList()
        val normalizedLimit = normalizeLimit(limit)

        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_CLIENT_VERSION)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("query", normalizedQ)
        }

        val res = try {
            Util.postJson("$INNERTUBE_SEARCH?key=$API_KEY&prettyPrint=false", body.toString(), TIMEOUT_MS)
        } catch (e: Exception) {
            throw FetchException("ytmusic search failed for \"${normalizedQ.take(50)}\": ${e.message}", e)
        }

        if (res !is JsonObject) return emptyList()

        val items = try { extractResults(res) } catch (e: Exception) {
            throw FetchException("ytmusic: failed to parse search results: ${e.message}", e)
        }

        val valid = mutableListOf<SongResult>()
        for (item in items) {
            try {
                if (item.id.isEmpty() || item.source.isEmpty()) continue
                val idStr = item.id.trim()
                if (idStr.isEmpty()) continue
                if (item.duration != null && item.duration <= 0) continue
                valid.add(item)
                if (valid.size >= normalizedLimit) break
            } catch (_: Exception) {}
        }
        return valid
    }

    private suspend fun streamWithClient(clientName: String, clientVersion: String, vid: String, sdkVersion: String = "30"): List<StreamInfo> {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", clientName)
                    put("clientVersion", clientVersion)
                    if (sdkVersion.isNotEmpty()) put("androidSdkVersion", sdkVersion)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", vid)
            put("racyCheckOk", true)
            put("contentCheckOk", true)
        }

        val res = Util.postJson("$INNERTUBE_PLAYER?key=$API_KEY", body.toString(), TIMEOUT_MS)
        val obj = res.jsonObject

        val playStatus = obj["playabilityStatus"]?.obj()
        if (playStatus != null) {
            val status = playStatus["status"]?.str()?.uppercase() ?: ""
            val reason = parseStringField(playStatus["reason"]?.str())
                ?: playStatus["messages"]?.arr()?.getOrNull(0)?.str()?.trim()
                ?: ""
            val errorStatuses = setOf("ERROR", "LOGIN_REQUIRED", "UNPLAYABLE", "AGE_VERIFICATION_REQUIRED")
            if (status in errorStatuses) {
                val msg = if (reason.isNotEmpty()) "$status: $reason" else "Video ${status.lowercase()} ($vid)"
                throw FetchException("ytmusic stream ($clientName): $msg")
            }
        }

        val streamingData = obj["streamingData"]?.obj()
            ?: throw FetchException("ytmusic stream ($clientName): no streamingData for $vid")

        val rawFormats = mutableListOf<JsonObject>()
        streamingData["adaptiveFormats"]?.arr()?.forEach { it.obj()?.let { o -> rawFormats.add(o) } }
        streamingData["formats"]?.arr()?.forEach { it.obj()?.let { o -> rawFormats.add(o) } }
        if (rawFormats.isEmpty()) throw FetchException("ytmusic stream ($clientName): empty formats for $vid")

        val audioOnly = rawFormats.filter { f ->
            val mt = f["mimeType"]?.str() ?: ""
            mt.contains("audio", ignoreCase = true)
        }
        if (audioOnly.isEmpty()) throw FetchException("ytmusic stream ($clientName): no audio formats for $vid")

        data class MappedFormat(val quality: String, val url: String, val type: String, val bitrate: Int, val contentLength: Long?)

        val mapped = mutableListOf<MappedFormat>()
        var cipherCount = 0

        for (f in audioOnly) {
            try {
                val hasCipher = f["signatureCipher"]?.str()?.trim()?.isNotEmpty() == true
                val rawUrl = f["url"]?.str()?.trim() ?: ""
                val hasUrl = rawUrl.isNotEmpty() && rawUrl.startsWith("http", ignoreCase = true)
                if (hasCipher && !hasUrl) { cipherCount++; continue }
                if (!hasUrl) { if (hasCipher) cipherCount++; continue }

                val bitrate = parseBitrate(f["bitrate"]?.prim() ?: f["averageBitrate"]?.prim())
                val quality = if (bitrate > 0) "${bitrate / 1000}kbps"
                    else parseStringField(f["qualityLabel"]?.str())
                        ?: parseStringField(f["quality"]?.str())
                        ?: "audio"
                val type = f["mimeType"]?.str()?.split(";")?.firstOrNull()?.trim() ?: "audio"
                val contentLength = parseContentLength(
                    f["contentLength"]?.prim() ?: f["contentLengthMs"]?.prim() ?: f["approxDurationMs"]?.prim()
                )
                mapped.add(MappedFormat(quality, rawUrl, type, bitrate, contentLength))
            } catch (_: Exception) { continue }
        }

        if (mapped.isEmpty()) {
            if (cipherCount > 0) throw FetchException("ytmusic stream ($clientName): all $cipherCount audio formats require signature deciphering for $vid")
            throw FetchException("ytmusic stream ($clientName): no playable audio URLs for $vid")
        }

        mapped.sortWith(
            compareByDescending<MappedFormat> { if (it.type.contains("mp4")) 1 else 0 }
                .thenByDescending { it.bitrate }
                .thenByDescending { it.contentLength ?: 0 }
        )

        return mapped.map { StreamInfo(quality = it.quality, url = it.url, type = it.type) }
    }

    suspend fun stream(videoId: String): List<StreamInfo> {
        val vid = validateVideoId(videoId)

        // Try ANDROID first (20.10.38 returns direct URLs), fall back to WEB
        return try {
            streamWithClient("ANDROID", ANDROID_CLIENT_VERSION, vid, ANDROID_SDK_VERSION)
        } catch (e: Exception) {
            try {
                streamWithClient("WEB", WEB_CLIENT_VERSION, vid, "")
            } catch (_: Exception) {
                throw FetchException("ytmusic stream: both ANDROID and WEB failed for $vid: ${e.message}", e)
            }
        }
    }
}
