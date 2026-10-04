package com.cinemate.app.domain.model

/**
 * Язык описаний и локализованных данных TMDb.
 * Значения — полноценные теги для параметра language TMDb.
 */
enum class ContentLanguage(val apiTag: String, val label: String) {
    RU("ru-RU", "Русский"),
    EN("en-US", "English"),
    ES("es-ES", "Español"),
    DE("de-DE", "Deutsch"),
    IT("it-IT", "Italiano"),
    FR("fr-FR", "Français"),
    BE("be-BY", "Беларуская"),
    KK("kk-KZ", "Қазақша"),
    ZH("zh-CN", "中文")
}