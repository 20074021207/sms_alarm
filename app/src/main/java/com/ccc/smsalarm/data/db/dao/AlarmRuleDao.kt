package com.ccc.smsalarm.data.db.dao

import androidx.room.*
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmRuleDao {
    @Query("SELECT * FROM alarm_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<AlarmRuleEntity>>

    @Query("SELECT * FROM alarm_rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<AlarmRuleEntity>

    @Insert
    suspend fun insert(rule: AlarmRuleEntity): Long

    @Update
    suspend fun update(rule: AlarmRuleEntity)

    @Delete
    suspend fun delete(rule: AlarmRuleEntity)
}
