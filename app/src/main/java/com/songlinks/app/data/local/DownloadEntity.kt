package com.songlinks.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
    val page: String? = null,
    val duration: Int? = null,
    val filePath: String,
    val fileSize: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis()
)
