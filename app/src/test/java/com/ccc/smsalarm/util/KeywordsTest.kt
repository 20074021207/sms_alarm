package com.ccc.smsalarm.util

import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordsTest {

    @Test
    fun `english comma separates`() {
        assertEquals(listOf("告警", "紧急"), Keywords.parse("告警,紧急"))
    }

    @Test
    fun `chinese comma separates`() {
        assertEquals(listOf("告警", "紧急"), Keywords.parse("告警，紧急"))
    }

    @Test
    fun `all common separators work`() {
        assertEquals(listOf("a", "b", "c", "d", "e"), Keywords.parse("a,b；c，d、e"))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(listOf("a", "b"), Keywords.parse("  a , b "))
    }

    @Test
    fun `empty segments are dropped`() {
        assertEquals(listOf("a", "b"), Keywords.parse("a,,，,b"))
    }

    @Test
    fun `blank input yields empty list`() {
        assertEquals(emptyList<String>(), Keywords.parse(""))
        assertEquals(emptyList<String>(), Keywords.parse("  "))
        assertEquals(emptyList<String>(), Keywords.parse("，，，"))
    }

    @Test
    fun `single keyword without separator`() {
        assertEquals(listOf("服务器"), Keywords.parse("服务器"))
    }
}
