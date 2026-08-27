package com.songlinks.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, PlayerService::class.java).apply {
            action = intent.action
        }
        context.startService(serviceIntent)
    }
}
