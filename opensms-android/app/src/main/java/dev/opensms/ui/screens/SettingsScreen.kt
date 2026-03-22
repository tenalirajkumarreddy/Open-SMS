package dev.opensms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.opensms.ui.theme.OpenSMSColors
import dev.opensms.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(navController: NavController, vm: MainViewModel = hiltViewModel()) {
    val prefs = vm.prefs
    val clipboard = LocalClipboardManager.current

    var port by remember { mutableStateOf(prefs.port.toString()) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var autoStart by remember { mutableStateOf(prefs.autoStart) }
    var notifyOnFailure by remember { mutableStateOf(prefs.notifyOnFailure) }
    var smsRateLimit by remember { mutableStateOf(prefs.smsPerMinute.toFloat()) }
    var webhookUrl by remember { mutableStateOf(prefs.webhookUrl) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Gateway") },
            text = { Text("This will clear all config and stop the service. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAll()
                    navController.navigate("setup") { popUpTo("dashboard") { inclusive = true } }
                }) { Text("Reset", color = OpenSMSColors.red) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
            containerColor = OpenSMSColors.surface,
        )
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("Regenerate API Key") },
            text = { Text("This will invalidate your current API key. All integrations will need to be updated.") },
            confirmButton = {
                TextButton(onClick = {
                    apiKey = vm.regenerateApiKey()
                    showRegenerateDialog = false
                }) { Text("Regenerate", color = OpenSMSColors.orange) }
            },
            dismissButton = { TextButton(onClick = { showRegenerateDialog = false }) { Text("Cancel") } },
            containerColor = OpenSMSColors.surface,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenSMSColors.bg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OpenSMSColors.muted)
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // Server Config
        SettingsSection("Server Config") {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("HTTP Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            // API Key row
            OutlinedTextField(
                value = apiKey,
                onValueChange = {},
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "Hide" else "Show", style = MaterialTheme.typography.labelSmall, color = OpenSMSColors.muted)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(apiKey)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = OpenSMSColors.muted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showRegenerateDialog = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = OpenSMSColors.orange, modifier = Modifier.size(18.dp))
                        }
                    }
                },
            )

            Button(
                onClick = {
                    prefs.port = port.toIntOrNull() ?: prefs.port
                    prefs.webhookUrl = webhookUrl
                    if (vm.isServiceRunning) { vm.stopGateway(); vm.startGateway() }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OpenSMSColors.indigo),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save & Restart Service") }
        }

        // Behaviour
        SettingsSection("Behaviour") {
            SettingToggleRow("Auto-start on Boot", autoStart) {
                autoStart = it; prefs.autoStart = it
            }
            SettingToggleRow("Notify on Failure", notifyOnFailure) {
                notifyOnFailure = it; prefs.notifyOnFailure = it
            }
            Column {
                Text("SMS Rate Limit: ${smsRateLimit.toInt()}/min", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = smsRateLimit,
                    onValueChange = { smsRateLimit = it },
                    onValueChangeFinished = { prefs.smsPerMinute = smsRateLimit.toInt() },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(thumbColor = OpenSMSColors.accent, activeTrackColor = OpenSMSColors.accent),
                )
            }
        }

        // Webhook
        SettingsSection("Webhook") {
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it },
                label = { Text("Webhook URL (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://yourapp.com/webhooks/sms") },
            )
        }

        // Danger zone
        SettingsSection("Danger Zone") {
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = OpenSMSColors.redDim),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset Gateway", color = OpenSMSColors.red)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = OpenSMSColors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OpenSMSColors.accent)
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = OpenSMSColors.bg, checkedTrackColor = OpenSMSColors.accent),
        )
    }
}
