package com.songlinks.app.ui.screens.tag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.songlinks.app.api.SongResult

@Composable
fun TagEditorScreen(song: SongResult, onSave: (SongResult) -> Unit = {}) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album ?: "") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Edit Tags — ${song.id}")
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        androidx.compose.material3.Button(onClick = { onSave(song.copy(title = title, artist = artist, album = album)) }, modifier = Modifier.padding(top = 12.dp)) { Text("Save") }
    }
}
