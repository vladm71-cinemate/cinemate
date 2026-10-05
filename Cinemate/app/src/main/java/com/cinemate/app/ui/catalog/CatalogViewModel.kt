package com.cinemate.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.data.repository.CatalogRepository
import com.cinemate.app.domain.model.CatalogFilter
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Genre
import com.cinemate.app.domain.model.HomeViewMode
import com.cinemate.app.domain.model.Movie
import com.cinemate.app.domain.model.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Состояние экрана каталога (список — отдельным Paging-потоком). */
data class CatalogUiState(
    val filter: CatalogFilter = CatalogFilter(),
    val genres: List<Genre> = emptyList(),
    val genresLoading: Boolean = false,
    val availableYears: List<Int> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    /** Вид главной: сетка или список. Из настроек, живой. */
    val homeViewMode: StateFlow<HomeViewMode> = tokenStore.homeViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewMode.GRID2)

    init {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        _state.update { it.copy(availableYears = (currentYear downTo 1960).toList()) }
        loadGenres()

        // Перезагружаем жанры при смене языка интерфейса
        viewModelScope.launch {
            tokenStore.appLanguage.collect {
                loadGenres()
            }
        }
    }

    /** При каждом изменении фильтра — новый Paging-поток со страницы 1. */
    val movies: Flow<PagingData<Movie>> = _state
        .flatMapLatest { repository.discover(it.filter) }
        .cachedIn(viewModelScope)

    fun setContentType(type: ContentType) {
        if (_state.value.filter.contentType == type) return
        _state.update {
            it.copy(
                filter = it.filter.copy(
                    contentType = type,
                    genreIds = emptySet(),
                    excludedCountries = emptySet(),
                    includedCountries = emptySet()
                )
            )
        }
        loadGenres()
    }

    fun setYear(year: Int?) = _state.update {
        it.copy(filter = it.filter.copy(year = year))
    }

    fun setSort(sort: SortOption) = _state.update {
        it.copy(filter = it.filter.copy(sort = sort))
    }

    fun toggleGenre(genreId: Int) = _state.update {
        val newIds = it.filter.genreIds.toMutableSet().apply {
            if (!add(genreId)) remove(genreId)
        }
        it.copy(filter = it.filter.copy(genreIds = newIds))
    }

    fun clearGenres() = _state.update {
        it.copy(filter = it.filter.copy(genreIds = emptySet()))
    }

    fun toggleExcludedCountry(code: String) = _state.update {
        val newExcluded = it.filter.excludedCountries.toMutableSet().apply {
            if (!add(code)) remove(code)
        }
        it.copy(
            filter = it.filter.copy(
                excludedCountries = newExcluded,
                includedCountries = if (newExcluded.isEmpty()) it.filter.includedCountries
                else emptySet()
            )
        )
    }

    fun toggleIncludedCountry(code: String) = _state.update {
        val newIncluded = it.filter.includedCountries.toMutableSet().apply {
            if (!add(code)) remove(code)
        }
        it.copy(
            filter = it.filter.copy(
                includedCountries = newIncluded,
                excludedCountries = if (newIncluded.isEmpty()) it.filter.excludedCountries
                else emptySet()
            )
        )
    }

    fun clearCountries() = _state.update {
        it.copy(
            filter = it.filter.copy(
                excludedCountries = emptySet(),
                includedCountries = emptySet()
            )
        )
    }

    fun setHomeViewMode(mode: HomeViewMode) {
        viewModelScope.launch { tokenStore.saveHomeViewMode(mode) }
    }

    private fun loadGenres() {
        val type = _state.value.filter.contentType
        viewModelScope.launch {
            _state.update { it.copy(genresLoading = true) }
            _state.update {
                try {
                    val langTag = appLanguageToTmdbTag()
                    it.copy(genres = repository.genres(type, langTag), genresLoading = false)
                } catch (e: Exception) {
                    it.copy(genres = emptyList(), genresLoading = false)
                }
            }
        }
    }

    /** Маппинг языка интерфейса -> тег language для TMDb. */
    private suspend fun appLanguageToTmdbTag(): String {
        val lang = tokenStore.appLanguage.first()
        return when (lang) {
            "ru" -> "ru-RU"
            "en" -> "en-US"
            "es" -> "es-ES"
            "de" -> "de-DE"
            "it" -> "it-IT"
            "fr" -> "fr-FR"
            "be" -> "be-BY"
            "kk" -> "kk-KZ"
            "zh" -> "zh-CN"
            else -> "ru-RU" // system: русский как дефолт контента
        }
    }
}