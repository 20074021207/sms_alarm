package com.ccc.smsalarm.util

/**
 * Keyword parsing shared by the rule editor and matching flow. Chinese input
 * methods produce full-width separators by default, so only accepting ","
 * silently turned "A，B" into a single keyword.
 */
object Keywords {

    private val SEPARATORS = Regex("[,，;；、]")

    fun parse(input: String): List<String> =
        input.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
}
