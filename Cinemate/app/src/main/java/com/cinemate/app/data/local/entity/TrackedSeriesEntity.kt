package com.cinemate.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Отслеживаемый сериал.
 * Статусные поля nextEpisode и isFinished заполняются при проверке
 * при старте приложения из /tv/{id}; экран их просто показывает.
 */
@Entity(tableName = "tracked_series")
data class TrackedSeriesEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val posterPath: String?,
    val lastNotifiedSeason: Int? = null,
    val lastNotifiedEpisode: Int? = null,
    // ---------- статус, обновляется при проверке при старте ----------
    /** Дата следующей серии "2026-05-12" или null, если завершён. */
    val nextEpisodeDate: String? = null,
    val nextEpisodeSeason: Int? = null,
    val nextEpisodeNumber: Int? = null,
    /** true, когда TMDb вернул next_episode_to_air = null. */
    val isFinished: Boolean = false
)