package com.ccc.smsalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matched_sms")
data class MatchedSmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val matchedKeywords: List<String>,
    val matchedRuleId: Long,
    val receivedAt: Long = System.currentTimeMillis()
)
