package com.cinemate.app.notifications

import android.util.Log
import com.cinemate.app.data.repository.UpcomingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверка ожидаемых фильмов и серий при запуске приложения.
 * По ТЗ: ТОЛЬКО при старте, без WorkManager и пуш-сервера.
 * По результатам: системное уведомление (если разрешено)
 * + баннер внутри приложения (всегда).
 */
@Singleton
class StartupChecker @Inject constructor(
    private val upcomingRepository: UpcomingRepository,
    private val notificationHelper: NotificationHelper,
    private val notificationCenter: NotificationCenter
) {
    companion object {
        private const val TAG = "StartupChecker"

        /**
         * Отладочный сдвиг «сегодняшней» даты (годы). Использовался для
         * теста уведомлений. В релизе должен быть 0.
         */
        private const val DEBUG_DATE_SHIFT_YEARS = 0
    }

    /** Scope живёт столько, сколько процесс приложения. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Вызывается один раз из CinemateApp.onCreate(). Не блокирует UI. */
    fun checkOnStartup() {
        appScope.launch {
            try {
                notificationHelper.createChannel()

                // DEBUG_DATE_SHIFT_YEARS = 0 → обычная текущая дата
                val today = LocalDate.now().plusYears(DEBUG_DATE_SHIFT_YEARS.toLong())

                // ---------- Фильмы с наступившей датой ----------
                val releasedMovies = upcomingRepository.checkUpcomingMovies(today)
                for (movie in releasedMovies) {
                    Log.i(TAG, "Фильм вышел: ${movie.title} (${movie.releaseDate})")
                    notificationHelper.notifyMovieReleased(movie.id, movie.title)
                    notificationCenter.add("Вышел фильм: ${movie.title}")
                }

                // ---------- Сериалы с новой серией ----------
                val updatedSeries = upcomingRepository.checkTrackedSeries(today)
                for (series in updatedSeries) {
                    Log.i(TAG, "Новая серия: ${series.title}")
                    notificationHelper.notifyNewEpisode(series.id, series.title)
                    notificationCenter.add("Новая серия: ${series.title}")
                }

                if (releasedMovies.isEmpty() && updatedSeries.isEmpty()) {
                    Log.i(TAG, "Новых релизов и серий нет")
                }
            } catch (e: Exception) {
                // Проверка никогда не мешает старту приложения
                Log.w(TAG, "Проверка при старте не удалась: ${e.message}")
            }
        }
    }
}