package com.songlinks.sources

import com.songlinks.Des
import com.songlinks.Models.*
import com.songlinks.Util
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

// Port of src/sources/jiosaavn.js — QUALITY_LADDER 12→320, DES decrypt, per-result isolate
object JioSaavnSource : com.songlinks.MusicSource {
    override val name = "jiosaavn"
    val QUALITY_LADDER = listOf("12","48","96","160","320")
    @Serializable private data class SaavnResp(val results: List<SaavnRow>? = null)
    @Serializable private data class SaavnRow(
        val id: String? = null, val title: String? = null, val subtitle: String? = null,
        val language: String? = null, val play_count: String? = null, val image: String? = null,
        val perma_url: String? = null, val explicit_content: String? = null,
        val more_info: SaavnInfo? = null
    )
    @Serializable private data class SaavnInfo(
        val album: String? = null, val duration: String? = null, val encrypted_media_url: String? = null,
        val is_dolby_content: String? = null, val singers: String? = null,
        val artistMap: ArtistMap? = null
    )
    @Serializable private data class ArtistMap(val primary_artists: List<Artist>? = null, val featured_artists: List<Artist>? = null, val artists: List<Artist>? = null)
    @Serializable private data class Artist(val name: String? = null, val title: String? = null)

    private fun upgrade(url: String, kbps: String) = url.replace(Regex("_(\\d+)\\.mp4", RegexOption.IGNORE_CASE), "_${kbps}.mp4")

    override suspend fun search(q: String, limit: Int): List<SongResult> {
        val nq = q.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val nLimit = limit.coerceIn(1, 50)
        val params = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=${encode(nq)}&_format=json&_marker=0&api_version=4&ctx=web6dot0&p=1&n=$nLimit"
        val res: SaavnResp = Util.client.get(params).body()
        return res.results.orEmpty().mapNotNull { r ->
            val id = r.id?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
            val meta = r.more_info
            val artists = run {
                val p = meta?.artistMap?.primary_artists.orEmpty().mapNotNull { it.name ?: it.title }
                val f = meta?.artistMap?.featured_artists.orEmpty().mapNotNull { it.name ?: it.title }
                val all = meta?.artistMap?.artists.orEmpty().mapNotNull { it.name ?: it.title }
                val combined = if (p.isNotEmpty() || f.isNotEmpty()) p+f else all
                if (combined.isNotEmpty()) combined.distinct().joinToString(", ") else meta?.singers ?: r.subtitle
            }
            val cover = r.image?.replace("150x150", "500x500")
            var decrypted: String? = null
            try { meta?.encrypted_media_url?.let { e -> if(e.isNotBlank()) decrypted = Des.decryptBase64(e) } } catch(_:Exception){}
            val streams = if (decrypted != null && decrypted!!.startsWith("http")) {
                val ext = if (decrypted!!.contains(".mp4")) "mp4" else "aac"
                QUALITY_LADDER.map { k -> StreamInfo("${k}kbps", upgrade(decrypted!!, k), ext) }
            } else emptyList()
            SongResult(
                source="jiosaavn", id=id, title=r.title, artist=artists, album=meta?.album,
                duration=meta?.duration?.toIntOrNull(), language=r.language,
                playCount=r.play_count?.toIntOrNull(), cover=cover, page=r.perma_url,
                streams=streams, extra=Extra(isExplicit = r.explicit_content=="1", isDolby = meta?.is_dolby_content=="true")
            )
        }
    }
    private fun encode(s:String)=java.net.URLEncoder.encode(s,"UTF-8")
}
