package com.songlinks.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val serviceIntent = Intent(context, PlayerService::class.java).apply {
            this.action = action
            putExtras(intent)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerReceiver", "start service failed", e)
        }
    }
}
