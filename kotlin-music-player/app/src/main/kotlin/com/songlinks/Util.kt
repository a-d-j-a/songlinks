package com.songlinks

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

// Port of src/util.js — fetchJson with timeout, retry, dedupe, UA
object Util {
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun <T> dedupe(list: List<T>, keyFn: (T) -> Any?): List<T> {
        val seen = mutableSetOf<String>()
        return list.filter {
            val k = try { keyFn(it) } catch (_: Exception) { null }
            if (k == null || k == "" ) return@filter false
            val key = when(k) {
                is String, is Number, is Boolean -> k.toString()
                else -> try { json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), json.encodeToJsonElement(k)) } catch(_:Exception){ k.toString() }
            }
            if (seen.contains(key)) false else { seen.add(key); true }
        }
    }

    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Util.json) }
        install(HttpTimeout) { requestTimeoutMillis = 15000; connectTimeoutMillis = 6000 }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 1)
            exponentialDelay()
        }
    }

    suspend inline fun <reified T> fetchJson(url: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, headers: Map<String,String> = emptyMap(), method: HttpMethod = HttpMethod.Get, body: Any? = null): T {
        return withTimeout(timeoutMs) {
            client.request(url) {
                this.method = method
                headers.forEach { (k,v) -> header(k, v) }
                if (!headers.keys.any { it.equals("User-Agent", true) }) header("User-Agent", UA)
                if (!headers.keys.any { it.equals("Accept", true) }) header("Accept", "application/json")
                if (body != null) setBody(body)
            }.body()
        }
    }
}
