package com.cinemate.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinemate.app.data.repository.SearchItem
import com.cinemate.app.data.repository.SearchRepository
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val contentType: ContentType = ContentType.MOVIE,
    /** true — универсальный поиск «Все» (фильмы+сериалы одним списком). */
    val searchAll: Boolean = false,
    val loading: Boolean = false,
    val results: List<Movie> = emptyList(),
    /** Тип результата по id (для ALL-режима: true = сериал). */
    val resultTypes: Map<Int, Boolean> = emptyMap(),
    val searched: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val history: StateFlow<List<String>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val pendingQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            pendingQuery
                .debounce(400)
                .distinctUntilChanged()
                .collect { query -> runSearch(query) }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _state.value = _state.value.copy(query = newQuery)
        val trimmed = newQuery.trim()
        pendingQuery.value = trimmed
        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(
                loading = false,
                results = emptyList(),
                searched = false,
                error = null
            )
        }
    }

    fun onHistoryItemClicked(query: String) {
        _state.value = _state.value.copy(query = query)
        pendingQuery.value = query.trim()
    }

    fun retry() {
        val query = _state.value.query.trim()
        if (query.isNotEmpty()) {
            _state.value = _state.value.copy(loading = true)
            viewModelScope.launch { executeSearch(query) }
        }
    }

    fun setContentType(type: ContentType) {
        if (_state.value.contentType == type) return
        _state.value = _state.value.copy(
            contentType = type,
            searchAll = false
        )
        val query = _state.value.query.trim()
        if (query.isNotEmpty()) {
            _state.value = _state.value.copy(loading = true)
            viewModelScope.launch { executeSearch(query) }
        }
    }

    /** Включить режим «Все» (multi-search). */
    fun setAllMode() {
        if (_state.value.searchAll) return
        _state.value = _state.value.copy(searchAll = true)
        val query = _state.value.query.trim()
        if (query.isNotEmpty()) {
            _state.value = _state.value.copy(loading = true)
            viewModelScope.launch { executeSearch(query) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun onResultClicked() {
        val query = _state.value.query.trim()
        if (query.isNotEmpty()) {
            viewModelScope.launch { repository.addToHistory(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isEmpty()) return
        _state.value = _state.value.copy(loading = true)
        executeSearch(query)
    }

    private suspend fun executeSearch(query: String) {
        _state.value = try {
            if (_state.value.searchAll) {
                val items = repository.searchMulti(query)
                _state.value.copy(
                    loading = false,
                    results = items.map { it.movie },
                    resultTypes = items.associate { it.movie.id to it.isTv },
                    searched = true,
                    error = null
                )
            } else {
                val results = repository.search(_state.value.contentType, query)
                _state.value.copy(
                    loading = false,
                    results = results,
                    resultTypes = emptyMap(),
                    searched = true,
                    error = null
                )
            }
        } catch (e: Exception) {
            _state.value.copy(
                loading = false,
                results = emptyList(),
                searched = true,
                error = e.message ?: "Ошибка поиска"
            )
        }
    }
}