package com.ccc.smsalarm.data.repository

import com.ccc.smsalarm.data.db.dao.AlarmRuleDao
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import com.ccc.smsalarm.data.model.MatchMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRuleRepository @Inject constructor(
    private val alarmRuleDao: AlarmRuleDao
) {
    fun getAllRules(): Flow<List<AlarmRuleEntity>> = alarmRuleDao.getAllRules()

    suspend fun getEnabledRules(): List<AlarmRuleEntity> = alarmRuleDao.getEnabledRules()

    suspend fun addRule(keywords: List<String>, matchMode: MatchMode): Long {
        return alarmRuleDao.insert(
            AlarmRuleEntity(keywords = keywords, matchMode = matchMode)
        )
    }

    suspend fun updateRule(rule: AlarmRuleEntity) = alarmRuleDao.update(rule)

    suspend fun deleteRule(rule: AlarmRuleEntity) = alarmRuleDao.delete(rule)

    suspend fun matchSms(body: String): AlarmRuleEntity? {
        val rules = getEnabledRules()
        for (rule in rules) {
            val matched = when (rule.matchMode) {
                MatchMode.ANY -> rule.keywords.any { body.contains(it, ignoreCase = true) }
                MatchMode.ALL -> rule.keywords.all { body.contains(it, ignoreCase = true) }
            }
            if (matched) return rule
        }
        return null
    }
}
