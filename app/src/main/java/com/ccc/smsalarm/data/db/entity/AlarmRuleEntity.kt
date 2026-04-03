package com.ccc.smsalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ccc.smsalarm.data.model.MatchMode

@Entity(tableName = "alarm_rules")
data class AlarmRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keywords: List<String>,
    val matchMode: MatchMode,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
