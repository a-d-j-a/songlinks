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

    suspend fun downloadSong(song: SongResult, streamUrl: String): DownloadEntity =
        withContext(Dispatchers.IO) {
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val extension = "mp3"
            val fileName = "${song.source}_${song.id}.$extension"
            val file = File(downloadsDir, fileName)

            val request = Request.Builder().url(streamUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }

            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
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

            downloadDao.insert(entity)
            entity
        }
}
