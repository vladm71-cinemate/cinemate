package com.cinemate.app.domain.model

/** Персона (актёр) и её фильмография. */
data class Person(
    val id: Int,
    val name: String,
    val biography: String?,
    val profilePath: String?,
    /** «Актёр», «Режиссёр» и т.п. */
    val knownForDepartment: String?,
    val birthday: String?,
    val placeOfBirth: String?,
    /** Работы в кадре (актёрские роли). */
    val castFilmography: List<Movie>,
    /** Работы за кадром (режиссёр, сценарист и т.д.). */
    val crewFilmography: List<Movie>
)