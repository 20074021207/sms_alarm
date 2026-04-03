package com.ccc.smsalarm.data.db

import androidx.room.TypeConverter
import com.ccc.smsalarm.data.model.MatchMode
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val array = JSONArray(value)
        return (0 until array.length()).map { array.getString(it) }
    }

    @TypeConverter
    fun fromMatchMode(mode: MatchMode): String = mode.name

    @TypeConverter
    fun toMatchMode(value: String): MatchMode = MatchMode.valueOf(value)
}
