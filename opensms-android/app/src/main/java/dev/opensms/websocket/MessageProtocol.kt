package dev.opensms.websocket

import com.google.gson.Gson
import com.google.gson.JsonParser

data class AuthMessage(
    val type: String = "auth",
    val apiKey: String,
    val deviceId: String,
)

data class AuthOkMessage(
    val type: String = "auth_ok",
)

data class TemplateSyncItem(
    val name: String,
    val body: String,
    val vars: List<String>,
)

data class TemplatesSyncMessage(
    val type: String = "templates_sync",
    val templates: List<TemplateSyncItem>,
)

data class JobMessage(
    val type: String = "job",
    val messageId: String,
    val to: String,
    val body: String,
    val template: String? = null,
)

data class StatusMessage(
    val type: String = "status",
    val messageId: String,
    val status: String,
    val error: String? = null,
    val timestamp: String,
)

data class PingMessage(val type: String = "ping")
data class PongMessage(val type: String = "pong")

data class QRPayload(
    val wsUrl: String,
    val apiKey: String,
    val deviceName: String = "OpenSMS Gateway",
)

object MessageParser {
    private val gson = Gson()

    fun encode(obj: Any): String = gson.toJson(obj)

    fun parseType(text: String): String? = runCatching {
        JsonParser.parseString(text).asJsonObject.get("type")?.asString
    }.getOrNull()

    fun parseJob(text: String): JobMessage? = runCatching {
        gson.fromJson(text, JobMessage::class.java)
    }.getOrNull()

    fun parseQRPayload(text: String): QRPayload? =
        // v4 format: base64-encoded JSON (from qrcode-terminal in the developer's terminal)
        runCatching {
            val decoded = String(android.util.Base64.decode(text.trim(), android.util.Base64.DEFAULT))
            val payload = gson.fromJson(decoded, QRPayload::class.java)
            if (payload.wsUrl.isNotBlank() && payload.apiKey.isNotBlank()) payload else null
        }.getOrNull()
        // v3 format: raw JSON (from /opensms/qr HTML page) — kept for backwards compatibility
        ?: runCatching {
            val payload = gson.fromJson(text, QRPayload::class.java)
            if (payload.wsUrl.isNotBlank() && payload.apiKey.isNotBlank()) payload else null
        }.getOrNull()
}
