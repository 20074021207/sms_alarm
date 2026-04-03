package com.ccc.smsalarm.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ccc.smsalarm.data.db.dao.AlarmRuleDao
import com.ccc.smsalarm.data.db.dao.MatchedSmsDao
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import com.ccc.smsalarm.data.db.entity.MatchedSmsEntity

@Database(
    entities = [AlarmRuleEntity::class, MatchedSmsEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmRuleDao(): AlarmRuleDao
    abstract fun matchedSmsDao(): MatchedSmsDao
}
