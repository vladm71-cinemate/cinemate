package com.cinemate.app.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.data.repository.DetailsRepository
import com.cinemate.app.domain.model.HomeViewMode
import com.cinemate.app.domain.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PersonUiState {
    data object Loading : PersonUiState
    data class Content(val person: Person) : PersonUiState
    data class Error(val message: String) : PersonUiState
}

@HiltViewModel
class PersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DetailsRepository,
    tokenStore: TokenStore
) : ViewModel() {

    private val personId: Int = savedStateHandle.get<Int>("id") ?: 0

    private val _state = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    /** Фильмография показывается в том же виде, что и главная (сетка/список). */
    val homeViewMode: StateFlow<HomeViewMode> = tokenStore.homeViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewMode.GRID2)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = PersonUiState.Loading
            _state.value = try {
                PersonUiState.Content(repository.person(personId))
            } catch (e: Exception) {
                PersonUiState.Error(e.message ?: "Не удалось загрузить")
            }
        }
    }
}