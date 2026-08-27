package com.songlinks.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.songlinks.app.ui.navigation.SongLinksNavGraph
import com.songlinks.app.ui.theme.SongLinksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SongLinksTheme {
                SongLinksNavGraph()
            }
        }
    }
}
