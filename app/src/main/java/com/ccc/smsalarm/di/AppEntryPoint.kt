package com.ccc.smsalarm.di

import com.ccc.smsalarm.data.repository.AlarmRuleRepository
import com.ccc.smsalarm.data.repository.MatchedSmsRepository
import com.ccc.smsalarm.data.repository.SettingsRepository

@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
@dagger.hilt.EntryPoint
interface AppEntryPoint {
    fun alarmRuleRepository(): AlarmRuleRepository
    fun matchedSmsRepository(): MatchedSmsRepository
    fun settingsRepository(): SettingsRepository
}
