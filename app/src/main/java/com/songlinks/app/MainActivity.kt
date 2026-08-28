package com.songlinks.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.songlinks.app.player.PlayerService
import com.songlinks.app.ui.navigation.SongLinksNavGraph
import com.songlinks.app.ui.theme.SongLinksTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val playerService = mutableStateOf<PlayerService?>(null)
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as? PlayerService.PlayerBinder
            if (binder != null) {
                playerService.value = binder.getService()
                bound = true
                Log.d(TAG, "Service bound")
            } else {
                Log.e(TAG, "Service connected with wrong binder")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playerService.value = null
            bound = false
            Log.d(TAG, "Service unbound")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "POST_NOTIFICATIONS permission granted: $isGranted")
    }

    val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportUri?.invoke(uri)
            onExportUri = null
        }
    }

    val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri?.invoke(uri)
            onImportUri = null
        }
    }

    var onExportUri: ((Uri) -> Unit)? = null
    var onImportUri: ((Uri) -> Unit)? = null

    fun launchExport(onResult: (Uri) -> Unit) {
        onExportUri = onResult
        exportLauncher.launch("songlinks_backup.backup")
    }

    fun launchImport(onResult: (Uri) -> Unit) {
        onImportUri = onResult
        importLauncher.launch(arrayOf("application/octet-stream", "application/json", "text/plain", "*/*"))
    }

    private val darkThemeState = mutableStateOf(true)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "dark_theme") {
            val prefs = getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
            darkThemeState.value = prefs.getBoolean("dark_theme", true)
            Log.d(TAG, "Dark theme changed: ${darkThemeState.value}")
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        val prefs = getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
        darkThemeState.value = prefs.getBoolean("dark_theme", true)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        setContent {
            SongLinksTheme(darkTheme = darkThemeState.value) {
                SongLinksNavGraph(playerService = playerService.value, activity = this@MainActivity)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = getSharedPreferences("songlinks_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (bound) {
            unbindService(connection)
            bound = false
        }
        Log.d(TAG, "onDestroy")
    }
}
