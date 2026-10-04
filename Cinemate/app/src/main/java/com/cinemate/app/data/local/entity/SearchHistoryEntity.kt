package com.cinemate.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * История поиска. Первичный ключ — сам запрос (в нижнем регистре),
 * поэтому дубликаты невозможны: повторный запрос обновляет timestamp.
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,   // нормализовано: lowercased, trimmed
    /** Момент последнего использования запроса, epoch millis. */
    val timestamp: Long
)