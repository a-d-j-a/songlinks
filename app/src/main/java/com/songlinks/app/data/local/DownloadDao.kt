package com.songlinks.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("SELECT * FROM downloads WHERE songId = :songId LIMIT 1")
    suspend fun getBySongId(songId: String): DownloadEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE songId = :songId)")
    suspend fun isDownloaded(songId: String): Boolean

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM downloads")
    suspend fun getTotalSize(): Long

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
