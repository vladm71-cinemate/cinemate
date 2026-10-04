package com.cinemate.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemate.app.R
import com.cinemate.app.domain.model.PlayerPresets

@Composable
fun TvWatchSection(
    viewModel: TvWatchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val playOnPhone = state.playOnPhone
    var pickingForButton by remember { mutableStateOf<Int?>(null) }
    var addNodeOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.enabled) {
        if (state.enabled) viewModel.checkConnection(silent = true)
    }

    Column {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.tv_enable_switch),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.enabled,
                onCheckedChange = { viewModel.setEnabled(it) }
            )
        }

        if (!state.enabled) {
            Text(
                stringResource(R.string.tv_enable_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Куда играть («Смотреть») ----------
        Text(stringResource(R.string.tv_play_where), style = MaterialTheme.typography.titleMedium)
        ThemeOption(
            title = stringResource(R.string.tv_play_where_box),
            selected = !playOnPhone,
            onSelect = { viewModel.setPlayOnPhone(false) }
        )
        ThemeOption(
            title = stringResource(R.string.tv_play_where_phone),
            selected = playOnPhone,
            onSelect = { viewModel.setPlayOnPhone(true) }
        )
        Spacer(Modifier.height(12.dp))

        // Индикатор соединения (актуален только для режима бокса)
        if (!playOnPhone) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ConnectionDot(status = connectionStatusOf(state))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = when {
                        state.checking -> stringResource(R.string.tv_connection_checking)
                        state.checkSuccess -> stringResource(R.string.tv_connection_ok)
                        state.checkResult != null -> stringResource(R.string.tv_connection_fail)
                        else -> stringResource(R.string.tv_connection_unknown)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        // ---------- Кнопки 1-4 («пульт») ----------
        Text(stringResource(R.string.tv_eyes_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.tv_eyes_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Text(stringResource(R.string.tv_eye_button_1), style = MaterialTheme.typography.bodyMedium)
        PlayerPickerRow(
            assigned = state.player1,
            label = viewModel.playerLabel(state.player1),
            onPickClick = { pickingForButton = 1 }
        )
        Text(stringResource(R.string.tv_eye_button_2), style = MaterialTheme.typography.bodyMedium)
        PlayerPickerRow(
            assigned = state.player2,
            label = viewModel.playerLabel(state.player2),
            onPickClick = { pickingForButton = 2 }
        )
        Text(stringResource(R.string.tv_eye_button_3), style = MaterialTheme.typography.bodyMedium)
        PlayerPickerRow(
            assigned = state.player3,
            label = viewModel.playerLabel(state.player3),
            onPickClick = { pickingForButton = 3 }
        )
        Text(stringResource(R.string.tv_eye_button_4), style = MaterialTheme.typography.bodyMedium)
        PlayerPickerRow(
            assigned = state.player4,
            label = viewModel.playerLabel(state.player4),
            onPickClick = { pickingForButton = 4 }
        )
        Spacer(Modifier.height(12.dp))

        // ---------- Адрес бокса (актуален только для режима бокса) ----------
        if (!playOnPhone) {
            Text(stringResource(R.string.tv_box_address_title), style = MaterialTheme.typography.titleMedium)

            AddressRow(
                label = stringResource(R.string.tv_box_address_main),
                ip = state.boxWifi,
                port = state.boxPort,
                ipPlaceholder = "192.168.1.8",
                portPlaceholder = "8080",
                onIp = viewModel::setBoxWifi,
                onPort = viewModel::setBoxPort,
                onClear = viewModel::clearMainAddress
            )
            Spacer(Modifier.height(8.dp))
            AddressRow(
                label = stringResource(R.string.tv_box_address_spare),
                ip = state.boxLan,
                port = state.boxLanPort,
                ipPlaceholder = "192.168.1.4",
                portPlaceholder = "8080",
                onIp = viewModel::setBoxLan,
                onPort = viewModel::setBoxLanPort,
                onClear = viewModel::clearSpareAddress
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tv_box_address_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Автопоиск + Проверить: фикс ширины — weight не даёт кнопкам схлопнуться
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.discovering) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.tv_discover_scanning), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { viewModel.stopDiscovery() }) {
                        Text(stringResource(R.string.tv_discover_stop))
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.startDiscovery() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.tv_discover),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { viewModel.checkConnection() },
                        enabled = !state.checking,
                        modifier = Modifier.weight(0.6f)
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                stringResource(R.string.tv_check_button),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Найденные боксы: крупные кнопки, тап = применить + сразу проверить
            if (state.discovered.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                state.discovered.forEach { box ->
                    Button(
                        onClick = { viewModel.useDiscoveredBox(box) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.tv_discover_use, "${box.address}:${box.port}"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            } else if (state.discovering) {
                Text(
                    stringResource(R.string.tv_discover_scan_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.checkResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.checkSuccess) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Поведение ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.tv_close_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.restartPlayer,
                    onCheckedChange = { viewModel.setRestartPlayer(it) }
                )
            }
            Text(
                stringResource(R.string.tv_close_apps_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // ---------- Список раздач: где показывать ----------
            Text(stringResource(R.string.tv_torrents_where), style = MaterialTheme.typography.titleMedium)
            ThemeOption(
                title = stringResource(R.string.tv_torrents_where_phone),
                selected = !state.torrentsOnTv,
                onSelect = { viewModel.setTorrentsOnTv(false) }
            )
            ThemeOption(
                title = stringResource(R.string.tv_torrents_where_tv),
                selected = state.torrentsOnTv,
                onSelect = { viewModel.setTorrentsOnTv(true) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Поиск раздач ----------
        Text(stringResource(R.string.tv_search_mode_title), style = MaterialTheme.typography.titleMedium)
        ThemeOption(
            title = stringResource(R.string.tv_search_mode_title_only),
            selected = searchMode == "title",
            onSelect = { viewModel.setSearchMode("title") }
        )
        ThemeOption(
            title = stringResource(R.string.tv_search_mode_title_year),
            selected = searchMode == "title_year",
            onSelect = { viewModel.setSearchMode("title_year") }
        )

        Spacer(Modifier.height(12.dp))

        // ---------- Ноды раздач ----------
        Text(stringResource(R.string.tv_nodes_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.tv_nodes_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        val activeNode = state.selectedNode ?: state.nodes.firstOrNull()?.baseUrl
        state.nodes.forEach { node ->
            val isSelected = node.baseUrl == activeNode
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { viewModel.selectNode(node.baseUrl) }
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { viewModel.selectNode(node.baseUrl) }
                ) {
                    Text(node.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        buildString {
                            append(node.baseUrl)
                            if (node.builtIn) append("  ·  ").append(stringResource(R.string.tv_nodes_builtin))
                            state.nodeCheckResults[node.baseUrl]?.let { append("  ·  ").append(it) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!node.builtIn) {
                    IconButton(onClick = { viewModel.removeNode(node.baseUrl) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.tv_delete_node),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { addNodeOpen = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tv_nodes_add), maxLines = 1)
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = { viewModel.checkNodes() },
                enabled = !state.checkingNodes,
                modifier = Modifier.weight(1f)
            ) {
                if (state.checkingNodes) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.tv_nodes_check_all), maxLines = 1)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.tv_nodes_autoswitch),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.nodeAutoSwitch,
                onCheckedChange = { viewModel.setNodeAutoSwitch(it) }
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tv_requires_receiver),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ---------- Диалог назначения приложения на кнопку ----------
    pickingForButton?.let { buttonNumber ->
        PlayerPickDialog(
            current = when (buttonNumber) {
                1 -> state.player1
                2 -> state.player2
                3 -> state.player3
                else -> state.player4
            },
            boxApps = state.boxApps,
            boxAppsLoading = state.loadingBoxApps,
            boxAppsError = state.boxAppsError,
            onLoadBoxApps = { viewModel.loadBoxApps() },
            onPick = { packageName ->
                viewModel.assignPlayer(buttonNumber, packageName)
                pickingForButton = null
            },
            onDismiss = { pickingForButton = null }
        )
    }

    // ---------- Диалог добавления ноды ----------
    if (addNodeOpen) {
        AddNodeDialog(
            onAdd = { name, url, key, onError ->
                viewModel.addNode(name, url, key, onError)
            },
            onDismiss = { addNodeOpen = false }
        )
    }
}

private enum class ConnectionStatus { ONLINE, OFFLINE, CHECKING, UNKNOWN }

private fun connectionStatusOf(state: TvWatchUiState): ConnectionStatus = when {
    state.checking -> ConnectionStatus.CHECKING
    state.checkSuccess -> ConnectionStatus.ONLINE
    state.checkResult != null -> ConnectionStatus.OFFLINE
    else -> ConnectionStatus.UNKNOWN
}

@Composable
private fun ConnectionDot(status: ConnectionStatus) {
    val color = when (status) {
        ConnectionStatus.ONLINE -> Color(0xFF2E9E4F)
        ConnectionStatus.OFFLINE -> Color(0xFFC62828)
        ConnectionStatus.CHECKING -> Color(0xFFFFB43A)
        ConnectionStatus.UNKNOWN -> Color(0xFF9E9E9E)
    }
    if (status == ConnectionStatus.CHECKING) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
    } else {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color = color, shape = CircleShape)
        )
    }
}

@Composable
private fun PlayerPickerRow(
    assigned: String?,
    label: String,
    onPickClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onPickClick) {
            Text(
                if (assigned == null) stringResource(R.string.tv_eye_assign)
                else stringResource(R.string.tv_eye_change)
            )
        }
    }
}

@Composable
private fun PlayerPickDialog(
    current: String?,
    boxApps: List<BoxAppUi>,
    boxAppsLoading: Boolean,
    boxAppsError: String?,
    onLoadBoxApps: () -> Unit,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadBoxApps()
    }

    if (showCustomInput) {
        CustomPackageDialog(
            initial = current.orEmpty(),
            onDone = { pkg ->
                onPick(pkg)
            },
            onDismiss = { showCustomInput = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tv_pick_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = current == null,
                        onClick = { onPick(null) }
                    )
                    Text(stringResource(R.string.tv_eye_unassigned))
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tv_pick_from_box),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    boxAppsLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.tv_pick_from_box_loading),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    boxAppsError != null -> Column {
                        Text(
                            boxAppsError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { onLoadBoxApps() }) {
                            Text(stringResource(R.string.tv_pick_repeat))
                        }
                    }
                    boxApps.isEmpty() -> Text(
                        stringResource(R.string.tv_pick_from_box_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> {
                        boxApps.forEach { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = current == app.packageName,
                                    onClick = { onPick(app.packageName) }
                                )
                                Column {
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tv_pick_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PlayerPresets.ALL.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = current == preset.packageName,
                            onClick = { onPick(preset.packageName) }
                        )
                        Column {
                            Text(preset.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                preset.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomInput = true }
                ) {
                    RadioButton(
                        selected = false,
                        onClick = { showCustomInput = true }
                    )
                    Text(stringResource(R.string.tv_pick_custom))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_pick_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_pick_cancel)) }
        }
    )
}

/** Компактный диалог ручного ввода package name. */
@Composable
private fun CustomPackageDialog(
    initial: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Package name") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onDone(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(stringResource(R.string.tv_pick_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_pick_cancel)) }
        }
    )
}

/** Диалог добавления своей ноды. */
@Composable
private fun AddNodeDialog(
    onAdd: (name: String, baseUrl: String, apiKey: String, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tv_node_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tv_node_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.tv_node_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.tv_node_address_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.tv_node_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(name, url, key) { err -> error = err }
                },
                enabled = url.isNotBlank()
            ) { Text(stringResource(R.string.tv_node_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_pick_cancel)) }
        }
    )
}

@Composable
private fun ThemeOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Строка адреса: [IP — тянется] [порт] [метла].
 * Порт у каждого поля свой (боксы могут быть на разных портах).
 * Метла очищает IP и порт своего поля.
 */
@Composable
private fun AddressRow(
    label: String,
    ip: String?,
    port: String,
    ipPlaceholder: String,
    portPlaceholder: String,
    onIp: (String) -> Unit,
    onPort: (String) -> Unit,
    onClear: () -> Unit
) {
    var ipText by remember(ip) { mutableStateOf(ip.orEmpty()) }
    var portText by remember(port) { mutableStateOf(port) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ipText,
                onValueChange = {
                    ipText = it
                    onIp(it.trim())
                },
                placeholder = { Text(ipPlaceholder) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = portText,
                onValueChange = {
                    val digits = it.filter { ch -> ch.isDigit() }.take(5)
                    portText = digits
                    onPort(digits)
                },
                placeholder = { Text(portPlaceholder) },
                singleLine = true,
                modifier = Modifier.width(92.dp)
            )
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.CleaningServices,
                    contentDescription = stringResource(R.string.cd_clear_field),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
