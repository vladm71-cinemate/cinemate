package com.cinemate.app.ui.details

import com.cinemate.app.domain.model.Actor
import com.cinemate.app.domain.model.Keyword
import com.cinemate.app.domain.model.Movie
import com.cinemate.app.domain.model.TitleDetails

/** Состояние ленивого блока: данные не запрашиваем, пока пользователь не попросил. */
sealed interface LazyBlockState<out T> {
    data object Idle : LazyBlockState<Nothing>
    data object Loading : LazyBlockState<Nothing>
    data class Loaded<T>(val data: T) : LazyBlockState<T>
    data class Error(val message: String) : LazyBlockState<Nothing>
}

/** Нода парсера для ViewModel. */
data class ParserNodeUi(
    val name: String,
    val baseUrl: String,
    val apiKey: String
)

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Content(
        val details: TitleDetails,
        val actors: LazyBlockState<List<Actor>> = LazyBlockState.Idle,
        val keywords: LazyBlockState<List<Keyword>> = LazyBlockState.Idle,
        val similar: LazyBlockState<List<Movie>> = LazyBlockState.Idle,
        val showMoreExpanded: Boolean = false,
        val showActors: Boolean = false,
        val showKeywords: Boolean = false
    ) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}
