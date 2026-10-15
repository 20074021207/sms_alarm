package com.ccc.smsalarm.data.model

data class AppSettings(
    val monitoringEnabled: Boolean = true,
    val alarmVolume: Int = -1, // -1 means use max
    val enableVibration: Boolean = true,
    val enableFlashlight: Boolean = true
)
