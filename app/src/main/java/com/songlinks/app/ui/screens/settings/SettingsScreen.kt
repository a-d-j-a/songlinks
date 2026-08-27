package com.songlinks.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.songlinks.app.ui.theme.Accent
import com.songlinks.app.ui.theme.Card as ThemeCard
import com.songlinks.app.ui.theme.CardBorder
import com.songlinks.app.ui.theme.OnSurfaceVariant
import com.songlinks.app.ui.theme.Surface
import com.songlinks.app.ui.theme.SurfaceVariant
import com.songlinks.app.ui.theme.TextPrimary
import com.songlinks.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionResult by viewModel.connectionResult.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        }

        item {
            SettingsSection(title = "SERVER") {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    label = { Text("Server URL") },
                    leadingIcon = {
                        Icon(Icons.Filled.Dns, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = OnSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = !isTestingConnection,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent
                    )
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isTestingConnection) "Testing..." else "Test Connection",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                AnimatedVisibility(visible = connectionResult != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (connectionResult == true)
                                Color(0xFF1B5E20).copy(alpha = 0.2f)
                            else
                                Color(0xFFB71C1C).copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (connectionResult == true)
                                    Icons.Filled.CheckCircle
                                else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (connectionResult == true) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (connectionResult == true) "Connected!" else "Connection failed",
                                color = if (connectionResult == true) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "APPEARANCE") {
                SettingsSwitch(
                    title = "Dark Mode",
                    subtitle = "Currently only dark theme supported",
                    icon = Icons.Filled.DarkMode,
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.toggleTheme() }
                )
            }
        }

        item {
            SettingsSection(title = "AUDIO") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.HighQuality,
                            contentDescription = null,
                            tint = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Audio Quality",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "auto" to "Auto",
                            "low" to "Low",
                            "mid" to "Mid",
                            "high" to "High"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = audioQuality == value,
                                onClick = { viewModel.updateAudioQuality(value) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSwitch(
                    title = "Crossfade",
                    subtitle = "Smooth transition between songs",
                    icon = Icons.Filled.MergeType,
                    checked = crossfadeEnabled,
                    onCheckedChange = { viewModel.toggleCrossfade() }
                )

                AnimatedVisibility(visible = crossfadeEnabled) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Duration: ${crossfadeDuration.toInt()}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Slider(
                            value = crossfadeDuration,
                            onValueChange = { viewModel.updateCrossfadeDuration(it) },
                            valueRange = 1f..12f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = Accent,
                                activeTrackColor = Accent,
                                inactiveTrackColor = CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = "ABOUT") {
                SettingsInfoRow(
                    icon = Icons.Filled.Info,
                    title = "Version",
                    value = "1.0.0"
                )
                SettingsInfoRow(
                    icon = Icons.Filled.Code,
                    title = "GitHub",
                    value = "songlinks"
                )
            }
        }

        item {
            SettingsSection(title = "DANGER ZONE") {
                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear History")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data")
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear your play history?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("This will reset all settings and data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearAllDialog = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Accent,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCard),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.3f),
                uncheckedThumbColor = OnSurfaceVariant,
                uncheckedTrackColor = CardBorder
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
