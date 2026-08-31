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
    var sources by mutableStateOf(setOf("itunes","jiosaavn","ytmusic"))
    var results by mutableStateOf<List<SongResult>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var perSource by mutableStateOf<List<PerSource>>(emptyList())

    private val adapters = mapOf("itunes" to ItunesSource, "jiosaavn" to JioSaavnSource, "ytmusic" to YtMusicSource)

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) { error="Enter query"; return }
        viewModelScope.launch {
            isLoading=true; error=null
            val active = if (sources.size==3 || sources.isEmpty()) adapters.keys else sources
            val t0 = System.currentTimeMillis()
            val jobs = active.map { name ->
                async {
                    val s = System.currentTimeMillis()
                    try {
                        val data = withTimeoutOrNull(4000) { adapters[name]!!.search(q, limit) } ?: emptyList()
                        PerSource(name, true, data.size, System.currentTimeMillis()-s) to data
                    } catch(e:Exception){
                        PerSource(name, false, 0, System.currentTimeMillis()-s, e.message) to emptyList<SongResult>()
                    }
                }
            }
            val pairs = jobs.awaitAll()
            perSource = pairs.map { it.first }
            // dedupe composite source:id (src/api.js:61)
            val flat = pairs.flatMap { it.second }
            val map = linkedMapOf<String, SongResult>()
            for(r in flat){
                val k="${r.source}:${r.id}"
                val existing = map[k]
                if(existing==null) map[k]=r else {
                    // merge streams
                    val merged = (existing.streams + r.streams).distinctBy { it.url }
                    map[k]=existing.copy(streams=merged)
                }
            }
            results = map.values.toList()
            isLoading=false
        }
    }

    fun playUrlFor(result: SongResult): String? {
        // Prefer jiosaavn 320kbps, itunes preview, ytmusic via proxy (1M cap, mp4)
        // For ytmusic, UI will call YtMusicSource.stream + proxy
        return result.streams.firstOrNull()?.url
    }
}
