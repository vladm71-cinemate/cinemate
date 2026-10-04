package com.cinemate.app.domain.model

/**
 * Единая модель деталей для фильма и сериала — UI не делает различий.
 */
data class TitleDetails(
    val id: Int,
    val type: ContentType,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterPath: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val tagline: String?,
    val status: String?,
    val rating: Double,
    val voteCount: Int,
    val genres: List<Genre>,
    // ---------- блок «Подробнее» ----------
    /** Бюджет в долларах (0 = не указан). Только у фильмов. */
    val budget: Long = 0L,
    /** Сборы в долларах (0 = не указаны). Только у фильмов. */
    val revenue: Long = 0L,
    /** Студии (у сериалов — каналы/сети). */
    val companies: List<String> = emptyList(),
    /** Страны производства — русские названия с TMDb. */
    val countries: List<String> = emptyList(),
    /** Языки — русские названия с TMDb. */
    val languages: List<String> = emptyList(),
    /** Число сезонов. Только у сериалов. */
    val numberOfSeasons: Int? = null
)