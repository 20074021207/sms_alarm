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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val alarmRuleRepository: AlarmRuleRepository,
    private val matchedSmsRepository: MatchedSmsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val smsList: StateFlow<List<MatchedSmsEntity>> = matchedSmsRepository.getAllSms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<AlarmRuleEntity>> = alarmRuleRepository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

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
}
