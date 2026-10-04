package com.cinemate.app.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.cinemate.app.R
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.HomeViewMode
import com.cinemate.app.ui.components.ListRowCard
import com.cinemate.app.ui.components.PosterCard

@Composable
fun CatalogScreen(
    onOpenDetails: (String, Int) -> Unit = { _, _ -> },
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewMode by viewModel.homeViewMode.collectAsStateWithLifecycle()
    val movies = viewModel.movies.collectAsLazyPagingItems()
    var genreDialogOpen by remember { mutableStateOf(false) }
    var countryDialogOpen by remember { mutableStateOf(false) }

    val openMovie: (Int) -> Unit = { id ->
        onOpenDetails(
            if (state.filter.contentType == ContentType.TV) "tv" else "movie",
            id
        )
    }

    Column(Modifier.fillMaxSize()) {

        // ---------- Переключатель Фильмы | Сериалы ----------
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = state.filter.contentType == ContentType.MOVIE,
                onClick = { viewModel.setContentType(ContentType.MOVIE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(stringResource(R.string.filter_movies)) }
            SegmentedButton(
                selected = state.filter.contentType == ContentType.TV,
                onClick = { viewModel.setContentType(ContentType.TV) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(stringResource(R.string.filter_tv)) }
        }

        // ---------- Строка фильтров ----------
        FiltersRow(
            state = state,
            onYearSelected = viewModel::setYear,
            onGenresClick = { genreDialogOpen = true },
            onCountriesClick = { countryDialogOpen = true },
            onSortSelected = viewModel::setSort
        )

        Spacer(Modifier.height(8.dp))

        // ---------- Содержимое ----------
        when {
            movies.loadState.refresh is LoadState.Loading && movies.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            movies.loadState.refresh is LoadState.Error && movies.itemCount == 0 -> {
                val message = (movies.loadState.refresh as LoadState.Error).error.message
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.catalog_error, message ?: "нет сети"))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { movies.retry() }) { Text(stringResource(R.string.common_retry)) }
                    }
                }
            }
            movies.loadState.refresh is LoadState.NotLoading && movies.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.catalog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                when (viewMode) {
                    HomeViewMode.GRID2 -> GridContent(
                        movies = movies,
                        columns = 2,
                        onOpenMovie = openMovie,
                        onRetry = { movies.retry() }
                    )
                    HomeViewMode.GRID -> GridContent(
                        movies = movies,
                        columns = 3,
                        onOpenMovie = openMovie,
                        onRetry = { movies.retry() }
                    )
                    HomeViewMode.LIST -> ListContent(
                        movies = movies,
                        onOpenMovie = openMovie,
                        onRetry = { movies.retry() }
                    )
                }
            }
        }
    }

    // ---------- Диалоги ----------
    if (genreDialogOpen) {
        GenreDialog(
            genres = state.genres,
            selectedIds = state.filter.genreIds,
            onToggle = viewModel::toggleGenre,
            onClear = viewModel::clearGenres,
            onDismiss = { genreDialogOpen = false }
        )
    }

    if (countryDialogOpen) {
        CountryDialog(
            excludedCountries = state.filter.excludedCountries,
            includedCountries = state.filter.includedCountries,
            onToggleExcluded = viewModel::toggleExcludedCountry,
            onToggleIncluded = viewModel::toggleIncludedCountry,
            onClear = viewModel::clearCountries,
            onDismiss = { countryDialogOpen = false }
        )
    }
}

/** Вид «Сетка»: постеры 2:3 в заданное число колонок (2 или 3). */
@Composable
private fun GridContent(
    movies: androidx.paging.compose.LazyPagingItems<com.cinemate.app.domain.model.Movie>,
    columns: Int,
    onOpenMovie: (Int) -> Unit,
    onRetry: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(count = movies.itemCount) { index ->
            movies[index]?.let { movie ->
                PosterCard(movie = movie, onClick = { onOpenMovie(movie.id) })
            }
        }

        if (movies.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }

        if (movies.loadState.append is LoadState.Error) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.load_more_error), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }
            }
        }
    }
}

/** Вид «Список»: строки с компактным постером, названием и годом. */
@Composable
private fun ListContent(
    movies: androidx.paging.compose.LazyPagingItems<com.cinemate.app.domain.model.Movie>,
    onOpenMovie: (Int) -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(count = movies.itemCount) { index ->
            movies[index]?.let { movie ->
                ListRowCard(movie = movie, onClick = { onOpenMovie(movie.id) })
            }
        }

        if (movies.loadState.append is LoadState.Loading) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }

        if (movies.loadState.append is LoadState.Error) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.load_more_error), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }
            }
        }
    }
}