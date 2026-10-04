package com.cinemate.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinemate.app.data.local.entity.FavoriteEntity
import com.cinemate.app.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Режим сортировки избранного. */
enum class FavoritesSort(val label: String) {
    TITLE("По алфавиту"),
    DATE("По дате добавления")
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: FavoritesRepository
) : ViewModel() {

    /** Выбранная сортировка (на сессию). */
    private val _sort = MutableStateFlow(FavoritesSort.DATE)
    val sort: StateFlow<FavoritesSort> = _sort.asStateFlow()

    fun setSort(sort: FavoritesSort) {
        _sort.value = sort
    }

    /**
     * Живой список из Room: любое изменение (кнопка на карточке,
     * свайп здесь) обновляет UI само, без перезагрузки.
     * Сортировка применяется локально при смене режима.
     */
    val favorites: StateFlow<List<FavoriteEntity>> = combine(
        repository.observeAllByTitle(),
        repository.observeAllByDate(),
        _sort
    ) { byTitle, byDate, sort ->
        if (sort == FavoritesSort.TITLE) byTitle else byDate
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Свайп-удаление. */
    fun delete(id: Int, type: String) {
        viewModelScope.launch { repository.delete(id, type) }
    }
}