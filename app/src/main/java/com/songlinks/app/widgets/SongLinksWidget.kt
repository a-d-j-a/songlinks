package com.songlinks.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.songlinks.app.MainActivity
import com.songlinks.app.R

class SongLinksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val prefs = context.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
            val lastTitle = prefs.getString("widget_last_title", "SongLinks") ?: "SongLinks"
            val lastArtist = prefs.getString("widget_last_artist", "Tap to play") ?: "Tap to play"
            val views = RemoteViews(context.packageName, R.layout.widget_songlinks)
            views.setTextViewText(R.id.widget_title, lastTitle)
            views.setTextViewText(R.id.widget_artist, lastArtist)
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    companion object {
        fun update(context: Context, title: String, artist: String) {
            val prefs = context.getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("widget_last_title", title).putString("widget_last_artist", artist).apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, SongLinksWidget::class.java))
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_songlinks)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
