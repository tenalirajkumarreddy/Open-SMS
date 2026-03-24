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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.opensms.qr.QRScannerView
import dev.opensms.ui.theme.OpenSMSColors
import dev.opensms.ui.viewmodel.MainViewModel
import dev.opensms.websocket.MessageParser

private enum class ConnectState { IDLE, SCANNING, CONNECTING, ERROR }

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    vm: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    var state by remember { mutableStateOf(ConnectState.IDLE) }
    var errorMsg by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("wss://") }
    var manualKey by remember { mutableStateOf("") }
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
        else { errorMsg = "Camera permission is required to scan the QR code."; state = ConnectState.ERROR }
    }

    fun handleQRResult(raw: String) {
        state = ConnectState.CONNECTING
        val payload = MessageParser.parseQRPayload(raw)
        if (payload == null) {
            errorMsg = "Invalid QR code. Make sure you scan the QR from /opensms/qr on your backend."
            state = ConnectState.ERROR
            return
        }
        vm.connectFromQR(payload.wsUrl, payload.apiKey)
        onConnected()
    }

    fun handleManualConnect() {
        if (!manualUrl.startsWith("wss://") && !manualUrl.startsWith("ws://")) {
            errorMsg = "Backend URL must start with wss:// or ws://"
            state = ConnectState.ERROR
            return
        }
        if (manualKey.isBlank()) {
            errorMsg = "API key cannot be empty."
            state = ConnectState.ERROR
            return
        }
        state = ConnectState.CONNECTING
        vm.connectFromQR(manualUrl, manualKey)
        onConnected()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenSMSColors.bg),
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
                            .background(OpenSMSColors.bg.copy(alpha = 0.8f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Point camera at the QR code from\nyourapp.com/opensms/qr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OpenSMSColors.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            ConnectState.CONNECTING -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(color = OpenSMSColors.accent)
                        Text("Connecting…", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(40.dp))

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(OpenSMSColors.accentDim),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = null,
                            tint = OpenSMSColors.accent,
                            modifier = Modifier.size(44.dp),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Text("OpenSMS", style = MaterialTheme.typography.headlineLarge, color = OpenSMSColors.text)
                    Text(
                        "Connect your backend to start sending SMS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpenSMSColors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    Spacer(Modifier.height(40.dp))

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
                                label = { Text("Backend WebSocket URL") },
                                placeholder = { Text("wss://yourapp.com/opensms/ws") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = connectTextFieldColors(),
                            )

                            OutlinedTextField(
                                value = manualKey,
                                onValueChange = { manualKey = it },
                                label = { Text("API Key") },
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

                    Spacer(Modifier.height(40.dp))

                    Text(
                        "Visit yourapp.com/opensms/qr after adding\nOpenSMSServer to your backend.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpenSMSColors.muted2,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(40.dp))
                }
            }
        }
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
