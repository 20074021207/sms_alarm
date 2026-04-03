package com.ccc.smsalarm.data.repository

import com.ccc.smsalarm.data.db.dao.MatchedSmsDao
import com.ccc.smsalarm.data.db.entity.MatchedSmsEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchedSmsRepository @Inject constructor(
    private val matchedSmsDao: MatchedSmsDao
) {
    fun getAllSms(): Flow<List<MatchedSmsEntity>> = matchedSmsDao.getAllSms()

    suspend fun insertSms(sender: String, body: String, matchedKeywords: List<String>, matchedRuleId: Long): Long {
        return matchedSmsDao.insert(
            MatchedSmsEntity(
                sender = sender,
                body = body,
                matchedKeywords = matchedKeywords,
                matchedRuleId = matchedRuleId
            )
        )
    }

    suspend fun deleteAll() = matchedSmsDao.deleteAll()
}
