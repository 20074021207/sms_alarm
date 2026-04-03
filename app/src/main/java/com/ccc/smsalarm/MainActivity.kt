package com.ccc.smsalarm

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import com.ccc.smsalarm.ui.screens.MainScreen
import com.ccc.smsalarm.ui.screens.SetupGuideScreen
import com.ccc.smsalarm.ui.theme.SmsAlarmTheme
import com.ccc.smsalarm.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "sms_alarm_prefs"
        private const val KEY_SETUP_DONE = "setup_done"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val setupDone = prefs.getBoolean(KEY_SETUP_DONE, false)

        setContent {
            SmsAlarmTheme {
                if (!setupDone) {
                    SetupGuideScreen {
                        prefs.edit { putBoolean(KEY_SETUP_DONE, true) }
                        setContent {
                            SmsAlarmTheme {
                                MainContent()
                            }
                        }
                    }
                } else {
                    MainContent()
                }
            }
        }
    }
}

@Composable
private fun MainContent() {
    val viewModel: MainViewModel = hiltViewModel()
    val smsList by viewModel.smsList.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val settings by viewModel.settings.collectAsState()

    MainScreen(
        smsList = smsList,
        rules = rules,
        settings = settings,
        onAddRule = { keywords, mode -> viewModel.addRule(keywords, mode) },
        onDeleteRule = { viewModel.deleteRule(it) },
        onToggleRule = { viewModel.toggleRule(it) },
        onVolumeChange = { viewModel.updateVolume(it) },
        onVibrationChange = { viewModel.updateVibration(it) },
        onFlashlightChange = { viewModel.updateFlashlight(it) }
    )
}
