package com.cinemate.app.domain.model

data class Movie(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterPath: String?,
    val releaseDate: String?,
    val rating: Double,
    val voteCount: Int
)
