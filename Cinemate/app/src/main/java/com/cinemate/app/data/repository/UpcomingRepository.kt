package com.cinemate.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.cinemate.app.R
import com.cinemate.app.data.local.dao.TrackedSeriesDao
import com.cinemate.app.data.local.dao.UpcomingMoviesDao
import com.cinemate.app.data.local.entity.TrackedSeriesEntity
import com.cinemate.app.data.local.entity.UpcomingMovieEntity
import com.cinemate.app.data.remote.TmdbApi
import com.cinemate.app.domain.model.TitleDetails
import com.cinemate.app.util.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ---------- Модели для экрана «Ожидания» ----------

data class UpcomingMovieUi(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val releaseDate: LocalDate?,
    val statusText: String?,
    val notified: Boolean
)

data class TrackedSeriesUi(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val statusText: String?,
    val isFinished: Boolean
)

@Singleton
class UpcomingRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val upcomingDao: UpcomingMoviesDao,
    private val trackedDao: TrackedSeriesDao,
    private val api: TmdbApi
) {

    companion object {
        /** Типы релизов по ТЗ: 4 = Digital, 5 = Physical. */
        private const val RELEASE_TYPE_DIGITAL = 4
        private const val RELEASE_TYPE_PHYSICAL = 5

        /** Локаль для дат — по языку интерфейса (обновляется из MainActivity). */
        @Volatile
        var displayLocale: Locale = Locale.getDefault()
    }

    private fun formatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", displayLocale)

    private fun formatterShort(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", displayLocale)

    // ---------- Кнопки на карточке (toggle) ----------

    fun observeIsAwaited(id: Int): Flow<Boolean> = upcomingDao.observeIsAwaited(id)
    fun observeIsTracked(id: Int): Flow<Boolean> = trackedDao.observeIsTracked(id)

    suspend fun toggleAwait(details: TitleDetails) {
        val deleted = upcomingDao.delete(details.id)
        if (deleted == 0) {
            upcomingDao.insert(
                UpcomingMovieEntity(
                    id = details.id,
                    title = details.title,
                    posterPath = details.posterPath,
                    releaseDate = details.releaseDate
                )
            )
        }
    }

    suspend fun toggleTrack(details: TitleDetails) {
        val deleted = trackedDao.delete(details.id)
        if (deleted == 0) {
            trackedDao.insert(
                TrackedSeriesEntity(
                    id = details.id,
                    title = details.title,
                    posterPath = details.posterPath
                )
            )
        }
    }

    // ---------- Экран «Ожидания»: списки с готовыми статусами ----------

    fun observeUpcomingMoviesUi(): Flow<List<UpcomingMovieUi>> =
        kotlinx.coroutines.flow.flow {
            upcomingDao.observeAll().collect { entities ->
                val today = LocalDate.now()
                emit(
                    entities.map { entity ->
                        val date = DateUtils.parse(entity.releaseDate)
                        val statusText = when {
                            date == null -> null
                            date == today -> appContext.getString(R.string.upcoming_released_today)
                            date.isAfter(today) -> {
                                val days = ChronoUnit.DAYS.between(today, date)
                                appContext.getString(R.string.upcoming_in_days, days.toInt())
                            }
                            else -> appContext.getString(R.string.status_released).let { "· $it" }
                        }
                        UpcomingMovieUi(
                            id = entity.id,
                            title = entity.title,
                            posterPath = entity.posterPath,
                            releaseDate = date,
                            statusText = statusText,
                            notified = entity.notified
                        )
                    }.sortedBy { it.releaseDate }
                )
            }
        }

    fun observeTrackedSeriesUi(): Flow<List<TrackedSeriesUi>> =
        kotlinx.coroutines.flow.flow {
            trackedDao.observeAll().collect { entities ->
                emit(
                    entities.map { entity ->
                        val statusText = buildSeriesStatus(entity)
                        TrackedSeriesUi(
                            id = entity.id,
                            title = entity.title,
                            posterPath = entity.posterPath,
                            statusText = statusText,
                            isFinished = entity.isFinished
                        )
                    }
                )
            }
        }

    /** Локализованный статус сериала из статусных полей Entity. */
    private fun buildSeriesStatus(entity: TrackedSeriesEntity): String? {
        if (entity.isFinished || entity.nextEpisodeDate == null) {
            return appContext.getString(R.string.upcoming_finished)
        }
        val airDate = DateUtils.parse(entity.nextEpisodeDate) ?: return null
        val today = LocalDate.now()
        val dateText = if (airDate.isAfter(today)) airDate.format(formatterShort()) else "·"
        val sb = StringBuilder()
        entity.nextEpisodeSeason?.let { sb.append(it).append(" ") }
        sb.append(appContext.getString(R.string.favorites_tv)).append(" ")
        entity.nextEpisodeNumber?.let { sb.append(it).append(" ") }
        sb.append("— ").append(dateText)
        return sb.toString().trim()
    }

    // ---------- Проверка при запуске (по ТЗ: только при старте) ----------

    /** Фильмы с наступившей датой; помечает notified и возвращает их. */
    suspend fun checkUpcomingMovies(today: LocalDate): List<UpcomingMovieEntity> {
        val result = mutableListOf<UpcomingMovieEntity>()

        for (movie in upcomingDao.getAllOnce()) {
            val precise = fetchDigitalReleaseDate(movie.id)
            val effective = precise ?: DateUtils.parse(movie.releaseDate)
            val released = effective != null && !effective.isAfter(today)

            if (released && !movie.notified) {
                upcomingDao.insert(
                    movie.copy(
                        releaseDate = precise?.toString() ?: movie.releaseDate,
                        notified = true
                    )
                )
                result += movie
            }
        }
        return result
    }

    /**
     * Сериалы: обновляет статусные поля и возвращает сериалы с вышедшей новой серией.
     */
    suspend fun checkTrackedSeries(today: LocalDate): List<TrackedSeriesEntity> {
        val result = mutableListOf<TrackedSeriesEntity>()

        for (series in trackedDao.getAllOnce()) {
            val details = runCatching { api.tvDetails(series.id) }.getOrNull()
                ?: continue

            val next = details.nextEpisodeToAir
            if (next == null) {
                if (!series.isFinished) {
                    trackedDao.insert(
                        series.copy(isFinished = true, nextEpisodeDate = null)
                    )
                }
                continue
            }

            trackedDao.insert(
                series.copy(
                    nextEpisodeDate = next.airDate,
                    nextEpisodeSeason = next.seasonNumber,
                    nextEpisodeNumber = next.episodeNumber,
                    isFinished = false
                )
            )

            val airDate = DateUtils.parse(next.airDate)
            val episodeOut = airDate != null && !airDate.isAfter(today)
            val isNew = next.seasonNumber != series.lastNotifiedSeason ||
                    next.episodeNumber != series.lastNotifiedEpisode

            if (episodeOut && isNew) {
                val season = next.seasonNumber ?: continue
                val episode = next.episodeNumber ?: continue
                trackedDao.updateLastNotified(series.id, season, episode)
                result += series
            }
        }
        return result
    }

    /**
     * Digital (4) / Physical (5) дата фильма.
     */
    private suspend fun fetchDigitalReleaseDate(movieId: Int): LocalDate? {
        val response = runCatching { api.movieReleaseDates(movieId) }.getOrNull()
            ?: return null

        val candidates = response.results
            .flatMap { it.releaseDates }
            .filter { it.type == RELEASE_TYPE_DIGITAL || it.type == RELEASE_TYPE_PHYSICAL }
            .mapNotNull { DateUtils.parse(it.releaseDate) }
            .distinct()
            .sorted()

        if (candidates.isEmpty()) return null

        val today = LocalDate.now()
        return candidates.firstOrNull { !it.isBefore(today) } ?: candidates.last()
    }
}