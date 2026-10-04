package com.cinemate.app.domain.model

/** Тип контента каталога. */
enum class ContentType {
    MOVIE,
    TV
}

/**
 * Сортировки: русское название для UI + значение API для movie и tv
 * (у дат параметры разные: primary_release_date vs first_air_date).
 */
enum class SortOption(
    val label: String,
    val movieValue: String,
    val tvValue: String
) {
    POPULARITY("По популярности", "popularity.desc", "popularity.desc"),
    RATING("По рейтингу", "vote_average.desc", "vote_average.desc"),
    NEWEST("Сначала новые", "primary_release_date.desc", "first_air_date.desc"),
    OLDEST("Сначала старые", "primary_release_date.asc", "first_air_date.asc"),
    VOTES("По количеству голосов", "vote_count.desc", "vote_count.desc")
}

/**
 * Текущий набор фильтров каталога.
 *
 * Фильтр стран — взаимоисключающий:
 *  - includedCountries («Только страны») уходит на сервер как with_origin_country;
 *  - excludedCountries («Скрыть страны») отрабатывается на клиенте
 *    (для сериалов — по origin_country, для фильмов — по original_language).
 * Одновременно активен только один набор — следит CatalogViewModel.
 */
data class CatalogFilter(
    val contentType: ContentType = ContentType.MOVIE,
    val year: Int? = null,                          // null = любой год
    val genreIds: Set<Int> = emptySet(),            // несколько жанров = OR (28|35)
    val sort: SortOption = SortOption.POPULARITY,
    val excludedCountries: Set<String> = emptySet(), // ISO-коды: IN, KR, SA...
    val includedCountries: Set<String> = emptySet()  // ISO-коды: RU, US...
) {
    /** Значение sort_by для API — зависит от типа контента. */
    val apiSortBy: String
        get() = if (contentType == ContentType.MOVIE) sort.movieValue else sort.tvValue

    /** Фильтр стран вообще включён? */
    val hasCountryFilter: Boolean
        get() = excludedCountries.isNotEmpty() || includedCountries.isNotEmpty()

    /**
     * Белый список — строка для API: "RU|US".
     * null, если белый список пуст (параметр не передаём).
     */
    val withOriginCountry: String?
        get() = includedCountries.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "|")
}