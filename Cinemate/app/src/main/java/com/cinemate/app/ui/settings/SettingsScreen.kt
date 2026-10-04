package com.cinemate.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemate.app.BuildConfig
import com.cinemate.app.R
import com.cinemate.app.domain.model.ContentLanguage
import com.cinemate.app.domain.model.HomeViewMode
import com.cinemate.app.domain.model.ThemeMode
import com.cinemate.app.util.SettingsBackup
import kotlinx.coroutines.launch

private const val TMDB_SITE_URL = "https://www.themoviedb.org/"
private const val AUTHOR_EMAIL = "kanskvladm@mail.ru"

private val appLanguages = listOf(
    "system" to "Как в системе",
    "ru" to "Русский",
    "en" to "English",
    "es" to "Español",
    "de" to "Deutsch",
    "it" to "Italiano",
    "fr" to "Français",
    "be" to "Беларуская",
    "kk" to "Қазақша",
    "zh" to "中文"
)

@Composable
fun SettingsScreen(
    onOpenWatched: () -> Unit = { },
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val userToken by viewModel.userToken.collectAsStateWithLifecycle()
    val homeViewMode by viewModel.homeViewMode.collectAsStateWithLifecycle()
    val contentLanguage by viewModel.contentLanguage.collectAsStateWithLifecycle()
    val showOriginalTitles by viewModel.showOriginalTitles.collectAsStateWithLifecycle()
    val proxyMode by viewModel.proxyMode.collectAsStateWithLifecycle()
    val proxyList by viewModel.proxyList.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val imageCacheMb by viewModel.imageCacheMb.collectAsStateWithLifecycle()
    val cacheStatus by viewModel.cacheStatus.collectAsStateWithLifecycle()
    val cacheUsedMb by viewModel.cacheUsedMb.collectAsStateWithLifecycle()
    val netMode by viewModel.netMode.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val checkingUpdate by viewModel.checkingUpdate.collectAsStateWithLifecycle()
    val dnsMode by viewModel.dnsMode.collectAsStateWithLifecycle()
    val dnsList by viewModel.dnsList.collectAsStateWithLifecycle()
    val dnsActive by viewModel.dnsActive.collectAsStateWithLifecycle()
    val testingDns by viewModel.testingDns.collectAsStateWithLifecycle()
    val dnsTestResults by viewModel.dnsTestResults.collectAsStateWithLifecycle()
    val testingProxies by viewModel.testingProxies.collectAsStateWithLifecycle()
    val proxyTestResults by viewModel.proxyTestResults.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var themeExpanded by remember { mutableStateOf(false) }
    var homeViewExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var contentLangExpanded by remember { mutableStateOf(false) }
    var netModeExpanded by remember { mutableStateOf(false) }
    var dnsExpanded by remember { mutableStateOf(false) }
    var proxyExpanded by remember { mutableStateOf(false) }
    var watchedExpanded by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    var apiKeyExpanded by remember { mutableStateOf(false) }
    var tvWatchExpanded by remember { mutableStateOf(false) }
    var helpExpanded by remember { mutableStateOf(false) }

    var backupStatus by remember { mutableStateOf<String?>(null) }
    var cacheText by remember(imageCacheMb) { mutableStateOf(imageCacheMb.toString()) }
    LaunchedEffect(Unit) { viewModel.refreshCacheUsage() }

    val savedMsg = stringResource(R.string.backup_saved)
    val restoredMsg = stringResource(R.string.backup_restored)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SettingsBackup.MIME_TYPE)
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupStatus = viewModel.backup.exportTo(uri)?.let { "❌ $it" } ?: savedMsg
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupStatus = viewModel.backup.importFrom(uri)?.let { "❌ $it" } ?: restoredMsg
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---------- Тема ----------
        ExpandableSection(
            title = stringResource(R.string.settings_theme),
            expanded = themeExpanded,
            onToggle = { themeExpanded = !themeExpanded },
            subtitle = when (themeMode) {
                ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
            }
        ) {
            ThemeOption(
                title = stringResource(R.string.settings_theme_dark),
                selected = themeMode == ThemeMode.DARK,
                onSelect = { viewModel.setThemeMode(ThemeMode.DARK) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_theme_light),
                selected = themeMode == ThemeMode.LIGHT,
                onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_theme_system),
                selected = themeMode == ThemeMode.SYSTEM,
                onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Язык приложения ----------
        ExpandableSection(
            title = stringResource(R.string.settings_language),
            expanded = languageExpanded,
            onToggle = { languageExpanded = !languageExpanded },
            subtitle = appLanguages.firstOrNull { it.first == appLanguage }?.second ?: "Как в системе"
        ) {
            appLanguages.forEach { (code, label) ->
                ThemeOption(
                    title = label,
                    selected = appLanguage == code,
                    onSelect = { viewModel.setAppLanguage(code) }
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Вид главной ----------
        ExpandableSection(
            title = stringResource(R.string.settings_home_view),
            expanded = homeViewExpanded,
            onToggle = { homeViewExpanded = !homeViewExpanded },
            subtitle = when (homeViewMode) {
                HomeViewMode.GRID2 -> stringResource(R.string.settings_home_view_grid2_short)
                HomeViewMode.GRID -> stringResource(R.string.settings_home_view_grid3_short)
                HomeViewMode.LIST -> stringResource(R.string.settings_home_view_list_short)
            }
        ) {
            ThemeOption(
                title = stringResource(R.string.settings_home_view_grid2),
                selected = homeViewMode == HomeViewMode.GRID2,
                onSelect = { viewModel.setHomeViewMode(HomeViewMode.GRID2) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_home_view_grid3),
                selected = homeViewMode == HomeViewMode.GRID,
                onSelect = { viewModel.setHomeViewMode(HomeViewMode.GRID) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_home_view_list),
                selected = homeViewMode == HomeViewMode.LIST,
                onSelect = { viewModel.setHomeViewMode(HomeViewMode.LIST) }
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Язык описаний (TMDb) ----------
        ExpandableSection(
            title = stringResource(R.string.section_content_lang),
            expanded = contentLangExpanded,
            onToggle = { contentLangExpanded = !contentLangExpanded },
            subtitle = contentLanguage.label
        ) {
            ContentLanguage.entries.forEach { lang ->
                ThemeOption(
                    title = lang.label,
                    selected = contentLanguage == lang,
                    onSelect = { viewModel.setContentLanguage(lang) }
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Интернет для каталога ----------
        ExpandableSection(
            title = stringResource(R.string.net_mode_title),
            expanded = netModeExpanded,
            onToggle = { netModeExpanded = !netModeExpanded },
            subtitle = when (netMode) {
                "cellular" -> stringResource(R.string.net_mode_cellular)
                else -> stringResource(R.string.net_mode_wifi)
            }
        ) {
            ThemeOption(
                title = stringResource(R.string.net_mode_wifi),
                selected = netMode == "wifi",
                onSelect = { viewModel.setCatalogNetMode("wifi") }
            )
            ThemeOption(
                title = stringResource(R.string.net_mode_cellular),
                selected = netMode == "cellular",
                onSelect = { viewModel.setCatalogNetMode("cellular") }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.net_mode_cellular_warn),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (netMode == "cellular") {
                val cellularState by viewModel.cellularAvailable.collectAsStateWithLifecycle()
    val netDiag by viewModel.netDiag.collectAsStateWithLifecycle()
                Text(
                    when (cellularState) {
                        true -> stringResource(R.string.net_mode_cellular_active)
                        false -> stringResource(R.string.net_mode_cellular_unavailable)
                        null -> stringResource(R.string.net_mode_cellular_waiting)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cellularState == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                if (netDiag.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            netDiag.forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- DNS ----------
        ExpandableSection(
            title = stringResource(R.string.section_dns),
            expanded = dnsExpanded,
            onToggle = { dnsExpanded = !dnsExpanded },
            subtitle = when (dnsMode) {
                "custom" -> stringResource(R.string.dns_subtitle_custom, dnsActive ?: "—")
                "system" -> stringResource(R.string.dns_subtitle_off)
                else -> stringResource(R.string.dns_subtitle_auto)
            }
        ) {
            DnsSection(
                mode = dnsMode,
                dnsListJson = dnsList,
                testing = testingDns,
                testResults = dnsTestResults,
                onMode = viewModel::setDnsMode,
                onAdd = viewModel::addDns,
                onRemove = viewModel::removeDns,
                onTest = viewModel::runDnsTest,
                onDismissResults = viewModel::dismissDnsTest
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Прокси TMDb ----------
        ExpandableSection(
            title = stringResource(R.string.proxy_title),
            expanded = proxyExpanded,
            onToggle = { proxyExpanded = !proxyExpanded },
            subtitle = when (proxyMode) {
                "always" -> stringResource(R.string.proxy_mode_always_short)
                "off" -> stringResource(R.string.proxy_mode_off_short)
                else -> stringResource(R.string.proxy_mode_auto_short)
            }
        ) {
            ProxySection(
                mode = proxyMode,
                list = proxyList,
                testing = testingProxies,
                testResults = proxyTestResults,
                onMode = viewModel::setProxyMode,
                onAdd = viewModel::addProxy,
                onRemove = viewModel::removeProxy,
                onTest = viewModel::runProxyTest,
                onDismissTest = viewModel::dismissProxyTest
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Смотрел ----------
        ExpandableSection(
            title = stringResource(R.string.watched_title),
            expanded = watchedExpanded,
            onToggle = { watchedExpanded = !watchedExpanded },
            titleColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                stringResource(R.string.watched_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenWatched) {
                Text(stringResource(R.string.watched_open))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Бэкап настроек ----------
        ExpandableSection(
            title = stringResource(R.string.backup_title),
            expanded = backupExpanded,
            onToggle = { backupExpanded = !backupExpanded }
        ) {
            Text(
                stringResource(R.string.backup_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { exportLauncher.launch(SettingsBackup.suggestedFileName()) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.backup_export)) }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.backup_import)) }
            }
            backupStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it.startsWith("✅")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Кэш картинок (шторка) ----------
        var cacheExpanded by remember { mutableStateOf(false) }
        ExpandableSection(
            title = stringResource(R.string.cache_title),
            expanded = cacheExpanded,
            onToggle = {
                cacheExpanded = !cacheExpanded
                if (cacheExpanded) viewModel.refreshCacheUsage()
            },
            subtitle = stringResource(R.string.cache_usage, cacheUsedMb, imageCacheMb)
        ) {
            Text(
                stringResource(R.string.cache_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = cacheText,
                    onValueChange = { cacheText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.cache_title)) },
                    suffix = { Text(stringResource(R.string.cache_mb)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { cacheText.toIntOrNull()?.let { viewModel.applyCacheSize(it) } },
                    enabled = (cacheText.toIntOrNull() ?: 0) in 10..500
                ) { Text(stringResource(R.string.cache_apply), maxLines = 1) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.clearImageCache() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.cache_clear)) }
            cacheStatus?.let { st ->
                Spacer(Modifier.height(6.dp))
                Text(
                    st,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Оригинальные названия ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_original_titles),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showOriginalTitles,
                onCheckedChange = { viewModel.setShowOriginalTitles(it) }
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Просмотр на ТВ (акцент) ----------
        ExpandableSection(
            title = stringResource(R.string.tv_section_title),
            expanded = tvWatchExpanded,
            onToggle = { tvWatchExpanded = !tvWatchExpanded },
            titleColor = MaterialTheme.colorScheme.primary
        ) {
            TvWatchSection(viewModel = hiltViewModel())
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Свой API-ключ ----------
        ExpandableSection(
            title = stringResource(R.string.settings_api_key_title),
            expanded = apiKeyExpanded,
            onToggle = { apiKeyExpanded = !apiKeyExpanded },
            subtitle = if (userToken.isNullOrBlank())
                stringResource(R.string.settings_api_key_using_default)
            else
                stringResource(R.string.settings_api_key_using_custom)
        ) {
            ApiKeySection(
                userToken = userToken,
                onSave = viewModel::saveApiKey,
                onReset = viewModel::resetApiKey
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Справка ----------
        ExpandableSection(
            title = stringResource(R.string.settings_help),
            expanded = helpExpanded,
            onToggle = { helpExpanded = !helpExpanded }
        ) {
            HelpSection()
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---------- Обновление приложения ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.update_check_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { viewModel.checkUpdateNow() },
                enabled = !checkingUpdate
            ) {
                if (checkingUpdate) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.update_check_now), maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Статус ручной проверки — виден всегда (обновлений нет / сбой)
        updateStatus?.let { st ->
            Text(
                st,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        updateInfo?.let { upd ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.update_available, upd.versionName),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (upd.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            upd.releaseNotes,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.downloadAndInstall() },
                            enabled = !downloading
                        ) {
                            if (downloading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.update_download_install))
                        }
                    }
                    updateStatus?.let { st ->
                        Spacer(Modifier.height(4.dp))
                        Text(st, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---------- О приложении ----------
        Text(
            stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Cinemate ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(R.string.settings_app_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.settings_author),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$AUTHOR_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "Cinemate — отзыв о приложении")
                }
                runCatching { context.startActivity(intent) }
            }
        ) {
            Text(stringResource(R.string.settings_write_author))
        }

        Spacer(Modifier.height(24.dp))

        Text(
            stringResource(R.string.settings_data_by),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))

        Image(
            painter = painterResource(R.drawable.tmdb_logo),
            contentDescription = "TMDb",
            modifier = Modifier
                .size(width = 120.dp, height = 50.dp)
                .clickable { uriHandler.openUri(TMDB_SITE_URL) }
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.tmdb_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { uriHandler.openUri(TMDB_SITE_URL) }
        )
    }
}

// ---------- Подсекции ----------

/** Секция «Прокси TMDb»: режим + список своих прокси + тест прокси. */
@Composable
private fun ProxySection(
    mode: String,
    list: List<String>,
    testing: Boolean,
    testResults: List<String>?,
    onMode: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onTest: () -> Unit,
    onDismissTest: () -> Unit
) {
    var newText by remember { mutableStateOf("") }

    Column {
        ThemeOption(
            title = stringResource(R.string.proxy_mode_auto),
            selected = mode == "auto",
            onSelect = { onMode("auto") }
        )
        ThemeOption(
            title = stringResource(R.string.proxy_mode_always),
            selected = mode == "always",
            onSelect = { onMode("always") }
        )
        ThemeOption(
            title = stringResource(R.string.proxy_mode_off),
            selected = mode == "off",
            onSelect = { onMode("off") }
        )

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.proxy_custom_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.proxy_custom_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (list.isEmpty()) {
            Text(
                stringResource(R.string.proxy_list_empty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        list.forEach { origin ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    origin,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(origin) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.proxy_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it.trim() },
                label = { Text(stringResource(R.string.proxy_address)) },
                placeholder = { Text("my-proxy.workers.dev") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onAdd(newText)
                    newText = ""
                },
                enabled = newText.isNotBlank()
            ) { Text(stringResource(R.string.proxy_add)) }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onTest,
                enabled = !testing
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.proxy_test_button))
            }
        }

        testResults?.let { results ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    results.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (line.contains("✅")) MaterialTheme.colorScheme.primary
                            else if (line.contains("❌")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    TextButton(onClick = onDismissTest) { Text(stringResource(R.string.common_hide)) }
                }
            }
        }
    }
}

/** Секция «DNS»: Авто/Свой. */
@Composable
private fun DnsSection(
    mode: String,
    dnsListJson: String,
    testing: Boolean,
    testResults: List<String>?,
    onMode: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onTest: () -> Unit,
    onDismissResults: () -> Unit
) {
    var newText by remember { mutableStateOf("") }

    Column {
        Text(
            stringResource(R.string.dns_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        ThemeOption(
            title = stringResource(R.string.dns_mode_auto),
            selected = mode == "auto",
            onSelect = { onMode("auto") }
        )
        ThemeOption(
            title = stringResource(R.string.dns_mode_custom),
            selected = mode == "custom",
            onSelect = { onMode("custom") }
        )

        ThemeOption(
            title = stringResource(R.string.dns_mode_off),
            selected = mode == "system",
            onSelect = { onMode("system") }
        )

        if (mode == "custom") {
            Spacer(Modifier.height(8.dp))

            val customList = remember(dnsListJson) {
                try {
                    val arr = org.json.JSONArray(dnsListJson)
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optString(i).takeIf(String::isNotBlank)
                    }
                } catch (e: Exception) { emptyList<String>() }
            }

            if (customList.isEmpty()) {
                Text(
                    stringResource(R.string.dns_list_empty),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            customList.forEach { dns ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dns,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemove(dns) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text(stringResource(R.string.dns_add_label)) },
                    placeholder = { Text(stringResource(R.string.dns_add_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onAdd(newText.trim())
                        newText = ""
                    },
                    enabled = newText.isNotBlank()
                ) { Text(stringResource(R.string.btn_add)) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Тест DNS — доступен в обоих режимах
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onTest,
                enabled = !testing
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.dns_test_button))
            }
        }

        testResults?.let { results ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    results.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (line.contains("✅")) MaterialTheme.colorScheme.primary
                            else if (line.contains("❌")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    TextButton(onClick = onDismissResults) { Text(stringResource(R.string.common_hide)) }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.dns_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    titleColor: Color? = null,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Скрыть" else "Показать",
            tint = titleColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(4.dp))
    AnimatedVisibility(visible = expanded) {
        Column(Modifier.padding(top = 4.dp)) {
            content()
        }
    }
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

@Composable
private fun ApiKeySection(
    userToken: String?,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    val savedIsV4 = userToken != null && userToken.startsWith("eyJ")
    var v4Text by remember(userToken) { mutableStateOf(if (savedIsV4) userToken.orEmpty() else "") }
    var v3Text by remember(userToken) { mutableStateOf(if (!savedIsV4 && userToken != null) userToken.orEmpty() else "") }
    var savedJustNow by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = v4Text,
            onValueChange = {
                v4Text = it
                savedJustNow = false
                if (it.isNotBlank()) v3Text = ""
            },
            label = { Text(stringResource(R.string.settings_api_key_v4)) },
            placeholder = { Text("eyJhbGciOiJIUzI1NiJ9...") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            stringResource(R.string.settings_api_key_or),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        OutlinedTextField(
            value = v3Text,
            onValueChange = {
                v3Text = it
                savedJustNow = false
                if (it.isNotBlank()) v4Text = ""
            },
            label = { Text(stringResource(R.string.settings_api_key_v3)) },
            placeholder = { Text("12dc17080ad7a014bec7865237f84864") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_api_key_one_field),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Row {
            Button(
                onClick = {
                    onSave(v4Text.trim().ifBlank { v3Text.trim() })
                    savedJustNow = true
                },
                enabled = v4Text.isNotBlank() || v3Text.isNotBlank()
            ) { Text(stringResource(R.string.settings_api_key_save)) }

            Spacer(Modifier.size(8.dp))

            OutlinedButton(
                onClick = {
                    onReset()
                    v4Text = ""
                    v3Text = ""
                    savedJustNow = true
                },
                enabled = !userToken.isNullOrBlank()
            ) { Text(stringResource(R.string.settings_api_key_reset)) }
        }

        if (savedJustNow) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_api_key_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun HelpSection() {
    val b = "• "
    val main = stringArrayResource(R.array.help_main)
    val bypass = stringArrayResource(R.array.help_bypass)
    val tv = stringArrayResource(R.array.help_tv)

    Column {
        Text(
            stringResource(R.string.help_main_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        HelpArrayItems(b, main)

        Text(
            stringResource(R.string.help_bypass_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        HelpArrayItems(b, bypass)

        Text(
            stringResource(R.string.help_tv_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        HelpArrayItems(b, tv)
    }
}

@Composable
private fun HelpArrayItems(bullet: String, items: Array<String>) {
    var i = 0
    while (i + 1 < items.size) {
        HelpItem(bullet, items[i], items[i + 1])
        i += 2
    }
}

@Composable
private fun HelpItem(bullet: String, title: String, body: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            bullet + title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}