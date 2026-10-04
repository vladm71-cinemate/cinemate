package com.cinemate.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinemate.app.R
import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.data.remote.TmdbProxyManager
import com.cinemate.app.data.repository.AppListResult
import com.cinemate.app.data.repository.BoxApp
import com.cinemate.app.data.repository.BoxClient
import com.cinemate.app.data.repository.BoxDiscovery
import com.cinemate.app.data.repository.DiscoveredBox
import com.cinemate.app.data.repository.ParserClient
import com.cinemate.app.data.repository.ParserNode
import com.cinemate.app.data.repository.SendResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Приложение на боксе для диалога назначения глаза. */
data class BoxAppUi(
    val packageName: String,
    val label: String
)

/** Состояние секции «Просмотр на ТВ». */
data class TvWatchUiState(
    val enabled: Boolean = false,
    val player1: String? = null,
    val player2: String? = null,
    val player3: String? = null,
    val player4: String? = null,
    val boxWifi: String? = null,
    val boxLan: String? = null,
    val boxPort: String = "8080",
    val boxLanPort: String = "",
    val restartPlayer: Boolean = true,
    val checking: Boolean = false,
    val checkResult: String? = null,
    val checkSuccess: Boolean = false,
    val discovering: Boolean = false,
    val discovered: List<DiscoveredBox> = emptyList(),
    val torrentsOnTv: Boolean = false,
    val playOnPhone: Boolean = false,
    val nodes: List<ParserNode> = emptyList(),
    val selectedNode: String? = null,
    val nodeAutoSwitch: Boolean = true,
    val checkingNodes: Boolean = false,
    val nodeCheckResults: Map<String, String> = emptyMap(),
    val loadingBoxApps: Boolean = false,
    val boxApps: List<BoxAppUi> = emptyList(),
    val boxAppsError: String? = null
)

@HiltViewModel
class TvWatchViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val boxClient: BoxClient,
    private val boxDiscovery: BoxDiscovery,
    private val parserClient: ParserClient,
    private val iconStore: com.cinemate.app.data.repository.IconStore,
    private val tmdbProxyManager: TmdbProxyManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TvWatchUiState())
    val state: StateFlow<TvWatchUiState> = _state.asStateFlow()

    private val _checking = MutableStateFlow(false)
    private val _checkResult = MutableStateFlow<String?>(null)
    private val _checkSuccess = MutableStateFlow(false)
    private val _discovering = MutableStateFlow(false)
    private val _discovered = MutableStateFlow<List<DiscoveredBox>>(emptyList())
    private val _checkingNodes = MutableStateFlow(false)
    private val _nodeCheckResults = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _loadingBoxApps = MutableStateFlow(false)
    private val _boxApps = MutableStateFlow<List<BoxAppUi>>(emptyList())
    private val _boxAppsError = MutableStateFlow<String?>(null)

    private var discoveryJob: Job? = null

    /** Стратегия поиска раздач: "title" | "title_year". */
    val searchMode: StateFlow<String> = tokenStore.tvSearchMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "title")

    /** Язык интерфейса. */
    val appLanguage: StateFlow<String> = tokenStore.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    fun setAppLanguage(lang: String) {
        viewModelScope.launch { tokenStore.saveAppLanguage(lang) }
    }

    fun setSearchMode(mode: String) {
        viewModelScope.launch { tokenStore.saveTvSearchMode(mode) }
    }

    // ---------- Обёртки для combine (без List<Any?> и кастов) ----------

    private data class Part1(
        val enabled: Boolean, val p1: String?, val p2: String?,
        val p3: String?, val p4: String?
    )
    private data class Part2(
        val wifi: String?, val lan: String?, val port: String,
        val lanPort: String, val restart: Boolean
    )

    init {
        val part1 = combine(
            tokenStore.tvEnabled, tokenStore.tvPlayer1, tokenStore.tvPlayer2,
            tokenStore.tvPlayer3, tokenStore.tvPlayer4
        ) { e, p1, p2, p3, p4 -> Part1(e, p1, p2, p3, p4) }

        val part2 = combine(
            tokenStore.tvBoxWifi, tokenStore.tvBoxLan, tokenStore.tvBoxPort,
            tokenStore.tvBoxLanPort, tokenStore.tvRestartPlayer
        ) { w, l, p, lp, r -> Part2(w, l, p, lp, r) }

        val part3 = combine(
            tokenStore.torrentsOnTv, tokenStore.playOnPhone
        ) { onTv, playPhone -> onTv to playPhone }

        viewModelScope.launch {
            combine(part1, part2, part3) { a, b, c -> Triple(a, b, c) }
                .collect { (a, b, c) ->
                    _state.value = _state.value.copy(
                        enabled = a.enabled,
                        player1 = a.p1,
                        player2 = a.p2,
                        player3 = a.p3,
                        player4 = a.p4,
                        boxWifi = b.wifi,
                        boxLan = b.lan,
                        boxPort = b.port,
                        boxLanPort = b.lanPort,
                        restartPlayer = b.restart,
                        torrentsOnTv = c.first,
                        playOnPhone = c.second,
                        checking = _checking.value,
                        checkResult = _checkResult.value,
                        checkSuccess = _checkSuccess.value,
                        discovering = _discovering.value,
                        discovered = _discovered.value,
                        checkingNodes = _checkingNodes.value,
                        nodeCheckResults = _nodeCheckResults.value,
                        loadingBoxApps = _loadingBoxApps.value,
                        boxApps = _boxApps.value,
                        boxAppsError = _boxAppsError.value
                    )
                }
        }

        viewModelScope.launch {
            combine(
                tokenStore.parserSelected,
                tokenStore.parserAutoSwitch,
                tokenStore.parserCustomNodes
            ) { selected, auto, custom -> Triple(selected, auto, custom) }
                .collect { (selected, auto, _) ->
                    val nodes = parserClient.allNodes()
                    _state.value = _state.value.copy(
                        nodes = nodes,
                        selectedNode = selected,
                        nodeAutoSwitch = auto
                    )
                }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { tokenStore.saveTvEnabled(enabled) }
    }

    fun setPlayer1(packageName: String?) {
        viewModelScope.launch { tokenStore.saveTvPlayer1(packageName) }
    }

    fun setPlayer2(packageName: String?) {
        viewModelScope.launch { tokenStore.saveTvPlayer2(packageName) }
    }

    fun setPlayer3(packageName: String?) {
        viewModelScope.launch { tokenStore.saveTvPlayer3(packageName) }
    }

    fun setPlayer4(packageName: String?) {
        viewModelScope.launch { tokenStore.saveTvPlayer4(packageName) }
    }

    /**
     * Назначить/пере-назначить кнопку: сохраняем привязку и тянем иконку
     * с бокса (при пере-назначении — иконку НОВОЙ программы).
     * Снятие («Не назначено») — чистим файл иконки.
     */
    fun assignPlayer(button: Int, packageName: String?) {
        viewModelScope.launch {
            val old = when (button) {
                1 -> tokenStore.tvPlayer1.first()
                2 -> tokenStore.tvPlayer2.first()
                3 -> tokenStore.tvPlayer3.first()
                else -> tokenStore.tvPlayer4.first()
            }
            when (button) {
                1 -> tokenStore.saveTvPlayer1(packageName)
                2 -> tokenStore.saveTvPlayer2(packageName)
                3 -> tokenStore.saveTvPlayer3(packageName)
                else -> tokenStore.saveTvPlayer4(packageName)
            }
            when {
                packageName == null -> iconStore.remove(old ?: return@launch)
                packageName != old -> iconStore.fetchAndSave(packageName)
            }
        }
    }

    fun setBoxWifi(ip: String) {
        viewModelScope.launch {
            tokenStore.saveTvBoxWifi(ip)
            _checkResult.value = null
            _checkSuccess.value = false
            publish()
        }
    }

    fun setBoxLan(ip: String) {
        viewModelScope.launch {
            tokenStore.saveTvBoxLan(ip)
            _checkResult.value = null
            _checkSuccess.value = false
            publish()
        }
    }

    fun setBoxPort(port: String) {
        viewModelScope.launch {
            tokenStore.saveTvBoxPort(port)
            _checkResult.value = null
            _checkSuccess.value = false
            publish()
        }
    }

    fun setBoxLanPort(port: String) {
        viewModelScope.launch {
            tokenStore.saveTvBoxLanPort(port)
            _checkResult.value = null
            _checkSuccess.value = false
            publish()
        }
    }

    /** Метла у основного поля: IP пусто, порт — дефолт. */
    fun clearMainAddress() {
        viewModelScope.launch {
            tokenStore.saveTvBoxWifi(null)
            tokenStore.saveTvBoxPort("")
            _checkResult.value = null
            _checkSuccess.value = false
            publish()
        }
    }

    /** Метла у запасного поля: IP и порт пусто. */
    fun clearSpareAddress() {
        viewModelScope.launch {
            tokenStore.saveTvBoxLan(null)
            tokenStore.saveTvBoxLanPort("")
            _checkResult.value = null
            _checkSuccess.value = false
            publish()
        }
    }

    /**
     * «Использовать» найденного бокса:
     *  - есть пустое поле (основное или запасное) → вставить туда (IP + его порт);
     *  - оба заняты → заменить основной;
     * после вставки — авто-тест соединения.
     */
    fun useDiscoveredBox(box: DiscoveredBox) {
        viewModelScope.launch {
            val main = tokenStore.tvBoxWifi.first()
            val spare = tokenStore.tvBoxLan.first()
            val port = box.port.toString()

            if (main.isNullOrBlank()) {
                tokenStore.saveTvBoxWifi(box.address)
                tokenStore.saveTvBoxPort(port)
            } else if (spare.isNullOrBlank()) {
                tokenStore.saveTvBoxLan(box.address)
                tokenStore.saveTvBoxLanPort(port)
            } else {
                tokenStore.saveTvBoxWifi(box.address)
                tokenStore.saveTvBoxPort(port)
            }

            _checkResult.value = null
            _checkSuccess.value = false
            publish()
            checkConnection()
        }
    }

    fun setRestartPlayer(restart: Boolean) {
        viewModelScope.launch { tokenStore.saveTvRestartPlayer(restart) }
    }

    fun setTorrentsOnTv(onTv: Boolean) {
        viewModelScope.launch { tokenStore.saveTorrentsOnTv(onTv) }
    }

    fun setPlayOnPhone(onPhone: Boolean) {
        viewModelScope.launch { tokenStore.savePlayOnPhone(onPhone) }
    }

    fun checkConnection(silent: Boolean = false) {
        if (_checking.value) return

        viewModelScope.launch {
            _checking.value = true
            if (!silent) _checkResult.value = null
            publish()

            val mainPort = tokenStore.tvBoxPort.first()
            val sparePort = tokenStore.tvBoxLanPort.first().ifBlank { mainPort }
            val candidates = buildList {
                tokenStore.tvBoxWifi.first()?.let { add("$it:$mainPort") }
                tokenStore.tvBoxLan.first()?.let { add("$it:$sparePort") }
                tokenStore.tvBoxLastGood.first()?.let { add(it) }
            }.distinct()

            if (candidates.isEmpty()) {
                _checkSuccess.value = false
                if (!silent) {
                    _checkResult.value = appContext.getString(R.string.tv_connection_no_address)
                }
                _checking.value = false
                publish()
                return@launch
            }

            var good: String? = null
            for (address in candidates) {
                if (boxClient.ping(address) is SendResult.Success) {
                    good = address
                    break
                }
            }

            if (good != null) {
                _checkSuccess.value = true
                tokenStore.saveTvBoxLastGood(good)
                _checkResult.value = if (silent) null else
                    appContext.getString(R.string.tv_connection_ok_with, good)
            } else {
                _checkSuccess.value = false
                _checkResult.value = if (silent) null else
                    appContext.getString(R.string.tv_connection_fail_with, candidates.joinToString(", "))
            }
            _checking.value = false
            publish()
        }
    }

    fun startDiscovery() {
        if (_discovering.value) return
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _discovering.value = true
            _discovered.value = emptyList()
            publish()

            val result = boxDiscovery.discover()
            _discovered.value = result
            _discovering.value = false
            publish()
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        _discovering.value = false
        publish()
    }

    fun selectNode(baseUrl: String) {
        viewModelScope.launch { tokenStore.saveParserSelected(baseUrl) }
    }

    fun setNodeAutoSwitch(auto: Boolean) {
        viewModelScope.launch { tokenStore.saveParserAutoSwitch(auto) }
    }

    fun addNode(name: String, baseUrl: String, apiKey: String, onError: (String) -> Unit) {
        val url = baseUrl.trim().trimEnd('/')
        if (url.isBlank()) {
            onError(appContext.getString(R.string.tv_node_error_empty))
            return
        }
        viewModelScope.launch {
            val existing = parserClient.allNodes()
            if (existing.any { it.baseUrl.equals(url, ignoreCase = true) }) {
                onError(appContext.getString(R.string.tv_node_error_dup))
                return@launch
            }
            val nodes = parseCustomNodesJson(tokenStore.parserCustomNodes.first())
            nodes.add(
                JSONObject().apply {
                    put("name", name.trim().ifBlank { url })
                    put("baseUrl", url)
                    put("apiKey", apiKey.trim())
                }
            )
            tokenStore.saveParserCustomNodes(JSONArray(nodes).toString())
            tokenStore.saveParserSelected(url)
        }
    }

    fun removeNode(baseUrl: String) {
        viewModelScope.launch {
            val node = parserClient.allNodes()
                .firstOrNull { it.baseUrl.equals(baseUrl, ignoreCase = true) }
            if (node == null || node.builtIn) return@launch

            val fresh = parseCustomNodesJson(tokenStore.parserCustomNodes.first())
                .filterNot { it.optString("baseUrl").equals(baseUrl, ignoreCase = true) }
            tokenStore.saveParserCustomNodes(JSONArray(fresh).toString())

            if (tokenStore.parserSelected.first()?.equals(baseUrl, ignoreCase = true) == true) {
                tokenStore.saveParserSelected(null)
            }
        }
    }

    fun checkNodes() {
        if (_checkingNodes.value) return
        viewModelScope.launch {
            _checkingNodes.value = true
            _nodeCheckResults.value = emptyMap()
            publish()

            val nodes = parserClient.allNodes()
            val results = mutableMapOf<String, String>()
            for (node in nodes) {
                val found = parserClient.search(node.baseUrl, node.apiKey, "мстители")
                results[node.baseUrl] = if (found.isEmpty())
                    appContext.getString(R.string.tv_nodes_dead)
                else
                    appContext.getString(R.string.tv_nodes_alive, found.size)
            }
            _nodeCheckResults.value = results
            _checkingNodes.value = false
            publish()
        }
    }

    private fun parseCustomNodesJson(json: String): MutableList<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(it) }
            }
        } catch (e: Exception) { }
        return list
    }

    fun setProxyMode(mode: String) {
        viewModelScope.launch {
            tokenStore.saveProxyMode(mode)
            tmdbProxyManager.invalidate()
        }
    }

    fun setProxyScheme(scheme: String) {
        viewModelScope.launch {
            tokenStore.saveProxyScheme(scheme)
            tmdbProxyManager.invalidate()
        }
    }

    fun setProxyHost(host: String) {
        viewModelScope.launch {
            tokenStore.saveProxyHost(host)
            tmdbProxyManager.invalidate()
        }
    }

    /**
     * Загрузить список приложений с бокса (/listjson).
     * ДИАГНОСТИКА: в ошибку пишется полный маршрут — адреса и результат каждого.
     */
    fun loadBoxApps() {
        if (_loadingBoxApps.value) return
        viewModelScope.launch {
            _loadingBoxApps.value = true
            _boxAppsError.value = null
            _boxApps.value = emptyList()
            publish()

            val mainPort = tokenStore.tvBoxPort.first()
            val sparePort = tokenStore.tvBoxLanPort.first().ifBlank { mainPort }
            val lastGood = tokenStore.tvBoxLastGood.first()
            val candidates = buildList {
                lastGood?.let { add(it) }
                tokenStore.tvBoxWifi.first()?.let { add("$it:$mainPort") }
                tokenStore.tvBoxLan.first()?.let { add("$it:$sparePort") }
            }.distinct()

            val log = mutableListOf<String>()
            log.add("lastGood: ${lastGood ?: "—"}")
            log.add("кандидаты: ${candidates.joinToString(", ").ifBlank { "НЕТ" }}")

            if (candidates.isEmpty()) {
                _boxAppsError.value = "ДИАГНОСТИКА:\n" + log.joinToString("\n")
                _loadingBoxApps.value = false
                publish()
                return@launch
            }

            var apps: List<BoxApp>? = null
            for (address in candidates) {
                when (val r = boxClient.fetchAppList(address)) {
                    is AppListResult.Success -> {
                        apps = r.apps
                        tokenStore.saveTvBoxLastGood(address)
                        log.add("$address → OK (${r.apps.size})")
                        break
                    }
                    is AppListResult.Error -> log.add("$address → ОШИБКА: ${r.message}")
                }
            }

            _boxApps.value = apps?.map { BoxAppUi(it.packageName, it.label) } ?: emptyList()
            _boxAppsError.value = if (apps != null) null
            else "ДИАГНОСТИКА:\n" + log.joinToString("\n")
            _loadingBoxApps.value = false
            publish()
        }
    }

    private fun publish() {
        _state.value = _state.value.copy(
            checking = _checking.value,
            checkResult = _checkResult.value,
            checkSuccess = _checkSuccess.value,
            discovering = _discovering.value,
            discovered = _discovered.value,
            checkingNodes = _checkingNodes.value,
            nodeCheckResults = _nodeCheckResults.value,
            loadingBoxApps = _loadingBoxApps.value,
            boxApps = _boxApps.value,
            boxAppsError = _boxAppsError.value
        )
    }

    fun playerLabel(packageName: String?): String {
        val fromBox = _boxApps.value.firstOrNull { it.packageName == packageName }?.label
        if (fromBox != null) return fromBox
        return packageName?.let { com.cinemate.app.domain.model.PlayerPresets.labelFor(it) }
            ?: appContext.getString(R.string.tv_eye_unassigned)
    }
}