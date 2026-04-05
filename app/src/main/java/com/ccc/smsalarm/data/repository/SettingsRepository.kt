package com.ccc.smsalarm.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ccc.smsalarm.data.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val ALARM_VOLUME = intPreferencesKey("alarm_volume")
        val ENABLE_VIBRATION = booleanPreferencesKey("enable_vibration")
        val ENABLE_FLASHLIGHT = booleanPreferencesKey("enable_flashlight")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            AppSettings(
                monitoringEnabled = prefs[Keys.MONITORING_ENABLED] ?: true,
                alarmVolume = prefs[Keys.ALARM_VOLUME] ?: -1,
                enableVibration = prefs[Keys.ENABLE_VIBRATION] ?: true,
                enableFlashlight = prefs[Keys.ENABLE_FLASHLIGHT] ?: true
            )
        }

    suspend fun updateVolume(volume: Int) {
        context.dataStore.edit { it[Keys.ALARM_VOLUME] = volume }
    }

    suspend fun updateVibration(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_VIBRATION] = enabled }
    }

    suspend fun updateFlashlight(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_FLASHLIGHT] = enabled }
    }

    suspend fun updateMonitoring(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    /**
     * Synchronous read for use in background components (Worker, BroadcastReceiver).
     */
    fun isMonitoringEnabledSync(): Boolean = runCatching {
        kotlinx.coroutines.runBlocking {
            context.dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[Keys.MONITORING_ENABLED] ?: true }
                .first()
        }
    }.getOrDefault(true)
}
