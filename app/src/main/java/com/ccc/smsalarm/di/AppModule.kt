package com.ccc.smsalarm.di

import android.content.Context
import androidx.room.Room
import com.ccc.smsalarm.data.db.AppDatabase
import com.ccc.smsalarm.data.db.dao.AlarmRuleDao
import com.ccc.smsalarm.data.db.dao.MatchedSmsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sms_alarm_db"
        ).build()
    }

    @Provides
    fun provideAlarmRuleDao(db: AppDatabase): AlarmRuleDao = db.alarmRuleDao()

    @Provides
    fun provideMatchedSmsDao(db: AppDatabase): MatchedSmsDao = db.matchedSmsDao()
}
