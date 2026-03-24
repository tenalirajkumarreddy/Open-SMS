package dev.opensms.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "opensms_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("opensms_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIGURED, value).apply()

    var wsUrl: String
        get() = prefs.getString(KEY_WS_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WS_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: generateAndSaveDeviceId()
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var notifyOnFailure: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_FAILURE, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_FAILURE, value).apply()

    var smsPerMinute: Int
        get() = prefs.getInt(KEY_SMS_PER_MINUTE, 10)
        set(value) = prefs.edit().putInt(KEY_SMS_PER_MINUTE, value).apply()

    fun backendDomain(): String {
        if (wsUrl.isBlank()) return ""
        return runCatching {
            wsUrl.removePrefix("wss://").removePrefix("ws://").substringBefore("/")
        }.getOrElse { wsUrl }
    }

    fun disconnect() {
        prefs.edit()
            .putBoolean(KEY_CONFIGURED, false)
            .putString(KEY_WS_URL, "")
            .putString(KEY_API_KEY, "")
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun generateAndSaveDeviceId(): String {
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    companion object {
        private const val KEY_CONFIGURED    = "configured"
        private const val KEY_WS_URL        = "ws_url"
        private const val KEY_API_KEY       = "api_key"
        private const val KEY_DEVICE_ID     = "device_id"
        private const val KEY_AUTO_START    = "auto_start"
        private const val KEY_NOTIFY_FAILURE = "notify_failure"
        private const val KEY_SMS_PER_MINUTE = "sms_per_minute"
    }
}
