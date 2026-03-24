package dev.opensms.websocket

import dev.opensms.queue.SmsJob
import dev.opensms.templates.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

class RelayClient(
    private val wsUrl: String,
    private val apiKey: String,
    private val deviceId: String,
    private val templateRepo: TemplateRepository,
    private val scope: CoroutineScope,
    val onJob: (SmsJob) -> Unit,
    val onStatusChanged: (ConnectionStatus) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectDelay = INITIAL_DELAY_MS
    private val isDestroyed = AtomicBoolean(false)

    fun connect() {
        if (isDestroyed.get()) return
        onStatusChanged(ConnectionStatus.CONNECTING)
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, Listener())
    }

    fun sendStatus(messageId: String, status: String, error: String? = null) {
        val msg = MessageParser.encode(
            StatusMessage(
                messageId = messageId,
                status = status,
                error = error,
                timestamp = Instant.now().toString(),
            )
        )
        webSocket?.send(msg)
    }

    fun reconnectNow() {
        reconnectDelay = INITIAL_DELAY_MS
        webSocket?.cancel()
        connect()
    }

    fun destroy() {
        isDestroyed.set(true)
        webSocket?.cancel()
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    private fun scheduleReconnect() {
        if (isDestroyed.get()) return
        onStatusChanged(ConnectionStatus.RECONNECTING)
        scope.launch {
            delay(reconnectDelay)
            reconnectDelay = minOf(reconnectDelay * 2, MAX_DELAY_MS)
            connect()
        }
    }

    private inner class Listener : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            reconnectDelay = INITIAL_DELAY_MS

            ws.send(MessageParser.encode(
                AuthMessage(apiKey = apiKey, deviceId = deviceId)
            ))

            val syncItems = templateRepo.getAll().map { t ->
                TemplateSyncItem(
                    name = t.name,
                    body = t.body,
                    vars = t.extractVars(),
                )
            }
            ws.send(MessageParser.encode(TemplatesSyncMessage(templates = syncItems)))
        }

        override fun onMessage(ws: WebSocket, text: String) {
            when (MessageParser.parseType(text)) {
                "auth_ok" -> onStatusChanged(ConnectionStatus.CONNECTED)
                "job"     -> MessageParser.parseJob(text)?.let { job ->
                    onJob(SmsJob(
                        messageId    = job.messageId,
                        to           = job.to,
                        body         = job.body,
                        templateName = job.template,
                    ))
                }
                "ping"    -> ws.send(MessageParser.encode(PongMessage()))
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            scheduleReconnect()
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            if (!isDestroyed.get()) scheduleReconnect()
            else onStatusChanged(ConnectionStatus.DISCONNECTED)
        }
    }

    companion object {
        private const val INITIAL_DELAY_MS = 2_000L
        private const val MAX_DELAY_MS     = 60_000L
    }
}

private fun dev.opensms.templates.Template.extractVars(): List<String> {
    val regex = Regex("\\{\\{(\\w+)\\}\\}")
    return regex.findAll(body).map { it.groupValues[1] }.toList()
}
