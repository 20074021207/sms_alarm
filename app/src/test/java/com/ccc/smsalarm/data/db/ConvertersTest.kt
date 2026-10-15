package com.ccc.smsalarm.data.db

import com.ccc.smsalarm.data.model.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `string list roundtrip`() {
        val original = listOf("告警", "紧急", "server down")
        assertEquals(original, converters.toStringList(converters.fromStringList(original)))
    }

    @Test
    fun `empty list roundtrip`() {
        val original = emptyList<String>()
        assertEquals(original, converters.toStringList(converters.fromStringList(original)))
    }

    @Test
    fun `keyword containing separator survives roundtrip`() {
        val original = listOf("a,b", "c")
        assertEquals(original, converters.toStringList(converters.fromStringList(original)))
    }

    @Test
    fun `match mode roundtrip`() {
        assertEquals(MatchMode.ANY, converters.toMatchMode(converters.fromMatchMode(MatchMode.ANY)))
        assertEquals(MatchMode.ALL, converters.toMatchMode(converters.fromMatchMode(MatchMode.ALL)))
    }

    @Test
    fun `unknown match mode falls back instead of crashing`() {
        assertEquals(MatchMode.ANY, converters.toMatchMode("SOMETHING_FUTURE"))
    }
}
