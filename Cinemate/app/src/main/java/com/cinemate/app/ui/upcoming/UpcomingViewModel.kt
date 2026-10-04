package com.cinemate.app.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinemate.app.data.repository.TrackedSeriesUi
import com.cinemate.app.data.repository.UpcomingMovieUi
import com.cinemate.app.data.repository.UpcomingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Состояние экрана «Ожидания»: два готовых списка. */
data class UpcomingUiState(
    val movies: List<UpcomingMovieUi> = emptyList(),
    val series: List<TrackedSeriesUi> = emptyList()
)

@HiltViewModel
class UpcomingViewModel @Inject constructor(
    repository: UpcomingRepository
) : ViewModel() {

    /**
     * Оба списка — Flow из Room со статусами, посчитанными в репозитории.
     * Любое изменение (добавил «Ожидаю»/«Отслеживать» на карточке,
     * проверка при старте обновила статусы) прилетает на экран само.
     */
    val state: StateFlow<UpcomingUiState> = combine(
        repository.observeUpcomingMoviesUi(),
        repository.observeTrackedSeriesUi()
    ) { movies, series ->
        UpcomingUiState(movies = movies, series = series)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpcomingUiState())
}