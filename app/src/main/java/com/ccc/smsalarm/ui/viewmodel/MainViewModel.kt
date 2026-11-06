package com.ccc.smsalarm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import com.ccc.smsalarm.data.db.entity.MatchedSmsEntity
import com.ccc.smsalarm.data.model.AppSettings
import com.ccc.smsalarm.data.model.MatchMode
import com.ccc.smsalarm.data.repository.AlarmRuleRepository
import com.ccc.smsalarm.data.repository.MatchedSmsRepository
import com.ccc.smsalarm.data.repository.SettingsRepository
import com.ccc.smsalarm.service.AlarmState
import com.ccc.smsalarm.util.AudioHelper
import com.ccc.smsalarm.util.FlashlightHelper
import com.ccc.smsalarm.util.VibrationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val alarmRuleRepository: AlarmRuleRepository,
    private val matchedSmsRepository: MatchedSmsRepository,
    private val settingsRepository: SettingsRepository,
    private val audioHelper: AudioHelper,
    private val vibrationHelper: VibrationHelper,
    private val flashlightHelper: FlashlightHelper
) : ViewModel() {

    val smsList: StateFlow<List<MatchedSmsEntity>> = matchedSmsRepository.getAllSms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<AlarmRuleEntity>> = alarmRuleRepository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** True while the config-tab test preview (ringtone/vibration/flashlight) runs. */
    private val _alarmTesting = MutableStateFlow(false)
    val alarmTesting: StateFlow<Boolean> = _alarmTesting.asStateFlow()

    private var testJob: Job? = null

    fun addRule(keywords: List<String>, matchMode: MatchMode) {
        viewModelScope.launch {
            alarmRuleRepository.addRule(keywords, matchMode)
        }
    }

    fun deleteRule(rule: AlarmRuleEntity) {
        viewModelScope.launch {
            alarmRuleRepository.deleteRule(rule)
        }
    }

    fun toggleRule(rule: AlarmRuleEntity) {
        viewModelScope.launch {
            alarmRuleRepository.updateRule(rule.copy(enabled = !rule.enabled))
        }
    }

    fun updateRule(rule: AlarmRuleEntity, keywords: List<String>, matchMode: MatchMode) {
        viewModelScope.launch {
            alarmRuleRepository.updateRule(rule.copy(keywords = keywords, matchMode = matchMode))
        }
    }

    fun updateVolume(volume: Int) {
        viewModelScope.launch { settingsRepository.updateVolume(volume) }
    }

    fun updateVibration(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateVibration(enabled) }
    }

    fun updateFlashlight(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateFlashlight(enabled) }
    }

    fun updateMonitoring(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMonitoring(enabled) }
    }

    fun updateRingtone(uri: String) {
        viewModelScope.launch { settingsRepository.updateRingtone(uri) }
    }

    fun deleteSms(id: Long) {
        viewModelScope.launch { matchedSmsRepository.deleteSms(id) }
    }

    fun deleteSmsByIds(ids: List<Long>) {
        viewModelScope.launch { matchedSmsRepository.deleteByIds(ids) }
    }

    fun deleteAllSms() {
        viewModelScope.launch { matchedSmsRepository.deleteAll() }
    }

    /**
     * Preview the alarm effects with the current settings for [TEST_DURATION_MS].
     * Drives the helpers directly (no AlarmService, no notification, no record)
     * so the user can verify sound/vibration/flashlight without a real SMS.
     * Refuses while a real alarm is running — the helpers are singletons and a
     * preview would stop the actual alarm's sound.
     */
    fun startAlarmTest() {
        if (_alarmTesting.value || AlarmState.state.value.active) return
        _alarmTesting.value = true
        testJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            audioHelper.startAlarm(settings.alarmVolume, settings.alarmRingtoneUri)
            if (settings.enableVibration) vibrationHelper.startVibration()
            if (settings.enableFlashlight) flashlightHelper.startFlashlight(viewModelScope)
            delay(TEST_DURATION_MS)
            stopAlarmTest()
        }
    }

    fun stopAlarmTest() {
        testJob?.cancel()
        testJob = null
        // Explicit stops: cancelling testJob does not stop the flashlight loop
        // (it runs on its own job) nor the MediaPlayer.
        audioHelper.stopAlarm()
        vibrationHelper.stopVibration()
        flashlightHelper.stopFlashlight()
        _alarmTesting.value = false
    }

    override fun onCleared() {
        // Insurance: never leave the torch on / sound looping after the UI dies.
        runCatching {
            audioHelper.stopAlarm()
            vibrationHelper.stopVibration()
            flashlightHelper.stopFlashlight()
        }
        super.onCleared()
    }

    companion object {
        private const val TEST_DURATION_MS = 10_000L
    }
}
