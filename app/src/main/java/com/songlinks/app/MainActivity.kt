package com.songlinks.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.songlinks.app.player.PlayerService
import com.songlinks.app.ui.navigation.SongLinksNavGraph
import com.songlinks.app.ui.theme.SongLinksTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private var playerService: PlayerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as PlayerService.PlayerBinder
            playerService = binder.getService()
            bound = true
            Log.d(TAG, "Service bound")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playerService = null
            bound = false
            Log.d(TAG, "Service unbound")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "POST_NOTIFICATIONS permission granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val serviceIntent = Intent(this, PlayerService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        val prefs = getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)

        setContent {
            SongLinksTheme(darkTheme = isDarkTheme) {
                SongLinksNavGraph(playerService = playerService)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        Log.d(TAG, "onDestroy")
    }
}
