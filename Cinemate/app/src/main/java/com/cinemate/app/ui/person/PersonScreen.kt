package com.cinemate.app.ui.person

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cinemate.app.R
import com.cinemate.app.domain.model.HomeViewMode
import com.cinemate.app.domain.model.Movie
import com.cinemate.app.domain.model.Person
import com.cinemate.app.ui.components.ListRowCard
import com.cinemate.app.ui.components.PosterCard
import com.cinemate.app.util.Constants

@Composable
fun PersonScreen(
    onBack: () -> Unit,
    onOpenDetails: (String, Int) -> Unit,
    viewModel: PersonViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewMode by viewModel.homeViewMode.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
        }

        when (val s = state) {
            PersonUiState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is PersonUiState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.search_error, s.message))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
                }
            }

            is PersonUiState.Content -> PersonContent(
                person = s.person,
                viewMode = viewMode,
                onOpenDetails = onOpenDetails
            )
        }
    }
}

@Composable
private fun PersonContent(
    person: Person,
    viewMode: HomeViewMode,
    onOpenDetails: (String, Int) -> Unit
) {
    var biographyExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ---------- Фото + имя ----------
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profileUrl(person.profilePath),
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    person.knownForDepartment?.let {
                        Text(
                            localizedDepartment(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---------- Доп. данные ----------
        if (person.birthday != null || person.placeOfBirth != null) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    buildString {
                        person.birthday?.let { append(stringResource(R.string.person_born, it)) }
                        if (person.birthday != null && person.placeOfBirth != null) append(" · ")
                        person.placeOfBirth?.let { append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- Биография (показать/скрыть) ----------
        person.biography?.let { biography ->
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (biographyExpanded) biography
                    else biography.take(300) + if (biography.length > 300) "…" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { biographyExpanded = !biographyExpanded }) {
                    Text(if (biographyExpanded) stringResource(R.string.person_bio_hide) else stringResource(R.string.person_bio_show))
                }
            }
        }

        // ---------- Актёрские работы ----------
        if (person.castFilmography.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.person_cast_credits),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                FilmographyList(
                    movies = person.castFilmography,
                    viewMode = viewMode,
                    onOpenDetails = onOpenDetails
                )
            }
        }

        // ---------- Закадровые работы ----------
        if (person.crewFilmography.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.person_crew_credits),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                FilmographyList(
                    movies = person.crewFilmography,
                    viewMode = viewMode,
                    onOpenDetails = onOpenDetails
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text(
                "This product uses the TMDb API but is not endorsed or certified by TMDb.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Фильмография внутри LazyColumn. Вложенные списки здесь обычные (не lazy) —
 * отображаются целиком внутри своего item. Раскладка рядами с фикс-колонками:
 * 2 или 3 в строке — по настройке вида главной.
 */
@Composable
private fun FilmographyList(
    movies: List<Movie>,
    viewMode: HomeViewMode,
    onOpenDetails: (String, Int) -> Unit
) {
    if (viewMode != HomeViewMode.LIST) {
        val columns = if (viewMode == HomeViewMode.GRID2) 2 else 3
        val chunked = movies.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            chunked.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { movie ->
                        Box(Modifier.weight(1f)) {
                            PosterCard(
                                movie = movie,
                                onClick = { onOpenDetails(guessType(movie), movie.id) }
                            )
                        }
                    }
                    // Заполнители для выравнивания неполного ряда
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            movies.forEach { movie ->
                ListRowCard(
                    movie = movie,
                    onClick = { onOpenDetails(guessType(movie), movie.id) }
                )
            }
        }
    }
}

/**
 * Тип контента в фильмографии: у Movie после маппинга нет отдельного флага,
 * определяем эвристикой по заполненности полей. Точность не критична:
 * маршрутизация влияет только на эндпоинт деталей.
 */
private fun guessType(movie: Movie): String =
    if (movie.releaseDate != null) "movie" else "tv"

private fun profileUrl(profilePath: String?): String =
    if (profilePath.isNullOrBlank())
        Constants.FALLBACK_POSTER
    else
        "${Constants.IMAGE_BASE_URL}w185$profilePath"

@Composable
private fun localizedDepartment(department: String): String = when (department) {
    "Acting" -> stringResource(R.string.dept_acting)
    "Directing" -> stringResource(R.string.dept_directing)
    "Writing" -> stringResource(R.string.dept_writing)
    "Production" -> stringResource(R.string.dept_production)
    "Camera" -> stringResource(R.string.dept_camera)
    "Sound" -> stringResource(R.string.dept_sound)
    "Art" -> stringResource(R.string.dept_art)
    else -> department
}