package com.cinemate.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cinemate.app.data.local.dao.CacheDao
import com.cinemate.app.data.local.dao.FavoritesDao
import com.cinemate.app.data.local.dao.SearchHistoryDao
import com.cinemate.app.data.local.dao.TrackedSeriesDao
import com.cinemate.app.data.local.dao.UpcomingMoviesDao
import com.cinemate.app.data.local.dao.WatchedDao
import com.cinemate.app.data.local.entity.ActorsCacheEntity
import com.cinemate.app.data.local.entity.FavoriteEntity
import com.cinemate.app.data.local.entity.KeywordsCacheEntity
import com.cinemate.app.data.local.entity.SearchHistoryEntity
import com.cinemate.app.data.local.entity.TrackedSeriesEntity
import com.cinemate.app.data.local.entity.UpcomingMovieEntity
import com.cinemate.app.data.local.entity.WatchedItemEntity

/**
 * version 6: favorites + addedAt (сортировка «по дате добавления»).
 */
@Database(
    entities = [
        FavoriteEntity::class,
        UpcomingMovieEntity::class,
        TrackedSeriesEntity::class,
        ActorsCacheEntity::class,
        KeywordsCacheEntity::class,
        SearchHistoryEntity::class,
        WatchedItemEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun upcomingMoviesDao(): UpcomingMoviesDao
    abstract fun trackedSeriesDao(): TrackedSeriesDao
    abstract fun cacheDao(): CacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun watchedDao(): WatchedDao
}