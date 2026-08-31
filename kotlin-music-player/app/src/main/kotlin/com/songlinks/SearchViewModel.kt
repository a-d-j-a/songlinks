package com.songlinks

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songlinks.sources.ItunesSource
import com.songlinks.sources.JioSaavnSource
import com.songlinks.sources.YtMusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var limit by mutableStateOf(10)
    var sources by mutableStateOf(setOf("itunes", "jiosaavn", "ytmusic"))
    var results by mutableStateOf<List<SongResult>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var perSource by mutableStateOf<List<PerSource>>(emptyList())
    var tookMs by mutableStateOf(0L)

    private val adapters = mapOf(
        "itunes" to ItunesSource,
        "jiosaavn" to JioSaavnSource,
        "ytmusic" to YtMusicSource
    )

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) { error = "Enter query"; return }
        viewModelScope.launch {
            isLoading = true; error = null
            val active = if (sources.size == 3 || sources.isEmpty()) adapters.keys else sources
            val t0 = System.currentTimeMillis()
            val jobs = active.map { name ->
                async {
                    val s = System.currentTimeMillis()
                    try {
                        val data = withTimeoutOrNull(4000) {
                            adapters[name]!!.search(q, limit)
                        } ?: emptyList()
                        PerSource(name, true, data.size, System.currentTimeMillis() - s) to data
                    } catch (e: Exception) {
                        PerSource(name, false, 0, System.currentTimeMillis() - s, e.message) to
                                emptyList<SongResult>()
                    }
                }
            }
            val pairs = jobs.awaitAll()
            perSource = pairs.map { it.first }
            val flat = pairs.flatMap { it.second }
            results = aggregate(flat)
            tookMs = System.currentTimeMillis() - t0
            isLoading = false
        }
    }

    private fun aggregate(results: List<SongResult>): List<SongResult> {
        // Port of src/api.js aggregate() — composite source:id key, stream dedup, field fill
        val grouped = linkedMapOf<String, AggregateEntry>()
        for (r in results) {
            if (r.id.isEmpty() || r.source.isEmpty()) continue
            val key = "${r.source}:${r.id}"
            val normalized = r.copy(
                streams = Util.dedupe(r.streams) { it.url }
            )
            val existing = grouped[key]
            if (existing == null) {
                grouped[key] = AggregateEntry(
                    result = normalized,
                    sourceList = mutableListOf(r.source)
                )
            } else {
                if (!existing.sourceList.contains(r.source)) existing.sourceList.add(r.source)
                val merged = Util.dedupe(existing.result.streams + normalized.streams) { it.url }
                val base = existing.result
                existing.result = base.copy(
                    streams = merged,
                    title = base.title ?: normalized.title,
                    artist = base.artist ?: normalized.artist,
                    album = base.album ?: normalized.album,
                    cover = base.cover ?: normalized.cover,
                    page = base.page ?: normalized.page,
                    duration = base.duration ?: normalized.duration,
                    genre = base.genre ?: normalized.genre,
                    language = base.language ?: normalized.language,
                    release = base.release ?: normalized.release,
                    sources = existing.sourceList.toList()
                )
            }
        }
        return grouped.values.map { entry ->
            entry.result.copy(
                streams = Util.dedupe(entry.result.streams) { it.url },
                sources = entry.sourceList.toList()
            )
        }
    }

    private class AggregateEntry(
        var result: SongResult,
        val sourceList: MutableList<String>
    )
}
