package com.dero.juststudykorean.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): JustStudyDatabase =
        Room.databaseBuilder(context, JustStudyDatabase::class.java, "juststudy.db")
            .setDriver(BundledSQLiteDriver())
            .build()
}
