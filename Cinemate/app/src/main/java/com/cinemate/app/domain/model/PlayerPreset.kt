package com.cinemate.app.domain.model

/**
 * Пресет известного плеера для «Просмотра на ТВ».
 *
 * packageName   — точное имя пакета (снято с бокса пользователя);
 * label         — название в списке настроек;
 * searchScheme  — шаблон deep link поиска, %s подменяется запросом.
 *                 null = deep link неизвестен, работает приёмник
 *                 (буфер + запуск). Уточняется на тестах.
 * confirmed     — все пакеты сверены с реальным боксом (фото приложений).
 */
data class PlayerPreset(
    val packageName: String,
    val label: String,
    val searchScheme: String? = null,
    val confirmed: Boolean = false
)

object PlayerPresets {

    /**
     * Плееры, снятые с бокса пользователя (сентябрь 2026):
     *  - Zona 3.0.66            -> mobi.zona
     *  - PRISMA 0.5.5           -> top.rootu.prisma
     *  - NUM 1.0.150            -> ru.yourok.num
     *  - Lampa TV 7.7.9-115     -> ru.twicker.lampa  (Lampa Uncensored / ByLampa)
     *  - LazyMedia Deluxe 3.440 -> com.lazycatsoftware.lmd
     */
    val ALL = listOf(
        PlayerPreset(
            packageName = "ru.yourok.num",
            label = "NUM",
            searchScheme = null,   // deep link NUM — на тестах (GitHub YouROK)
            confirmed = true
        ),
        PlayerPreset(
            packageName = "top.rootu.prisma",
            label = "Prizma",
            searchScheme = null,   // deep link — на тестах (GitHub Sheinices/Prisma_TV)
            confirmed = true
        ),
        PlayerPreset(
            packageName = "com.lazycatsoftware.lmd",
            label = "Lazy Media Deluxe",
            searchScheme = "lazymedia://search?query=%s",  // проверить на боксе
            confirmed = true
        ),
        PlayerPreset(
            packageName = "mobi.zona",
            label = "Zona",
            searchScheme = null,   // есть zona:// для контента, поиск — проверить
            confirmed = true
        ),
        PlayerPreset(
            packageName = "ru.twicker.lampa",
            label = "Lampa TV (Uncensored)",
            searchScheme = "lampa://search?query=%s",  // проверить на боксе
            confirmed = true
        )
    )

    fun byPackage(packageName: String): PlayerPreset? = ALL.find { it.packageName == packageName }

    fun labelFor(packageName: String): String =
        byPackage(packageName)?.label ?: packageName
}