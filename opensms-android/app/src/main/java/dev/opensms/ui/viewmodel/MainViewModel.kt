package dev.opensms.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.opensms.prefs.AppPreferences
import dev.opensms.service.SmsGatewayService
import dev.opensms.state.MessageLog
import dev.opensms.state.MessageRecord
import dev.opensms.state.StatsCounter
import dev.opensms.templates.Template
import dev.opensms.templates.TemplateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    val prefs: AppPreferences,
    private val messageLog: MessageLog,
    private val stats: StatsCounter,
    private val templateRepo: TemplateRepository,
) : AndroidViewModel(application) {

    val isSetupComplete: Boolean get() = prefs.isSetupComplete

    var isServiceRunning by mutableStateOf(SmsGatewayService.isRunning)
        private set

    var recentMessages by mutableStateOf<List<MessageRecord>>(emptyList())
        private set

    var allMessages by mutableStateOf<List<MessageRecord>>(emptyList())
        private set

    var templates by mutableStateOf<List<Template>>(emptyList())
        private set

    var sentToday by mutableStateOf(0)
        private set

    var sentThisWeek by mutableStateOf(0)
        private set

    var failedTotal by mutableStateOf(0)
        private set

    init {
        startPolling()
        refreshTemplates()
    }

    private fun startPolling() = viewModelScope.launch {
        while (isActive) {
            isServiceRunning = SmsGatewayService.isRunning
            recentMessages = messageLog.getRecent(10)
            allMessages = messageLog.getAll()
            sentToday = stats.sentToday()
            sentThisWeek = stats.sentThisWeek()
            failedTotal = stats.failedTotal()
            delay(1000)
        }
    }

    fun refreshTemplates() {
        templates = templateRepo.getAll()
    }

    fun startGateway() {
        SmsGatewayService.start(getApplication())
        isServiceRunning = true
    }

    fun stopGateway() {
        SmsGatewayService.stop(getApplication())
        isServiceRunning = false
    }

    fun completeSetup(port: Int) {
        prefs.port = port
        prefs.isSetupComplete = true
        startGateway()
    }

    fun saveTemplate(template: Template) {
        templateRepo.save(template)
        refreshTemplates()
    }

    fun deleteTemplate(name: String) {
        templateRepo.delete(name)
        refreshTemplates()
    }

    fun clearLogs() {
        messageLog.clear()
        allMessages = emptyList()
        recentMessages = emptyList()
    }

    fun regenerateApiKey(): String = prefs.regenerateApiKey()

    fun resetAll() {
        stopGateway()
        messageLog.clear()
        prefs.reset()
    }
}
