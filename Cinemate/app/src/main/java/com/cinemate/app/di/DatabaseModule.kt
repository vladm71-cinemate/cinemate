package com.cinemate.app.di

import android.content.Context
import androidx.room.Room
import com.cinemate.app.data.local.AppDatabase
import com.cinemate.app.data.local.DatabaseMigrations
import com.cinemate.app.data.local.dao.CacheDao
import com.cinemate.app.data.local.dao.FavoritesDao
import com.cinemate.app.data.local.dao.SearchHistoryDao
import com.cinemate.app.data.local.dao.TrackedSeriesDao
import com.cinemate.app.data.local.dao.UpcomingMoviesDao
import com.cinemate.app.data.local.dao.WatchedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "cinemate.db")
            // Без destructive migration: обновления не стирают данные.
            // Все переходы версий — через явные миграции (DatabaseMigrations).
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    @Provides fun provideFavoritesDao(db: AppDatabase): FavoritesDao = db.favoritesDao()
    @Provides fun provideUpcomingMoviesDao(db: AppDatabase): UpcomingMoviesDao = db.upcomingMoviesDao()
    @Provides fun provideTrackedSeriesDao(db: AppDatabase): TrackedSeriesDao = db.trackedSeriesDao()
    @Provides fun provideCacheDao(db: AppDatabase): CacheDao = db.cacheDao()
    @Provides fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()
    @Provides fun provideWatchedDao(db: AppDatabase): WatchedDao = db.watchedDao()
}