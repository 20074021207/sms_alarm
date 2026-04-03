package com.ccc.smsalarm.data.model

data class MatchedSms(
    val id: Long = 0,
    val sender: String,
    val body: String,
    val matchedKeywords: List<String>,
    val matchedRuleId: Long,
    val receivedAt: Long = System.currentTimeMillis()
)
