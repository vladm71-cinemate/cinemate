package com.cinemate.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции базы Cinemate.
 *
 * История версий:
 *  v1 — favorites, upcoming_movies, tracked_series
 *  v2 — + actors_cache, keywords_cache
 *  v3 — + search_history
 *  v4 — tracked_series: + nextEpisodeDate, nextEpisodeSeason, nextEpisodeNumber, isFinished
 *  v5 — + watched_items (история «Смотрел»)
 *  v6 — favorites: + addedAt (сортировка «по дате добавления»)
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `actors_cache` (" +
                        "`titleKey` TEXT NOT NULL, " +
                        "`castJson` TEXT NOT NULL, " +
                        "`cachedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`titleKey`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `keywords_cache` (" +
                        "`titleKey` TEXT NOT NULL, " +
                        "`keywordsJson` TEXT NOT NULL, " +
                        "`cachedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`titleKey`))"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `search_history` (" +
                        "`query` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`query`))"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tracked_series` ADD COLUMN `nextEpisodeDate` TEXT")
            db.execSQL("ALTER TABLE `tracked_series` ADD COLUMN `nextEpisodeSeason` INTEGER")
            db.execSQL("ALTER TABLE `tracked_series` ADD COLUMN `nextEpisodeNumber` INTEGER")
            db.execSQL(
                "ALTER TABLE `tracked_series` ADD COLUMN `isFinished` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `watched_items` (" +
                        "`infoHash` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`posterPath` TEXT, " +
                        "`watchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`infoHash`))"
            )
        }
    }

    /** v5 -> v6: favorites + addedAt (дата добавления для сортировки). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Новая колонка с дефолтом 0; старые записи получат 0 —
            // при следующем toggle (добавить/удалить/добавить) дата проставится
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `addedAt` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Все миграции по порядку — для addMigrations(). */
    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}