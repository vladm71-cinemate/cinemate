package com.cinemate.app.util

object Constants {
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    /** Большой постер: карточка фильма, ТВ-экран раздач. */
    const val POSTER_SIZE = "w500"

    /** Малый постер: сетки, списки, карусели — в 3 раза меньше трафика. */
    const val POSTER_SIZE_SMALL = "w185"

    const val DEFAULT_LANGUAGE = "ru-RU"

    const val FALLBACK_POSTER =
        "https://placehold.co/500x750/1c1b1f/938f99?text=No+Poster"

    /** Большой постер (карточка фильма). */
    fun posterUrl(posterPath: String?): String =
        if (posterPath.isNullOrBlank()) FALLBACK_POSTER
        else "$IMAGE_BASE_URL$POSTER_SIZE$posterPath"

    /** Малый постер (сетки, списки, карусели). */
    fun posterUrlSmall(posterPath: String?): String =
        if (posterPath.isNullOrBlank()) FALLBACK_POSTER
        else "$IMAGE_BASE_URL$POSTER_SIZE_SMALL$posterPath"
}