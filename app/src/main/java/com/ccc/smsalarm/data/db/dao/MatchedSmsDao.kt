package com.ccc.smsalarm.data.db.dao

import androidx.room.*
import com.ccc.smsalarm.data.db.entity.MatchedSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchedSmsDao {
    @Query("SELECT * FROM matched_sms ORDER BY receivedAt DESC")
    fun getAllSms(): Flow<List<MatchedSmsEntity>>

    @Insert
    suspend fun insert(sms: MatchedSmsEntity): Long

    @Query("DELETE FROM matched_sms")
    suspend fun deleteAll()
}
