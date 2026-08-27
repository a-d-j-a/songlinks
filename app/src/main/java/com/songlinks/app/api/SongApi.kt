package com.songlinks.app.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SongApi"

class SongApi(private val context: Context) {

    suspend fun search(query: String, sources: Set<String>): List<SongResult> {
        Log.d(TAG, "search() query=$query, sources=$sources")
        return withContext(Dispatchers.IO) {
            DirectApi.search(query, sources)
        }
    }

    suspend fun resolveStreamUrl(songId: String): String {
        Log.d(TAG, "resolveStreamUrl() id=$songId")
        return withContext(Dispatchers.IO) {
            DirectApi.resolveStreamUrl(songId)
        }
    }

    suspend fun getLyrics(title: String, artist: String): LyricsResponse {
        Log.d(TAG, "getLyrics() title=$title, artist=$artist")
        return withContext(Dispatchers.IO) {
            DirectApi.getLyrics(title, artist)
        }
    }

    suspend fun getBackupData(): BackupData {
        Log.d(TAG, "getBackupData()")
        return BackupData()
    }

    suspend fun restoreBackup(data: BackupData): Boolean {
        Log.d(TAG, "restoreBackup()")
        return true
    }

    suspend fun checkHealth(): Boolean {
        Log.d(TAG, "checkHealth() — always true (no server)")
        return true
    }
}
