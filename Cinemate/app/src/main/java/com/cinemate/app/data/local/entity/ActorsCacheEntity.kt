package com.cinemate.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш списка актёров. Актёры меняются редко — храним 7 дней.
 * Актёры сериализуются в JSON строку (Moshi) — отдельная таблица
 * для актёров не нужна, это единый «снимок» карточки.
 */
@Entity(tableName = "actors_cache")
data class ActorsCacheEntity(
    /** Составной ключ текстом: "movie_123" / "tv_456". */
    @PrimaryKey val titleKey: String,
    /** JSON: List<Actor>. */
    val castJson: String,
    /** Момент записи кэша, epoch millis. */
    val cachedAt: Long
)