package com.songlinks.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.songlinks.app.data.local.AppDatabase

class SongLinksApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val prefs: SharedPreferences by lazy {
        getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
    }
}
