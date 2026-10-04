package com.cinemate.app.ui.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinemate.app.R
import com.cinemate.app.domain.model.SortOption

/**
 * Горизонтальная строка фильтров: Год | Жанры | Страны | Сортировка.
 * На узких экранах строка прокручивается горизонтально — чипы не сжимаются.
 */
@Composable
fun FiltersRow(
    state: CatalogUiState,
    onYearSelected: (Int?) -> Unit,
    onGenresClick: () -> Unit,
    onCountriesClick: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var yearMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---------- Год ----------
        Box {
            FilterChip(
                selected = state.filter.year != null,
                onClick = { yearMenuOpen = true },
                label = {
                    Text(
                        text = state.filter.year?.toString() ?: stringResource(R.string.filter_year),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            DropdownMenu(
                expanded = yearMenuOpen,
                onDismissRequest = { yearMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.filter_year_any)) },
                    onClick = {
                        onYearSelected(null)
                        yearMenuOpen = false
                    }
                )
                state.availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = {
                            onYearSelected(year)
                            yearMenuOpen = false
                        }
                    )
                }
            }
        }

        // ---------- Жанры ----------
        FilterChip(
            selected = state.filter.genreIds.isNotEmpty(),
            onClick = onGenresClick,
            label = {
                Text(
                    text = if (state.filter.genreIds.isEmpty())
                        stringResource(R.string.filter_genres)
                    else stringResource(R.string.filter_genres_count, state.filter.genreIds.size),
                    maxLines = 1
                )
            }
        )

        // ---------- Страны ----------
        FilterChip(
            selected = state.filter.excludedCountries.isNotEmpty() ||
                    state.filter.includedCountries.isNotEmpty(),
            onClick = onCountriesClick,
            label = {
                val text = when {
                    state.filter.excludedCountries.isNotEmpty() ->
                        stringResource(R.string.filter_excluded_count, state.filter.excludedCountries.size)
                    state.filter.includedCountries.isNotEmpty() ->
                        stringResource(R.string.filter_included_count, state.filter.includedCountries.size)
                    else -> stringResource(R.string.filter_countries)
                }
                Text(text = text, maxLines = 1)
            }
        )

        // ---------- Сортировка ----------
        Box {
            FilterChip(
                selected = state.filter.sort != SortOption.POPULARITY,
                onClick = { sortMenuOpen = true },
                label = {
                    val sortLabels = mapOf(
                        SortOption.POPULARITY to stringResource(R.string.filter_sort_popularity),
                        SortOption.RATING to stringResource(R.string.filter_sort_rating),
                        SortOption.NEWEST to stringResource(R.string.filter_sort_newest),
                        SortOption.OLDEST to stringResource(R.string.filter_sort_oldest),
                        SortOption.VOTES to stringResource(R.string.filter_sort_votes)
                    )
                    Text(
                        text = sortLabels[state.filter.sort] ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false }
            ) {
                val sortLabels = mapOf(
                    SortOption.POPULARITY to stringResource(R.string.filter_sort_popularity),
                    SortOption.RATING to stringResource(R.string.filter_sort_rating),
                    SortOption.NEWEST to stringResource(R.string.filter_sort_newest),
                    SortOption.OLDEST to stringResource(R.string.filter_sort_oldest),
                    SortOption.VOTES to stringResource(R.string.filter_sort_votes)
                )
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabels[option] ?: option.name) },
                        onClick = {
                            onSortSelected(option)
                            sortMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}