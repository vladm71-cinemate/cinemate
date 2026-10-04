package com.cinemate.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш ключевых слов карточки. Тоже 7 дней.
 */
@Entity(tableName = "keywords_cache")
data class KeywordsCacheEntity(
    @PrimaryKey val titleKey: String,
    /** JSON: List<Keyword>. */
    val keywordsJson: String,
    /** Момент записи кэша, epoch millis. */
    val cachedAt: Long
)