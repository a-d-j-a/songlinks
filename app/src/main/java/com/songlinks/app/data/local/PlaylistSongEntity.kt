package com.songlinks.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index(value = ["playlistId", "songId"], unique = true)]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val source: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
    val page: String? = null,
    val duration: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int = 0
)
