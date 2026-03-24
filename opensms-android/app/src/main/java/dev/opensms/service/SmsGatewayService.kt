package dev.opensms.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.opensms.MainActivity
import dev.opensms.R
import dev.opensms.prefs.AppPreferences
import dev.opensms.queue.SmsJob
import dev.opensms.queue.TokenBucketRateLimiter
import dev.opensms.sms.SmsSender
import dev.opensms.state.MessageLog
import dev.opensms.state.MessageRecord
import dev.opensms.state.MessageStatus
import dev.opensms.state.StatsCounter
import dev.opensms.state.maskPhone
import dev.opensms.templates.TemplateRepository
import dev.opensms.websocket.ConnectionStatus
import dev.opensms.websocket.RelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class SmsGatewayService : Service() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var templateRepo: TemplateRepository
    @Inject lateinit var messageLog: MessageLog
    @Inject lateinit var stats: StatsCounter
    @Inject lateinit var smsSender: SmsSender

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var relayClient: RelayClient

    private val jobChannel = Channel<SmsJob>(
        capacity = 1000,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    companion object {
        const val NOTIF_ID   = 1001
        const val CHANNEL_ID = "opensms_gateway"

        var isRunning         = false
            private set
        var isPaused          = false
        var connectionStatus  = ConnectionStatus.DISCONNECTED
        var backendDomain     = ""
        val queueDepth        = AtomicInteger(0)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SmsGatewayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SmsGatewayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning    = true
        isPaused     = false
        backendDomain = prefs.backendDomain()
        queueDepth.set(0)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startRelayClient()
        launchQueueConsumer()
    }

    private fun startRelayClient() {
        relayClient = RelayClient(
            wsUrl        = prefs.wsUrl,
            apiKey       = prefs.apiKey,
            deviceId     = prefs.deviceId,
            templateRepo = templateRepo,
            scope        = scope,
            onJob        = { job ->
                queueDepth.incrementAndGet()
                jobChannel.trySend(job)
                updateNotification()
            },
            onStatusChanged = { status ->
                connectionStatus = status
                updateNotification()
            },
        )
        relayClient.connect()
    }

    private fun launchQueueConsumer() = scope.launch(Dispatchers.IO) {
        val rateLimiter = TokenBucketRateLimiter(prefs.smsPerMinute)

        for (job in jobChannel) {
            while (isPaused) { kotlinx.coroutines.delay(500) }

            queueDepth.decrementAndGet()
            updateNotification()

            val record = MessageRecord(
                messageId    = job.messageId,
                to           = job.to,
                toMasked     = maskPhone(job.to),
                templateName = job.templateName,
                body         = job.body,
                status       = MessageStatus.PROCESSING,
            )
            messageLog.add(record)

            rateLimiter.acquire()

            smsSender.send(
                job = job,
                onSent = {
                    messageLog.update(job.messageId, MessageStatus.SENT)
                    stats.incrementSent()
                    relayClient.sendStatus(job.messageId, "sent")
                    updateNotification()
                },
                onDelivered = {
                    messageLog.update(job.messageId, MessageStatus.DELIVERED)
                    relayClient.sendStatus(job.messageId, "delivered")
                    updateNotification()
                },
                onFailed = { reason ->
                    messageLog.update(job.messageId, MessageStatus.FAILED, reason)
                    stats.incrementFailed()
                    relayClient.sendStatus(job.messageId, "failed", reason)
                    updateNotification()
                    if (prefs.notifyOnFailure) notifyFailure(job.to, reason)
                },
            )
        }
    }

    override fun onDestroy() {
        isRunning        = false
        isPaused         = false
        connectionStatus = ConnectionStatus.DISCONNECTED
        queueDepth.set(0)
        relayClient.destroy()
        jobChannel.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val statusText = when (connectionStatus) {
            ConnectionStatus.CONNECTED    -> "Connected to $backendDomain"
            ConnectionStatus.RECONNECTING -> "Reconnecting to $backendDomain…"
            ConnectionStatus.CONNECTING   -> "Connecting to $backendDomain…"
            ConnectionStatus.DISCONNECTED -> "Disconnected"
        }
        val pausedSuffix = if (isPaused) " • PAUSED" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("● OpenSMS  •  ${stats.sentToday()} sent today$pausedSuffix")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_sms_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun notifyFailure(to: String, reason: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Failed")
            .setContentText("Failed to ${maskPhone(to)}: $reason")
            .setSmallIcon(R.drawable.ic_sms_notification)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "SMS Gateway", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "OpenSMS gateway service"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
