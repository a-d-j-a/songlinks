package com.songlinks.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.songlinks.app.data.local.AppDatabase
import com.songlinks.app.util.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class SongLinksApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val prefs: SharedPreferences by lazy {
        getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        try {
            NewPipe.init(NewPipeDownloader.getInstance())
        } catch (e: Exception) {
            android.util.Log.e("SongLinksApp", "NewPipe init failed", e)
        }
    }
}
