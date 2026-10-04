package com.cinemate.app.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cinemate.app.R
import com.cinemate.app.domain.model.Countries

/**
 * Диалог фильтра стран: две вкладки — «Скрыть» и «Только».
 * Локализованные названия стран — из ресурсов (arrays.xml country_names);
 * порядок стран в Countries.ALL строго соответствует порядку массива.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountryDialog(
    excludedCountries: Set<String>,
    includedCountries: Set<String>,
    onToggleExcluded: (String) -> Unit,
    onToggleIncluded: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Локализованные названия стран: индекс совпадает с Countries.ALL
    val countryNames = stringArrayResource(R.array.country_names)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_countries)) },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.filter_excluded_count, excludedCountries.size)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.filter_included_count, includedCountries.size)) }
                    )
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Countries.ALL.forEachIndexed { index, country ->
                        // Название из ресурсов; если индекс вышел за пределы — код
                        val name = countryNames.getOrNull(index) ?: country.code
                        val selected = if (selectedTab == 0)
                            country.code in excludedCountries
                        else
                            country.code in includedCountries

                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selectedTab == 0) onToggleExcluded(country.code)
                                else onToggleIncluded(country.code)
                            },
                            label = { Text(name) }
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
                onClick = onClear,
                enabled = excludedCountries.isNotEmpty() || includedCountries.isNotEmpty()
            ) { Text(stringResource(R.string.search_history_clear)) }
        }
    )
}