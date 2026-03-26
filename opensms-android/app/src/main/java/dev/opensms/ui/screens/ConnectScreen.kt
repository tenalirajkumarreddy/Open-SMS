package dev.opensms.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.opensms.qr.QRScannerView
import dev.opensms.ui.theme.OpenSMSColors
import dev.opensms.ui.viewmodel.MainViewModel
import org.json.JSONObject

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    vm: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    var supabaseUrl  by remember { mutableStateOf("") }
    var anonKey      by remember { mutableStateOf("") }
    var showKey      by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }
    var showCamera   by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) showCamera = true
        else errorMsg = "Camera permission needed for QR scan"
    }

    fun handleQRDecoded(raw: String) {
        showCamera = false
        try {
            val json = JSONObject(raw)
            supabaseUrl = json.optString("supabaseUrl").ifBlank { json.optString("supabase_url") }
            anonKey     = json.optString("anonKey").ifBlank { json.optString("anon_key") }
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                errorMsg = "QR code missing supabaseUrl or anonKey"
            }
        } catch (e: Exception) {
            errorMsg = "Invalid QR code format"
        }
    }

    fun doConnect() {
        errorMsg = null
        if (!supabaseUrl.startsWith("https://")) {
            errorMsg = "URL must start with https://"
            return
        }
        if (anonKey.isBlank()) {
            errorMsg = "Anon key cannot be empty"
            return
        }
        isConnecting = true
        requestBatteryOptimizationExclusion(context)
        vm.connect(supabaseUrl, anonKey)
        isConnecting = false
        onConnected()
    }

    if (showCamera && hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().background(OpenSMSColors.bg)) {
            QRScannerView(
                modifier = Modifier.fillMaxSize(),
                onQRDetected = ::handleQRDecoded,
            )
            IconButton(
                onClick = { showCamera = false },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = OpenSMSColors.text)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(OpenSMSColors.bg.copy(alpha = 0.88f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Point camera at the QR code\nfrom your Supabase project",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenSMSColors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(OpenSMSColors.accentDim)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Sms,
                contentDescription = null,
                tint = OpenSMSColors.accent,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "OpenSMS",
            style = MaterialTheme.typography.headlineLarge,
            color = OpenSMSColors.text,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Supabase-powered SMS gateway",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenSMSColors.muted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 32.dp),
            textAlign = TextAlign.Center,
        )

        Text(
            "Connect to Supabase",
            style = MaterialTheme.typography.titleMedium,
            color = OpenSMSColors.text,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = supabaseUrl,
            onValueChange = { supabaseUrl = it; errorMsg = null },
            label = { Text("Project URL") },
            placeholder = { Text("https://xxxx.supabase.co") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = OpenSMSColors.muted) },
            colors = connectFieldColors(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = anonKey,
            onValueChange = { anonKey = it; errorMsg = null },
            label = { Text("Anon Key") },
            placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showKey) "Hide" else "Show",
                        tint = OpenSMSColors.muted,
                    )
                }
            },
            colors = connectFieldColors(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                if (hasCameraPermission) showCamera = true
                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OpenSMSColors.accent),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan QR instead")
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = errorMsg != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = OpenSMSColors.redDim,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = OpenSMSColors.red, modifier = Modifier.size(18.dp))
                    Text(errorMsg ?: "", style = MaterialTheme.typography.bodyMedium, color = OpenSMSColors.red)
                }
            }
        }

        Button(
            onClick = { doConnect() },
            enabled = supabaseUrl.isNotBlank() && anonKey.isNotBlank() && !isConnecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OpenSMSColors.accent),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = OpenSMSColors.bg,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OpenSMSColors.bg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isConnecting) "Starting…" else "Start Gateway",
                color = OpenSMSColors.bg,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = OpenSMSColors.surface,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("How to connect", style = MaterialTheme.typography.labelSmall, color = OpenSMSColors.accent)
                Text(
                    "1. Create an sms_jobs table in your Supabase project\n" +
                    "2. Enable Realtime on the table\n" +
                    "3. Paste your Project URL and Anon Key above\n" +
                    "4. Tap Start Gateway — then INSERT rows to send SMS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenSMSColors.muted,
                )
            }
        }
    }
}

private fun requestBatteryOptimizationExclusion(context: Context) {
    try {
        val pm = context.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    } catch (_: Exception) {}
}

@Composable
private fun connectFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = OpenSMSColors.accent,
    unfocusedBorderColor = OpenSMSColors.border,
    focusedTextColor     = OpenSMSColors.text,
    unfocusedTextColor   = OpenSMSColors.text,
    cursorColor          = OpenSMSColors.accent,
    focusedLabelColor    = OpenSMSColors.accent,
    unfocusedLabelColor  = OpenSMSColors.muted,
)
