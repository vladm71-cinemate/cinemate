package com.cinemate.app.ui.settings

import androidx.lifecycle.ViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.cinemate.app.R
import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.data.remote.ApiKeyProvider
import com.cinemate.app.data.remote.AuthInterceptor
import com.cinemate.app.data.remote.CatalogNetworkManager
import com.cinemate.app.data.remote.DnsManager
import com.cinemate.app.data.remote.TmdbProxyManager
import com.cinemate.app.domain.model.ContentLanguage
import com.cinemate.app.domain.model.HomeViewMode
import com.cinemate.app.domain.model.ThemeMode
import com.cinemate.app.util.SettingsBackup
import com.cinemate.app.util.UpdateChecker
import com.cinemate.app.util.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val apiKeyProvider: ApiKeyProvider,
    private val authInterceptor: AuthInterceptor,
    private val tmdbProxyManager: TmdbProxyManager,
    private val dnsManager: DnsManager,
    private val networkManager: CatalogNetworkManager,
    val backup: SettingsBackup,
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // ---------- Тема ----------

    val themeMode: StateFlow<ThemeMode> = tokenStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { tokenStore.saveThemeMode(mode) }
    }

    // ---------- Вид главной ----------

    val homeViewMode: StateFlow<HomeViewMode> = tokenStore.homeViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewMode.GRID)

    fun setHomeViewMode(mode: HomeViewMode) {
        viewModelScope.launch { tokenStore.saveHomeViewMode(mode) }
    }

    // ---------- Язык интерфейса ----------

    val appLanguage: StateFlow<String> = tokenStore.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    fun setAppLanguage(lang: String) {
        viewModelScope.launch { tokenStore.saveAppLanguage(lang) }
    }

    // ---------- Интернет для каталога ----------

    val netMode: StateFlow<String> = tokenStore.catalogNetMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogNetworkManager.MODE_WIFI)

    /** null = ожидание; true = сотовая сеть захвачена; false = недоступна. */
    val cellularAvailable: StateFlow<Boolean?> = networkManager.cellularAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val netDiag: StateFlow<List<String>> = networkManager.diag
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCatalogNetMode(mode: String) {
        viewModelScope.launch {
            tokenStore.saveCatalogNetMode(mode)
            networkManager.setMode(mode)
        }
    }

    // ---------- Кэш картинок ----------

    val imageCacheMb: StateFlow<Int> = tokenStore.imageCacheMb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)

    private val _cacheStatus = MutableStateFlow<String?>(null)
    val cacheStatus: StateFlow<String?> = _cacheStatus.asStateFlow()

    /** Применить новый размер: перезапуск процесса подхватит при создании кэша. */
    fun applyCacheSize(mb: Int) {
        viewModelScope.launch {
            tokenStore.saveImageCacheMb(mb)
            _cacheStatus.value = appContext.getString(R.string.cache_applied_restart)
        }
    }

    /** Мягкая очистка: только кэш картинок, настройки не трогаем. */
    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = java.io.File(appContext.cacheDir, "http_cache")
            val before = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            dir.deleteRecursively()
            coil.Coil.imageLoader(context = appContext).memoryCache?.clear()
            val mb = before / (1024 * 1024)
            _cacheStatus.value = appContext.getString(R.string.cache_cleared, mb)
            refreshCacheUsage()
        }
    }

    /** Текущий размер кэша, МБ (для индикатора). */
    private val _cacheUsedMb = MutableStateFlow(0)
    val cacheUsedMb: StateFlow<Int> = _cacheUsedMb.asStateFlow()

    fun refreshCacheUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = java.io.File(appContext.cacheDir, "http_cache")
            val bytes = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            _cacheUsedMb.value = (bytes / (1024 * 1024)).toInt()
        }
    }

    // ---------- Язык описаний (TMDb) ----------

    val contentLanguage: StateFlow<ContentLanguage> = tokenStore.contentLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContentLanguage.RU)

    fun setContentLanguage(language: ContentLanguage) {
        viewModelScope.launch {
            tokenStore.saveContentLanguage(language)
            authInterceptor.invalidateLanguage()
        }
    }

    // ---------- Оригинальные названия ----------

    val showOriginalTitles: StateFlow<Boolean> = tokenStore.showOriginalTitles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setShowOriginalTitles(show: Boolean) {
        viewModelScope.launch { tokenStore.saveShowOriginalTitles(show) }
    }

    // ---------- Прокси TMDb ----------

    val proxyMode: StateFlow<String> = tokenStore.proxyMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")

    /** Список своих прокси (строки-источники "https://host" / "http://host"). */
    val proxyList: StateFlow<List<String>> = tokenStore.proxyCustomList
        .map { json -> parseProxyList(json) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setProxyMode(mode: String) {
        viewModelScope.launch {
            tokenStore.saveProxyMode(mode)
            tmdbProxyManager.invalidate()
        }
    }

    /**
     * Добавить свой прокси: "my.workers.dev" (https) или "http://host".
     * Ограничений по количеству нет. Дубликат — только точное совпадение
     * полного адреса: один хост может существовать и как https, и как http.
     */
    fun addProxy(raw: String) {
        val cleaned = raw.trim().trimEnd('/')
        if (cleaned.isBlank()) return
        val origin = if (cleaned.contains("://")) cleaned else "https://$cleaned"
        viewModelScope.launch {
            val list = parseProxyList(tokenStore.proxyCustomList.first())
            if (list.none { it.equals(origin, ignoreCase = true) }) {
                tokenStore.saveProxyCustomList(org.json.JSONArray(list + origin).toString())
                // Одиночное поле-предшественник больше не используется
                tokenStore.saveProxyHost(null)
                tmdbProxyManager.invalidate()
            }
        }
    }

    /** Удалить свой прокси из списка. */
    fun removeProxy(origin: String) {
        viewModelScope.launch {
            val list = parseProxyList(tokenStore.proxyCustomList.first())
                .filterNot { it.equals(origin, ignoreCase = true) }
            tokenStore.saveProxyCustomList(org.json.JSONArray(list).toString())
            if (tokenStore.proxyLastGood.first()?.equals(origin.substringAfter("://"), ignoreCase = true) == true) {
                tokenStore.saveProxyLastGood(null)
            }
            tmdbProxyManager.invalidate()
        }
    }

    private fun parseProxyList(json: String): List<String> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).trim().takeIf(String::isNotBlank)
        }
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- Тест прокси ----------

    private val _testingProxies = MutableStateFlow(false)
    val testingProxies: StateFlow<Boolean> = _testingProxies.asStateFlow()

    private val _proxyTestResults = MutableStateFlow<List<String>?>(null)
    val proxyTestResults: StateFlow<List<String>?> = _proxyTestResults.asStateFlow()

    fun runProxyTest() {
        if (_testingProxies.value) return
        viewModelScope.launch {
            _testingProxies.value = true
            _proxyTestResults.value = null
            val results = tmdbProxyManager.testProxies()
            _proxyTestResults.value = results.map { "${it.name} — ${it.detail}" }
            _testingProxies.value = false
        }
    }

    fun dismissProxyTest() {
        _proxyTestResults.value = null
    }

    // ---------- DNS ----------

    val dnsMode: StateFlow<String> = tokenStore.dnsMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")

    val dnsList: StateFlow<String> = tokenStore.dnsList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "[]")

    val dnsActive: StateFlow<String?> = tokenStore.dnsActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDnsMode(mode: String) {
        viewModelScope.launch {
            tokenStore.saveDnsMode(mode)
        }
    }

    /** Добавить DNS в список (с проверкой дубликатов). */
    fun addDns(address: String) {
        val addr = address.trim()
        if (addr.isBlank()) return
        viewModelScope.launch {
            val list = parseDnsList(tokenStore.dnsList.first())
            if (list.none { it.equals(addr, ignoreCase = true) }) {
                list.add(addr)
                tokenStore.saveDnsList(org.json.JSONArray(list).toString())
            }
        }
    }

    /** Удалить DNS из списка (если он был активным — сброс активного). */
    fun removeDns(address: String) {
        viewModelScope.launch {
            val list = parseDnsList(tokenStore.dnsList.first())
                .filterNot { it.equals(address, ignoreCase = true) }
            tokenStore.saveDnsList(org.json.JSONArray(list).toString())
            if (tokenStore.dnsActive.first()?.equals(address, ignoreCase = true) == true) {
                tokenStore.saveDnsActive(null)
            }
        }
    }

    private fun parseDnsList(json: String): MutableList<String> {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf(String::isNotBlank)?.let { list.add(it) }
            }
        } catch (e: Exception) { }
        return list
    }

    // ---------- Тест DNS ----------

    private val _testingDns = MutableStateFlow(false)
    val testingDns: StateFlow<Boolean> = _testingDns.asStateFlow()

    private val _dnsTestResults = MutableStateFlow<List<String>?>(null)
    val dnsTestResults: StateFlow<List<String>?> = _dnsTestResults.asStateFlow()

    /** Тест всех DNS: резолв TMDb + проверка заглушек. Лучший — активный. */
    fun runDnsTest() {
        if (_testingDns.value) return
        viewModelScope.launch {
            _testingDns.value = true
            _dnsTestResults.value = null
            val results = dnsManager.testAllDns()
            val systemLabel = appContext.getString(R.string.dns_system_report)
            _dnsTestResults.value = results.map { r ->
                if (r.address == DnsManager.SYSTEM_DNS_ID)
                    "$systemLabel — ${r.detail}"
                else
                    "${r.address} (${r.type}) — ${r.detail}"
            }

            // Авто-выбор: первый живой ("system" — тест провайдера, не кандидат)
            val best = results.firstOrNull { it.ok && it.address != DnsManager.SYSTEM_DNS_ID }
            if (best != null) {
                tokenStore.saveDnsActive(best.address)
            } else {
                tokenStore.saveDnsActive(null)
            }
            _testingDns.value = false
        }
    }

    fun dismissDnsTest() {
        _dnsTestResults.value = null
    }

    // ---------- Свой API-ключ ----------

    private val _userToken = MutableStateFlow<String?>(null)
    val userToken: StateFlow<String?> = _userToken.asStateFlow()

    // ---------- Обновление приложения ----------

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    /** Скачать скачанное ранее и проверить релизы (тихо). */
    fun checkUpdateOnStart() {
        updateChecker.cleanup()
        viewModelScope.launch {
            _updateInfo.value = updateChecker.checkDaily()
        }
    }

    /** Ручная проверка сейчас — мимо суточного окна. */
    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    fun checkUpdateNow() {
        if (_checkingUpdate.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            val found = updateChecker.checkForUpdate()
            _updateInfo.value = found
            _checkingUpdate.value = false
            _updateStatus.value = if (found == null) {
                appContext.getString(R.string.update_up_to_date)
            } else null
        }
    }

    fun downloadAndInstall() {
        val info = _updateInfo.value ?: return
        if (_downloading.value) return
        viewModelScope.launch {
            _downloading.value = true
            _updateStatus.value = null
            val file = updateChecker.downloadApk(info)
            _downloading.value = false
            if (file == null) {
                _updateStatus.value = appContext.getString(R.string.update_failed) +
                        (updateChecker.lastError.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            } else {
                val installed = updateChecker.installApk(file)
                _updateStatus.value = if (installed) null
                else appContext.getString(R.string.update_failed) +
                        " (" + (updateChecker.lastError.ifBlank { "install failed" }) + ")"
            }
        }
    }

    init {
        viewModelScope.launch {
            _userToken.value = tokenStore.userToken.first()
        }
        checkUpdateOnStart()
    }

    fun saveApiKey(token: String) {
        viewModelScope.launch {
            val normalized = token.trim().ifBlank { null }
            tokenStore.saveUserToken(normalized)
            apiKeyProvider.invalidate()
            _userToken.value = normalized
        }
    }

    fun resetApiKey() = saveApiKey("")
}
