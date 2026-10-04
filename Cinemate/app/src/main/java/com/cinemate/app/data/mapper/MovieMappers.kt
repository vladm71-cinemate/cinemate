package com.cinemate.app.data.mapper

import com.cinemate.app.data.remote.dto.GenreDto
import com.cinemate.app.data.remote.dto.MovieDto
import com.cinemate.app.domain.model.Genre
import com.cinemate.app.domain.model.Movie

/**
 * DTO -> Domain. Работает и для фильмов, и для сериалов:
 * берём то поле, которое заполнено.
 */
fun MovieDto.toDomain(): Movie = Movie(
    id = id,
    title = title ?: name ?: "Без названия",
    originalTitle = originalTitle ?: originalName,
    overview = overview,
    posterPath = posterPath,
    releaseDate = releaseDate ?: firstAirDate,
    rating = voteAverage ?: 0.0,
    voteCount = voteCount ?: 0
)

fun GenreDto.toDomain(): Genre = Genre(id = id, name = name)
