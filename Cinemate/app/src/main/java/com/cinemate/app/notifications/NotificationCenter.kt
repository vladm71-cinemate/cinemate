package com.cinemate.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Очередь сообщений для баннера внутри приложения.
 * Наполняется StartupChecker'ом при обнаружении релизов/серий,
 * читается баннером на главном экране.
 */
@Singleton
class NotificationCenter @Inject constructor() {

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    fun add(message: String) {
        _messages.value = _messages.value + message
    }

    /** Убрать сообщение из баннера (тап по крестику). */
    fun dismiss(message: String) {
        _messages.value = _messages.value - message
    }
}