package com.tempmonitor.app.di

import android.content.Context
import androidx.room.Room
import com.tempmonitor.app.data.db.AppDatabase
import com.tempmonitor.app.data.db.TemperatureDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "temperature_monitor.db"
        ).build()

    @Provides
    fun provideTemperatureDao(db: AppDatabase): TemperatureDao = db.temperatureDao()
}
