package com.ccc.smsalarm.data.repository

import com.ccc.smsalarm.data.db.dao.AlarmRuleDao
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import com.ccc.smsalarm.data.model.MatchMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmRuleRepositoryTest {

    private class FakeDao : AlarmRuleDao {
        val rules = mutableListOf<AlarmRuleEntity>()
        private val flow = MutableStateFlow<List<AlarmRuleEntity>>(emptyList())

        override fun getAllRules(): Flow<List<AlarmRuleEntity>> = flow

        override suspend fun getEnabledRules(): List<AlarmRuleEntity> = rules.filter { it.enabled }

        override suspend fun insert(rule: AlarmRuleEntity): Long {
            rules.add(rule)
            return rules.size.toLong()
        }

        override suspend fun update(rule: AlarmRuleEntity) {}

        override suspend fun delete(rule: AlarmRuleEntity) {}
    }

    private fun rule(keywords: List<String>, mode: MatchMode = MatchMode.ANY, enabled: Boolean = true) =
        AlarmRuleEntity(id = 0, keywords = keywords, matchMode = mode, enabled = enabled)

    private val dao = FakeDao()
    private val repository = AlarmRuleRepository(dao)

    @Test
    fun `any mode matches single keyword`() = runBlocking {
        dao.rules += rule(listOf("告警"))
        assertEquals("告警", repository.matchSms("服务器CPU告警")!!.keywords.first())
    }

    @Test
    fun `matching is case insensitive`() = runBlocking {
        dao.rules += rule(listOf("critical"))
        assertEquals(1, repository.matchSms("SYSTEM Critical FAILURE")!!.keywords.size)
    }

    @Test
    fun `no match returns null`() = runBlocking {
        dao.rules += rule(listOf("告警"))
        assertNull(repository.matchSms("今天天气不错"))
    }

    @Test
    fun `all mode requires every keyword`() = runBlocking {
        dao.rules += rule(listOf("服务器", "宕机"), MatchMode.ALL)
        assertNull(repository.matchSms("服务器CPU过高"))
        assertEquals(2, repository.matchSms("服务器宕机了快修")!!.keywords.size)
    }

    @Test
    fun `empty keyword list never matches`() = runBlocking {
        dao.rules += rule(emptyList(), MatchMode.ALL)
        assertNull(repository.matchSms("任何内容"))
    }

    @Test
    fun `disabled rules are ignored`() = runBlocking {
        dao.rules += rule(listOf("告警"), enabled = false)
        assertNull(repository.matchSms("告警报警"))
    }

    @Test
    fun `first matching rule wins in order`() = runBlocking {
        dao.rules += rule(listOf("宕机"))
        dao.rules += rule(listOf("服务器"))
        val matched = repository.matchSms("服务器宕机")
        assertEquals(listOf("宕机"), matched!!.keywords)
    }
}
