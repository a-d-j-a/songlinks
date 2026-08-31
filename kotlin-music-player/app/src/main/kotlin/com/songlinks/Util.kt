package com.songlinks

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

object Util {
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val MAX_RETRIES = 3
    private const val RETRY_BASE_MS = 250L

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Util.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        followRedirects = true
        expectSuccess = false
    }

    private val httpDateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") },
        SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss z", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") },
        SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") },
    )

    fun isRetryableStatus(status: HttpStatusCode): Boolean {
        val code = status.value
        return code == 408 || code == 429 || code in 500..599
    }

    fun parseRetryAfter(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val trimmed = header.trim()
        trimmed.toLongOrNull()?.let { return it * 1000L }
        for (fmt in httpDateFormats) {
            try {
                val date = fmt.parse(trimmed)
                if (date != null) {
                    val diff = date.time - System.currentTimeMillis()
                    return maxOf(diff, 0L)
                }
            } catch (_: Exception) { }
        }
        return null
    }

    fun <T> dedupe(list: List<T>, keyFn: (T) -> Any?): List<T> {
        val seen = mutableSetOf<String>()
        return list.filter { item ->
            val rawKey = try { keyFn(item) } catch (_: Exception) { null }
            if (rawKey == null) return@filter false
            val key = when {
                rawKey is Double && (rawKey.isNaN() || rawKey.isInfinite()) -> return@filter false
                rawKey is Float && (rawKey.isNaN() || rawKey.isInfinite()) -> return@filter false
                rawKey is String && rawKey.isEmpty() -> return@filter false
                else -> rawKey.toString()
            }
            if (key.isEmpty()) return@filter false
            if (seen.contains(key)) false else { seen.add(key); true }
        }
    }

    suspend inline fun <reified T> fetchJson(
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        headers: Map<String, String> = emptyMap(),
        method: HttpMethod = HttpMethod.Get,
        body: Any? = null,
    ): T {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return withTimeout(timeoutMs) {
                    val response = httpClient.request(url) {
                        this.method = method
                        headers.forEach { (k, v) -> header(k, v) }
                        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                            header("User-Agent", UA)
                        }
                        if (headers.keys.none { it.equals("Accept", ignoreCase = true) }) {
                            header("Accept", "application/json")
                        }
                        if (body != null) setBody(body)
                    }
                    val status = response.status
                    if (status.isSuccess()) {
                        response.body<T>()
                    } else {
                        val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
                        if (isRetryableStatus(status) && attempt < MAX_RETRIES - 1) {
                            val retryMs = if (status == HttpStatusCode.TooManyRequests) {
                                val after = parseRetryAfter(response.headers["Retry-After"])
                                after?.coerceAtLeast(RETRY_BASE_MS * (1L shl attempt))
                                    ?: RETRY_BASE_MS * (1L shl attempt)
                            } else {
                                RETRY_BASE_MS * (1L shl attempt)
                            }
                            delay(retryMs)
                            throw RetryableHttpException(status, errorBody)
                        }
                        throw HttpFetchException(status, errorBody, url)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableHttpException) {
                lastException = e
                if (attempt == MAX_RETRIES - 1) throw HttpFetchException(e.status, e.body, url)
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_BASE_MS * (1L shl attempt))
                } else {
                    throw NetworkException("Network error fetching $url after $MAX_RETRIES attempts: ${e.message}", e)
                }
            } catch (e: io.ktor.client.plugins.TimeoutRequestException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_BASE_MS * (1L shl attempt))
                } else {
                    throw TimeoutException("Timeout fetching $url after $MAX_RETRIES attempts", e)
                }
            } catch (e: io.ktor.utils.io.core.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt == MAX_RETRIES - 1) {
                    throw FetchException("Failed to fetch $url: ${e.message}", e)
                }
                delay(RETRY_BASE_MS * (1L shl attempt))
            }
        }
        throw FetchException("Failed to fetch $url after $MAX_RETRIES attempts", lastException)
    }

    suspend fun fetchBytes(
        url: String,
        timeoutMs: Long = 15_000,
        headers: Map<String, String> = emptyMap(),
    ): Pair<ByteArray, HttpStatusCode> {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = withTimeout(timeoutMs) {
                    httpClient.request(url) {
                        headers.forEach { (k, v) -> header(k, v) }
                        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                            header("User-Agent", UA)
                        }
                    }
                }
                val status = response.status
                if (status.isSuccess()) {
                    val bytes = response.readBytes()
                    return Pair(bytes, status)
                } else {
                    if (isRetryableStatus(status) && attempt < MAX_RETRIES - 1) {
                        val retryMs = if (status == HttpStatusCode.TooManyRequests) {
                            val after = parseRetryAfter(response.headers["Retry-After"])
                            after?.coerceAtLeast(RETRY_BASE_MS * (1L shl attempt))
                                ?: RETRY_BASE_MS * (1L shl attempt)
                        } else {
                            RETRY_BASE_MS * (1L shl attempt)
                        }
                        delay(retryMs)
                        continue
                    }
                    throw HttpFetchException(status, try { response.bodyAsText() } catch (_: Exception) { "" }, url)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_BASE_MS * (1L shl attempt))
                } else {
                    throw NetworkException("Network error fetching bytes from $url after $MAX_RETRIES attempts: ${e.message}", e)
                }
            } catch (e: io.ktor.client.plugins.TimeoutRequestException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_BASE_MS * (1L shl attempt))
                } else {
                    throw TimeoutException("Timeout fetching bytes from $url after $MAX_RETRIES attempts", e)
                }
            } catch (e: HttpFetchException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt == MAX_RETRIES - 1) {
                    throw FetchException("Failed to fetch bytes from $url: ${e.message}", e)
                }
                delay(RETRY_BASE_MS * (1L shl attempt))
            }
        }
        throw FetchException("Failed to fetch bytes from $url after $MAX_RETRIES attempts", lastException)
    }

    suspend fun streamResponse(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse {
        return httpClient.prepareGet(url) {
            headers.forEach { (k, v) -> header(k, v) }
            if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                header("User-Agent", UA)
            }
        }.execute { it }
    }
}

open class FetchException(message: String, cause: Throwable? = null) : Exception(message, cause)
class HttpFetchException(val status: HttpStatusCode, val body: String, url: String) :
    FetchException("HTTP ${status.value} from $url: ${body.take(500)}")
class NetworkException(message: String, cause: Throwable? = null) : FetchException(message, cause)
class TimeoutException(message: String, cause: Throwable? = null) : FetchException(message, cause)
private class RetryableHttpException(val status: HttpStatusCode, val body: String) :
    Exception("Retryable HTTP ${status.value}")
