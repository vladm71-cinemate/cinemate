package com.cinemate.app.data.mapper

import com.cinemate.app.data.remote.dto.CastMemberDto
import com.cinemate.app.data.remote.dto.CreditsDto
import com.cinemate.app.data.remote.dto.KeywordDto
import com.cinemate.app.data.remote.dto.MovieDetailsDto
import com.cinemate.app.data.remote.dto.TvDetailsDto
import com.cinemate.app.domain.model.Actor
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Genre
import com.cinemate.app.domain.model.Keyword
import com.cinemate.app.domain.model.TitleDetails

fun MovieDetailsDto.toDomain(): TitleDetails = TitleDetails(
    id = id,
    type = ContentType.MOVIE,
    title = title ?: "Без названия",
    originalTitle = originalTitle,
    overview = overview,
    posterPath = posterPath,
    releaseDate = releaseDate,
    runtimeMinutes = runtime,
    tagline = tagline?.takeIf { it.isNotBlank() },
    status = status,
    rating = voteAverage ?: 0.0,
    voteCount = voteCount ?: 0,
    genres = genres.map { Genre(it.id, it.name) },
    // «Подробнее»
    budget = budget ?: 0L,
    revenue = revenue ?: 0L,
    companies = productionCompanies.map { it.name },
    countries = productionCountries.map { it.name },
    languages = spokenLanguages.mapNotNull { it.name ?: it.englishName }
)

fun TvDetailsDto.toDomain(): TitleDetails = TitleDetails(
    id = id,
    type = ContentType.TV,
    title = name ?: "Без названия",
    originalTitle = originalName,
    overview = overview,
    posterPath = posterPath,
    releaseDate = firstAirDate,
    runtimeMinutes = episodeRunTime.firstOrNull(),
    tagline = null, // у сериалов TMDb слоган не отдаёт
    status = status,
    rating = voteAverage ?: 0.0,
    voteCount = voteCount ?: 0,
    genres = genres.map { Genre(it.id, it.name) },
    // «Подробнее»: у сериалов студии = сети вещания
    budget = 0L,
    revenue = 0L,
    companies = networks.map { it.name },
    countries = productionCountries.map { it.name },
    languages = spokenLanguages.mapNotNull { it.name ?: it.englishName },
    numberOfSeasons = numberOfSeasons
)

fun CastMemberDto.toDomain(): Actor = Actor(
    id = id,
    name = name,
    character = character,
    profilePath = profilePath
)

fun KeywordDto.toDomain(): Keyword = Keyword(
    id = id,
    name = name
)

fun List<CastMemberDto>.toActorList(): List<Actor> =
    this.map { it.toDomain() }

fun List<KeywordDto>.toKeywordList(): List<Keyword> =
    this.map { it.toDomain() }