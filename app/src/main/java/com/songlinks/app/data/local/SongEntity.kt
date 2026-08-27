package com.songlinks.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "saved_songs", indices = [Index(value = ["songId"], unique = true)])
data class SongEntity(
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
    val savedAt: Long = System.currentTimeMillis()
)
