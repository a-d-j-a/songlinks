package com.songlinks.app.util

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    companion object {
        @Volatile private var INSTANCE: NewPipeDownloader? = null
        fun getInstance(): NewPipeDownloader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NewPipeDownloader(
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                ).also { INSTANCE = it }
            }
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
        val reqBuilder = okhttp3.Request.Builder().url(request.url())
        // Apply headers
        for ((key, values) in request.headers()) {
            for (value in values) {
                reqBuilder.addHeader(key, value)
            }
        }
        // Ensure User-Agent if not set
        if (!request.headers().containsKey("User-Agent")) {
            reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        }

        when (request.httpMethod()) {
            "GET" -> reqBuilder.get()
            "HEAD" -> reqBuilder.head()
            "POST" -> {
                val data = request.dataToSend()
                val body = if (data != null) data.toRequestBody(null) else ByteArray(0).toRequestBody(null)
                reqBuilder.post(body)
            }
            else -> reqBuilder.get()
        }

        val response = client.newCall(reqBuilder.build()).execute()
        val body = try { response.body?.string() ?: "" } catch (_: Exception) { "" }
        val headersMap = mutableMapOf<String, List<String>>()
        for ((name, values) in response.headers.toMultimap()) {
            headersMap[name] = values
        }
        val code = response.code
        val message = response.message
        val latestUrl = response.request.url.toString()
        response.close()
        return org.schabi.newpipe.extractor.downloader.Response(code, message, headersMap, body, latestUrl)
    }
}
