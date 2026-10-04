package com.cinemate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Тема приложения для MainActivity.
 * WhileSubscribed: подписка жива, пока жива Activity — изменение темы
 * в настройках прилетает немедленно, без перезапуска приложения.
 * Начальное значение DARK — тема по умолчанию по ТЗ.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    tokenStore: TokenStore
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = tokenStore.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.DARK
        )
}