package com.cinemate.app.ui.upcoming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cinemate.app.R
import com.cinemate.app.data.repository.TrackedSeriesUi
import com.cinemate.app.data.repository.UpcomingMovieUi
import com.cinemate.app.util.Constants
import com.cinemate.app.util.DateUtils

@Composable
fun UpcomingScreen(
    onOpenDetails: (String, Int) -> Unit,
    viewModel: UpcomingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.movies.isEmpty() && state.series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.upcoming_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- Ожидаемые фильмы ----------
        if (state.movies.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.upcoming_movies_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.movies, key = { "movie_${it.id}" }) { movie ->
                UpcomingMovieRow(movie = movie, onClick = { onOpenDetails("movie", movie.id) })
            }
        }

        // ---------- Отслеживаемые сериалы ----------
        if (state.series.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.upcoming_series_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.series, key = { "tv_${it.id}" }) { series ->
                TrackedSeriesRow(series = series, onClick = { onOpenDetails("tv", series.id) })
            }
        }
    }
}

@Composable
private fun UpcomingMovieRow(
    movie: UpcomingMovieUi,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = Constants.posterUrlSmall(movie.posterPath),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    movie.releaseDate?.let { DateUtils.formatDate(it) }
                        ?: stringResource(R.string.upcoming_date_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                movie.statusText?.let { status ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (movie.notified) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackedSeriesRow(
    series: TrackedSeriesUi,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = Constants.posterUrlSmall(series.posterPath),
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    series.statusText ?: stringResource(R.string.upcoming_waiting_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (series.isFinished) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}