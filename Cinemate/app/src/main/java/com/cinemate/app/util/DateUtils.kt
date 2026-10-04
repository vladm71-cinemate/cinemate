package com.cinemate.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateUtils {

    /** Текущая локаль для форматирования дат (задаётся из языка интерфейса). */
    @Volatile
    var locale: Locale = Locale.getDefault()

    private fun displayFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", locale)

    private fun displayFormatterShort(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", locale)

    /** Парсинг "2026-03-01" -> LocalDate, безопасно. */
    fun parse(date: String?): LocalDate? =
        date?.take(10)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    /** Дата словами: "1 марта 2026" / "March 1, 2026" — по локали. */
    fun formatDate(date: LocalDate): String =
        date.format(displayFormatter())

    /** Короткий формат: "1 мар 2026". */
    fun formatDateShort(date: LocalDate): String =
        date.format(displayFormatterShort())

    /**
     * Статус для ожидаемого фильма.
     * Тексты — RU по умолчанию; локализация UI происходит на уровне экрана
     * через строки ресурсов, здесь — логические метки.
     */
    fun releaseStatus(releaseDate: LocalDate?, today: LocalDate): String? {
        val date = releaseDate ?: return null
        return when {
            date == today -> "released_today"
            date.isAfter(today) -> {
                val days = ChronoUnit.DAYS.between(today, date)
                "in_days:$days"
            }
            else -> "released"
        }
    }

    /**
     * Статус для отслеживаемого сериала. Возвращает структуру:
     * "finished" | "episode:<season>:<episode>:<date>"
     */
    fun seriesStatus(
        nextEpisode: String?,
        season: Int?,
        episode: Int?,
        today: LocalDate
    ): String? {
        if (nextEpisode == null) return "finished"
        val airDate = parse(nextEpisode) ?: return null
        val dateText = if (airDate.isAfter(today)) {
            formatDateShort(airDate)
        } else {
            "aired"
        }
        return buildString {
            append("episode:")
            append(season ?: "")
            append(":")
            append(episode ?: "")
            append(":")
            append(dateText)
        }
    }
}