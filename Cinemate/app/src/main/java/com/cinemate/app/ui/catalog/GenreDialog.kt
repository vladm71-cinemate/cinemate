package com.cinemate.app.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cinemate.app.R
import com.cinemate.app.domain.model.Genre

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreDialog(
    genres: List<Genre>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_genres)) },
        text = {
            if (genres.isEmpty()) {
                Text(stringResource(R.string.catalog_error, "жанры"))
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genres.forEach { genre ->
                        FilterChip(
                            selected = genre.id in selectedIds,
                            onClick = { onToggle(genre.id) },
                            label = { Text(genre.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_pick_done)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onClear(); onDismiss() },
                enabled = selectedIds.isNotEmpty()
            ) { Text(stringResource(R.string.search_history_clear)) }
        }
    )
}