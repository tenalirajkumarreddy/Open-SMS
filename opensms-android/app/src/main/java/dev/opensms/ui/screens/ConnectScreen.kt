package dev.opensms.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.opensms.qr.QRScannerView
import dev.opensms.ui.theme.OpenSMSColors
import dev.opensms.ui.viewmodel.MainViewModel
import dev.opensms.websocket.MessageParser
import dev.opensms.websocket.QRPayload

// States: Idle → Scanning → Decoded → Connecting → (navigate to Dashboard)
private enum class ConnectState { IDLE, SCANNING, DECODED, CONNECTING, ERROR }

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    vm: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    var state         by remember { mutableStateOf(ConnectState.IDLE) }
    var errorMsg      by remember { mutableStateOf("") }
    var showManual    by remember { mutableStateOf(false) }
    var decodedPayload by remember { mutableStateOf<QRPayload?>(null) }
    var manualUrl     by remember { mutableStateOf("ws://") }
    var manualKey     by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) state = ConnectState.SCANNING
        else {
            errorMsg = "Camera permission is required to scan the QR code."
            state = ConnectState.ERROR
        }
    }

    fun doConnect(wsUrl: String, apiKey: String) {
        state = ConnectState.CONNECTING
        vm.connectFromQR(wsUrl, apiKey)
        onConnected()
    }

    fun handleQRResult(raw: String) {
        val payload = MessageParser.parseQRPayload(raw)
        if (payload == null) {
            errorMsg = "Invalid QR. Scan the QR that appears in your terminal after running node server.js"
            state = ConnectState.ERROR
            return
        }
        decodedPayload = payload
        state = ConnectState.DECODED
    }

    fun handleManualConnect() {
        if (!manualUrl.startsWith("wss://") && !manualUrl.startsWith("ws://")) {
            errorMsg = "Backend URL must start with ws:// or wss://"
            state = ConnectState.ERROR
            return
        }
        if (manualKey.isBlank()) {
            errorMsg = "API key cannot be empty."
            state = ConnectState.ERROR
            return
        }
        doConnect(manualUrl.trim(), manualKey.trim())
    }

    Box(
        modifier = Modifier.fillMaxSize().background(OpenSMSColors.bg),
    ) {
        when (state) {

            ConnectState.SCANNING -> {
                if (hasCameraPermission) {
                    QRScannerView(
                        modifier = Modifier.fillMaxSize(),
                        onQRDetected = ::handleQRResult,
                    )
                    IconButton(
                        onClick = { state = ConnectState.IDLE },
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = OpenSMSColors.text)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(OpenSMSColors.bg.copy(alpha = 0.85f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Point camera at the QR code in your terminal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OpenSMSColors.text,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Run: node server.js  and scan the printed QR",
                                style = MaterialTheme.typography.labelSmall,
                                color = OpenSMSColors.muted,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            ConnectState.DECODED -> {
                val payload = decodedPayload!!
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(OpenSMSColors.accentDim),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = OpenSMSColors.accent,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Text("QR Scanned", style = MaterialTheme.typography.headlineSmall, color = OpenSMSColors.text)
                        Text(
                            "Ready to connect to your backend",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OpenSMSColors.muted,
                            textAlign = TextAlign.Center,
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = OpenSMSColors.surface,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("URL", style = MaterialTheme.typography.labelSmall, color = OpenSMSColors.muted)
                                    Text(
                                        payload.wsUrl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OpenSMSColors.accent,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Key", style = MaterialTheme.typography.labelSmall, color = OpenSMSColors.muted)
                                    Text(
                                        "${payload.apiKey.take(8)}…${payload.apiKey.takeLast(4)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OpenSMSColors.muted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { doConnect(payload.wsUrl, payload.apiKey) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OpenSMSColors.accent),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = OpenSMSColors.bg, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect Now", color = OpenSMSColors.bg, style = MaterialTheme.typography.titleMedium)
                        }

                        TextButton(onClick = { state = ConnectState.IDLE }) {
                            Text("Scan Different QR", color = OpenSMSColors.muted)
                        }
                    }
                }
            }

            ConnectState.CONNECTING -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(color = OpenSMSColors.accent, strokeWidth = 3.dp)
                        Text("Connecting…", style = MaterialTheme.typography.titleMedium, color = OpenSMSColors.text)
                        Text("Authenticating with your backend", style = MaterialTheme.typography.bodyMedium, color = OpenSMSColors.muted)
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))

                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(OpenSMSColors.accentDim),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = null,
                            tint = OpenSMSColors.accent,
                            modifier = Modifier.size(48.dp),
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    Text("OpenSMS", style = MaterialTheme.typography.headlineLarge, color = OpenSMSColors.text)
                    Text(
                        "Zero-infrastructure SMS gateway",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpenSMSColors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    Spacer(Modifier.height(36.dp))

                    if (state == ConnectState.ERROR) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = OpenSMSColors.redDim,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = OpenSMSColors.red, modifier = Modifier.size(18.dp))
                                Text(errorMsg, style = MaterialTheme.typography.bodyMedium, color = OpenSMSColors.red)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── How it works ──────────────────────────────────────────
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = OpenSMSColors.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("How to connect", style = MaterialTheme.typography.titleSmall, color = OpenSMSColors.accent)
                            StepRow("1", "Copy opensms.js into your project")
                            StepRow("2", "npm install ws qrcode-terminal")
                            StepRow("3", "OPENSMS_HOST=<your IP> node server.js")
                            StepRow("4", "Scan the QR that appears in your terminal")
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (hasCameraPermission) state = ConnectState.SCANNING
                            else permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OpenSMSColors.accent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = OpenSMSColors.bg, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Scan QR Code", color = OpenSMSColors.bg, style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(Modifier.height(16.dp))

                    TextButton(onClick = { showManual = !showManual }) {
                        Icon(
                            if (showManual) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = OpenSMSColors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (showManual) "Hide manual entry" else "Enter manually",
                            color = OpenSMSColors.muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    AnimatedVisibility(visible = showManual) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OpenSMSColors.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, OpenSMSColors.border, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Manual Connection", style = MaterialTheme.typography.titleSmall, color = OpenSMSColors.accent)

                            OutlinedTextField(
                                value = manualUrl,
                                onValueChange = { manualUrl = it },
                                label = { Text("WebSocket URL") },
                                placeholder = { Text("ws://192.168.1.10:3001/") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = connectTextFieldColors(),
                            )

                            OutlinedTextField(
                                value = manualKey,
                                onValueChange = { manualKey = it },
                                label = { Text("API Key") },
                                placeholder = { Text("Printed in your terminal") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = connectTextFieldColors(),
                            )

                            Button(
                                onClick = ::handleManualConnect,
                                enabled = manualUrl.isNotBlank() && manualKey.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = OpenSMSColors.indigo),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Connect", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun StepRow(num: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(OpenSMSColors.accentDim),
            contentAlignment = Alignment.Center,
        ) {
            Text(num, style = MaterialTheme.typography.labelSmall, color = OpenSMSColors.accent, fontSize = 11.sp)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = OpenSMSColors.muted,
            fontFamily = if (num == "2" || num == "3") FontFamily.Monospace else null,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun connectTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OpenSMSColors.accent,
    unfocusedBorderColor = OpenSMSColors.border,
    focusedTextColor = OpenSMSColors.text,
    unfocusedTextColor = OpenSMSColors.text,
    cursorColor = OpenSMSColors.accent,
    focusedLabelColor = OpenSMSColors.accent,
    unfocusedLabelColor = OpenSMSColors.muted,
)
