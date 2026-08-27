package com.songlinks.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM saved_songs ORDER BY savedAt DESC")
    fun getAllSaved(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity)

    @Query("DELETE FROM saved_songs WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_songs WHERE songId = :songId)")
    suspend fun isSaved(songId: String): Boolean

    @Query("DELETE FROM saved_songs")
    suspend fun deleteAll()
}
