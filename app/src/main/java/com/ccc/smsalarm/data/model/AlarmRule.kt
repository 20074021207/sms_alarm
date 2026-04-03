package com.ccc.smsalarm.data.model

data class AlarmRule(
    val id: Long = 0,
    val keywords: List<String>,
    val matchMode: MatchMode,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
