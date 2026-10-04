package com.cinemate.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemate.app.R
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.ui.components.PosterCard

@Composable
fun SearchScreen(
    onOpenDetails: (String, Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {

        // ---------- Переключатель: Все | Фильмы | Сериалы ----------
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = !state.searchAll && state.contentType == ContentType.MOVIE,
                onClick = { viewModel.setContentType(ContentType.MOVIE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text(
                    stringResource(R.string.filter_movies),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SegmentedButton(
                selected = state.searchAll,
                onClick = { viewModel.setAllMode() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text(
                    stringResource(R.string.search_all),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SegmentedButton(
                selected = !state.searchAll && state.contentType == ContentType.TV,
                onClick = { viewModel.setContentType(ContentType.TV) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text(
                    stringResource(R.string.filter_tv),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ---------- Строка поиска ----------
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_history_clear))
                    }
                }
            },
            singleLine = true
        )

        Text(
            stringResource(R.string.search_language_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ---------- Содержимое ----------
        when {
            state.query.isBlank() -> {
                SearchHistoryListComposable(
                    history = history,
                    onItemClick = viewModel::onHistoryItemClicked,
                    onClear = viewModel::clearHistory
                )
            }

            state.loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.search_error, state.error ?: ""))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
                }
            }

            state.searched && state.results.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.results, key = { it.id }) { movie ->
                    // В ALL-режиме тип из resultTypes; в режимах Фильмы/Сериалы — из contentType
                    val isTv = if (state.searchAll) {
                        state.resultTypes[movie.id] ?: false
                    } else {
                        state.contentType == ContentType.TV
                    }
                    PosterCard(
                        movie = movie,
                        onClick = {
                            viewModel.onResultClicked()
                            onOpenDetails(if (isTv) "tv" else "movie", movie.id)
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun SearchHistoryListComposable(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.search_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.search_history_title),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.search_history_clear)) }
        }

        Column(Modifier.fillMaxSize()) {
            history.forEach { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        query,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}