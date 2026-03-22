package dev.opensms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.opensms.state.MessageRecord
import dev.opensms.state.MessageStatus
import dev.opensms.ui.theme.OpenSMSColors
import dev.opensms.ui.theme.statusColor
import dev.opensms.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

private val FILTER_TABS = listOf("All", "Delivered", "Sent", "Failed", "Pending")

@Composable
fun LogsScreen(navController: NavController, vm: MainViewModel = hiltViewModel()) {
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedRecord by remember { mutableStateOf<MessageRecord?>(null) }

    val filtered = vm.allMessages.filter { record ->
        when (selectedFilter) {
            "Delivered" -> record.status == MessageStatus.DELIVERED
            "Sent" -> record.status == MessageStatus.SENT
            "Failed" -> record.status == MessageStatus.FAILED
            "Pending", "Queued" -> record.status == MessageStatus.QUEUED || record.status == MessageStatus.PROCESSING
            else -> true
        }
    }

    if (selectedRecord != null) {
        MessageDetailDialog(record = selectedRecord!!, onDismiss = { selectedRecord = null })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenSMSColors.bg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OpenSMSColors.muted)
            }
            Text("Logs", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clearLogs() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = OpenSMSColors.muted)
            }
        }

        // Filter pills
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTER_TABS.forEach { tab ->
                val selected = tab == selectedFilter
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = tab },
                    label = { Text(tab, style = MaterialTheme.typography.bodyMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OpenSMSColors.accentDim,
                        selectedLabelColor = OpenSMSColors.accent,
                    ),
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { record ->
                    LogRow(record = record, onClick = { selectedRecord = record })
                }
            }
        }
    }
}

@Composable
private fun LogRow(record: MessageRecord, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = OpenSMSColors.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor(record.status.name)))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.toMasked, style = MaterialTheme.typography.bodyMedium)
                Text(
                    record.body.take(50) + if (record.body.length > 50) "…" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenSMSColors.muted2,
                )
            }
            Text(formatTimestamp(record.enqueuedAt), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MessageDetailDialog(record: MessageRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Detail") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("To", record.to)
                DetailRow("Status", record.status.name.lowercase())
                if (record.templateName != null) DetailRow("Template", record.templateName)
                DetailRow("Body", record.body)
                DetailRow("Queued", formatTimestamp(record.enqueuedAt))
                record.sentAt?.let { DetailRow("Sent", formatTimestamp(it)) }
                record.deliveredAt?.let { DetailRow("Delivered", formatTimestamp(it)) }
                record.errorReason?.let { DetailRow("Error", it) }
                DetailRow("Message ID", record.messageId)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = OpenSMSColors.surface,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(millis))
}
