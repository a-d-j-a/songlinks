package com.songlinks.app.data.local

import android.content.Context
import com.songlinks.app.api.SongResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class SongDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    private val downloadDao: DownloadDao
) {
    private val appContext = context.applicationContext

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
    }

    suspend fun downloadSong(song: SongResult, streamUrl: String): DownloadEntity =
        withContext(Dispatchers.IO) {
            // Ensure full stream, not preview: resolve via YouTube if preview/itunes
            val isPreview = song.quality.contains("preview", ignoreCase = true) || song.source.equals("itunes", ignoreCase = true)
            val finalUrl = if (isPreview || streamUrl.isBlank()) {
                try { com.songlinks.app.api.DirectApi.resolveStreamUrl(song.id, song.title, song.artist).takeIf { it.isNotBlank() } ?: streamUrl } catch (_: Exception) { streamUrl }
            } else streamUrl
            if (finalUrl.isBlank()) throw Exception("No playable stream for download")
            val downloadsDir = File(appContext.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                if (!downloadsDir.mkdirs() && !downloadsDir.exists()) {
                    throw Exception("Failed to create downloads directory")
                }
            }

            val safeId = sanitizeFileName(song.id)
            val safeSource = sanitizeFileName(song.source)
            val fileName = "${safeSource}_${safeId}.mp3"
            val file = File(downloadsDir, fileName)
            val tmpFile = File(downloadsDir, "$fileName.tmp")

            val request = Request.Builder().url(finalUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                throw Exception("Download failed: ${response.code}")
            }

            val body = response.body ?: run { response.close(); throw Exception("Empty response") }
            try {
                body.byteStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tmpFile.length() == 0L) {
                    tmpFile.delete()
                    throw Exception("Downloaded file is empty")
                }
                if (file.exists()) file.delete()
                if (!tmpFile.renameTo(file)) {
                    tmpFile.copyTo(file, overwrite = true)
                    tmpFile.delete()
                }
            } finally {
                response.close()
                if (tmpFile.exists() && tmpFile != file) tmpFile.delete()
            }

            val entity = DownloadEntity(
                source = song.source,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                cover = song.cover,
                page = song.page,
                duration = song.duration,
                filePath = file.absolutePath,
                fileSize = file.length()
            )

            try {
                downloadDao.insert(entity)
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            entity
        }
}
