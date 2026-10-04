package com.cinemate.app.ui.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinemate.app.R
import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.data.local.dao.WatchedDao
import com.cinemate.app.data.local.entity.WatchedItemEntity
import com.cinemate.app.data.repository.BoxClient
import com.cinemate.app.data.repository.DetailsRepository
import com.cinemate.app.data.repository.FavoritesRepository
import com.cinemate.app.data.repository.ParserClient
import com.cinemate.app.data.repository.ParserNode
import com.cinemate.app.data.repository.SendResult
import com.cinemate.app.data.repository.TorrentResult
import com.cinemate.app.data.repository.UpcomingRepository
import com.cinemate.app.domain.model.Actor
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Keyword
import com.cinemate.app.domain.model.Movie
import com.cinemate.app.domain.model.TitleDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Состояние поиска торрентов (шит «Смотреть»). */
sealed interface TorrentsUiState {
    data object Idle : TorrentsUiState
    data object Loading : TorrentsUiState
    data class Loaded(
        val torrents: List<TorrentResult>,
        val nodeName: String?,
        val fellBack: Boolean
    ) : TorrentsUiState
    data class Error(val message: String) : TorrentsUiState
}

/** Сортировка списка раздач (локальная, без перезапроса). */
enum class TorrentSort(val label: String) {
    SEEDS("по сидам"),
    SIZE("по размеру"),
    DATE("по дате")
}

/** Фильтр по качеству: null = все. */
typealias QualityFilter = Int?

/** Фильтр по сезону: null = все. */
typealias SeasonFilter = Int?

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val detailsRepository: DetailsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val upcomingRepository: UpcomingRepository,
    private val tokenStore: TokenStore,
    private val boxClient: BoxClient,
    private val parserClient: ParserClient,
    private val iconStore: com.cinemate.app.data.repository.IconStore,
    private val watchedDao: WatchedDao
) : ViewModel() {

    private val type: ContentType =
        if (savedStateHandle.get<String>("type") == "tv") ContentType.TV else ContentType.MOVIE
    private val id: Int = savedStateHandle.get<Int>("id") ?: 0

    private val _state = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = favoritesRepository
        .observeIsFavorite(id, type.name.lowercase())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAwaited: StateFlow<Boolean> = upcomingRepository
        .observeIsAwaited(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTracked: StateFlow<Boolean> = upcomingRepository
        .observeIsTracked(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val tvEnabled: StateFlow<Boolean> = tokenStore.tvEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val tvPlayer1: StateFlow<String?> = tokenStore.tvPlayer1
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tvPlayer2: StateFlow<String?> = tokenStore.tvPlayer2
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tvPlayer3: StateFlow<String?> = tokenStore.tvPlayer3
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tvPlayer4: StateFlow<String?> = tokenStore.tvPlayer4
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Файлы иконок для назначенных кнопок (1..4). Пересчитывается на смену привязок. */
    val eyeIconFiles: StateFlow<List<File?>> = combine(
        tokenStore.tvPlayer1, tokenStore.tvPlayer2,
        tokenStore.tvPlayer3, tokenStore.tvPlayer4
    ) { p1, p2, p3, p4 ->
        listOf(p1, p2, p3, p4).map { pkg -> pkg?.let { iconStore.iconFile(it) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(null, null, null, null))

    val tvSearchMode: StateFlow<String> = tokenStore.tvSearchMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "title")

    val showOriginalTitles: StateFlow<Boolean> = tokenStore.showOriginalTitles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _tvSendStatus = MutableStateFlow<String?>(null)
    val tvSendStatus: StateFlow<String?> = _tvSendStatus.asStateFlow()

    private val _tvSendingButton = MutableStateFlow<Int?>(null)
    val tvSendingButton: StateFlow<Int?> = _tvSendingButton.asStateFlow()

    private val _torrentsState = MutableStateFlow<TorrentsUiState>(TorrentsUiState.Idle)
    val torrentsState: StateFlow<TorrentsUiState> = _torrentsState.asStateFlow()

    private val _torrentSort = MutableStateFlow(TorrentSort.SEEDS)
    val torrentSort: StateFlow<TorrentSort> = _torrentSort.asStateFlow()

    /** Фильтр по качеству: null = все, иначе 2160/1080/720/480. */
    private val _qualityFilter = MutableStateFlow<QualityFilter>(null)
    val qualityFilter: StateFlow<QualityFilter> = _qualityFilter.asStateFlow()

    fun setQualityFilter(quality: QualityFilter) {
        _qualityFilter.value = quality
    }

    /** Фильтр по сезону: null = все. */
    private val _seasonFilter = MutableStateFlow<SeasonFilter>(null)
    val seasonFilter: StateFlow<SeasonFilter> = _seasonFilter.asStateFlow()

    fun setSeasonFilter(season: SeasonFilter) {
        _seasonFilter.value = season
    }

    private val _exactSearch = MutableStateFlow(false)
    val exactSearch: StateFlow<Boolean> = _exactSearch.asStateFlow()

    private val _magnetStatus = MutableStateFlow<String?>(null)
    val magnetStatus: StateFlow<String?> = _magnetStatus.asStateFlow()

    private val _sendingMagnet = MutableStateFlow<String?>(null)
    val sendingMagnet: StateFlow<String?> = _sendingMagnet.asStateFlow()

    val torrentsOnTv: StateFlow<Boolean> = tokenStore.torrentsOnTv
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Расширенный вид списка раздач. */
    val richView: StateFlow<Boolean> = tokenStore.torrentRichView
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleRichView() {
        viewModelScope.launch { tokenStore.saveTorrentRichView(!richView.value) }
    }

    val playOnPhone: StateFlow<Boolean> = tokenStore.playOnPhone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _closeSheet = MutableStateFlow(0)
    val closeSheet: StateFlow<Int> = _closeSheet.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailsUiState.Loading
            _state.value = try {
                DetailsUiState.Content(detailsRepository.details(type, id))
            } catch (e: Exception) {
                DetailsUiState.Error(e.message ?: appContext.getString(R.string.common_retry))
            }
        }
    }

    // ---------- Каскад адресов ----------

    private suspend fun addressCandidates(): List<String> {
        val mainPort = tokenStore.tvBoxPort.first()
        val sparePort = tokenStore.tvBoxLanPort.first().ifBlank { mainPort }
        return buildList {
            tokenStore.tvBoxLastGood.first()?.let { add(it) }
            tokenStore.tvBoxWifi.first()?.let { add("$it:$mainPort") }
            tokenStore.tvBoxLan.first()?.let { add("$it:$sparePort") }
        }.distinct()
    }

    // ---------- История «Смотрел» ----------

    private fun magnetHash(magnet: String): String =
        Regex("xt=urn:btih:([A-Fa-f0-9]{40}|[A-Fa-f0-9]{32})")
            .find(magnet)?.groupValues?.get(1)?.uppercase()
            ?: magnet.hashCode().toString()

    private suspend fun rememberWatched(torrent: TorrentResult) {
        val details = (_state.value as? DetailsUiState.Content)?.details
        try {
            val existing = watchedDao.get(magnetHash(torrent.magnetUri))
            watchedDao.upsert(
                WatchedItemEntity(
                    infoHash = magnetHash(torrent.magnetUri),
                    title = existing?.title ?: torrent.title,
                    posterPath = existing?.posterPath ?: details?.posterPath,
                    watchedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) { }
    }

    // ---------- Открытие шита раздач ----------

    fun openTorrents() {
        val details = (_state.value as? DetailsUiState.Content)?.details ?: return
        if (_torrentsState.value is TorrentsUiState.Loading) return

        val year = if (tvSearchMode.value == "title_year")
            details.releaseDate?.take(4) else null
        val query = buildString {
            append(details.title)
            year?.let { append(" $it") }
        }

        viewModelScope.launch {
            _torrentsState.value = TorrentsUiState.Loading
            _magnetStatus.value = null

            val selected = tokenStore.parserSelected.first()
            var autoSwitchedTo: ParserNode? = null
            val outcome = parserClient.searchWithFallback(
                selected, query,
                onAutoSwitched = { node -> autoSwitchedTo = node }
            )

            autoSwitchedTo?.let { node ->
                tokenStore.saveParserSelected(node.baseUrl)
                _magnetStatus.value = appContext.getString(R.string.parser_autoswitched, node.name)
            }

            _torrentsState.value = if (outcome.results.isEmpty()) {
                TorrentsUiState.Error(
                    appContext.getString(R.string.torrents_none) +
                            (outcome.answeredBy?.let { " (" + it.name + ")" } ?: "")
                )
            } else {
                _torrentSort.value = TorrentSort.SEEDS
                _qualityFilter.value = null
                _seasonFilter.value = null
                TorrentsUiState.Loaded(
                    torrents = outcome.results,
                    nodeName = outcome.answeredBy?.name,
                    fellBack = outcome.fellBack
                )
            }
        }
    }

    fun setSort(sort: TorrentSort) {
        _torrentSort.value = sort
    }

    fun toggleExactSearch() {
        _exactSearch.value = !_exactSearch.value
        val details = (_state.value as? DetailsUiState.Content)?.details ?: return
        val year = if (tvSearchMode.value == "title_year")
            details.releaseDate?.take(4) else null
        val query = buildString {
            append(details.title)
            year?.let { append(" $it") }
        }
        val finalQuery = if (_exactSearch.value) "\"$query\"" else query

        viewModelScope.launch {
            _torrentsState.value = TorrentsUiState.Loading
            val selected = tokenStore.parserSelected.first()
            var autoSwitchedTo: ParserNode? = null
            val outcome = parserClient.searchWithFallback(
                selected, finalQuery,
                onAutoSwitched = { node -> autoSwitchedTo = node }
            )

            autoSwitchedTo?.let { node ->
                tokenStore.saveParserSelected(node.baseUrl)
                _magnetStatus.value = appContext.getString(R.string.parser_autoswitched, node.name)
            }

            _torrentsState.value = if (outcome.results.isEmpty()) {
                TorrentsUiState.Error(
                    appContext.getString(R.string.torrents_none) +
                            (outcome.answeredBy?.let { " (" + it.name + ")" } ?: "")
                )
            } else {
                _torrentSort.value = TorrentSort.SEEDS
                _qualityFilter.value = null
                _seasonFilter.value = null
                TorrentsUiState.Loaded(outcome.results, outcome.answeredBy?.name, outcome.fellBack)
            }
        }
    }

    // ---------- Отправка раздачи ----------

    private fun launchLocally(magnet: String): Boolean {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(magnet)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            return true
        } catch (e: Exception) { }
        try {
            val toMatrix = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(magnet)
                setPackage("ru.yourok.torrserve")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(toMatrix)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun sendTorrent(torrent: TorrentResult) {
        viewModelScope.launch {
            _magnetStatus.value = null

            if (playOnPhone.value) {
                _sendingMagnet.value = torrent.magnetUri
                val ok = launchLocally(torrent.magnetUri)
                _sendingMagnet.value = null
                _magnetStatus.value = if (ok) {
                    rememberWatched(torrent)
                    appContext.getString(R.string.torrents_started_phone, torrent.title)
                } else {
                    appContext.getString(R.string.torrents_no_magnet_app)
                }
                return@launch
            }

            val candidates = addressCandidates()
            if (candidates.isEmpty()) {
                _magnetStatus.value = appContext.getString(R.string.tv_box_no_address)
                return@launch
            }

            _sendingMagnet.value = torrent.magnetUri

            val details = (_state.value as? DetailsUiState.Content)?.details
            val posterUrl = details?.let { com.cinemate.app.util.Constants.posterUrl(it.posterPath) }

            var success = false
            var lastError: String? = null

            if (torrentsOnTv.value) {
                val all = (_torrentsState.value as? TorrentsUiState.Loaded)?.torrents ?: listOf(torrent)
                val payload = parserClient.buildTorrentsPayload(
                    query = torrent.title.take(60),
                    posterUrl = posterUrl,
                    results = all,
                    richView = richView.value
                )
                for (address in candidates) {
                    when (val r = boxClient.sendTorrents(address, payload)) {
                        is SendResult.Success -> {
                            success = true
                            tokenStore.saveTvBoxLastGood(address)
                        }
                        is SendResult.BoxError -> lastError = r.message
                        is SendResult.Unreachable -> lastError = r.message
                    }
                    if (success) break
                }
                _magnetStatus.value = if (success) {
                    appContext.getString(R.string.torrents_sent_tv, all.size)
                } else {
                    appContext.getString(R.string.tv_box_unreachable) +
                            (lastError?.let { " — $it" } ?: "")
                }
                if (success) {
                    rememberWatched(torrent)
                    _closeSheet.value++
                }
            } else {
                for (address in candidates) {
                    when (val r = parserClient.sendMagnet(address, torrent.magnetUri, torrent.title, posterUrl)) {
                        is SendResult.Success -> {
                            success = true
                            tokenStore.saveTvBoxLastGood(address)
                        }
                        is SendResult.BoxError -> lastError = r.message
                        is SendResult.Unreachable -> lastError = r.message
                    }
                    if (success) break
                }
                _magnetStatus.value = if (success) {
                    appContext.getString(R.string.torrents_started_tv, torrent.title)
                } else {
                    appContext.getString(R.string.tv_box_unreachable) +
                            (lastError?.let { " — $it" } ?: "")
                }
                if (success) rememberWatched(torrent)
            }

            _sendingMagnet.value = null
        }
    }

    fun stopOnTv() {
        viewModelScope.launch {
            val candidates = addressCandidates()
            if (candidates.isEmpty()) {
                _magnetStatus.value = appContext.getString(R.string.tv_no_address_short)
                return@launch
            }
            var success = false
            var lastError: String? = null
            for (address in candidates) {
                when (val r = boxClient.sendStop(address)) {
                    is SendResult.Success -> { success = true; tokenStore.saveTvBoxLastGood(address) }
                    is SendResult.BoxError -> lastError = r.message
                    is SendResult.Unreachable -> lastError = r.message
                }
                if (success) break
            }
            _magnetStatus.value = if (success) appContext.getString(R.string.torrents_stop_done)
            else appContext.getString(R.string.tv_box_unreachable) +
                    (lastError?.let { " — $it" } ?: "")
        }
    }

    fun dismissMagnetStatus() {
        _magnetStatus.value = null
    }

    // ---------- Кнопки глаз ----------

    fun sendToTv(buttonNumber: Int) {
        val details = (_state.value as? DetailsUiState.Content)?.details ?: return

        val packageName = when (buttonNumber) {
            1 -> tvPlayer1.value
            2 -> tvPlayer2.value
            3 -> tvPlayer3.value
            4 -> tvPlayer4.value
            else -> null
        }
        if (packageName.isNullOrBlank()) {
            _tvSendStatus.value = appContext.getString(R.string.tv_eye_no_app, buttonNumber)
            return
        }

        _tvSendingButton.value = buttonNumber
        _tvSendStatus.value = null

        viewModelScope.launch {
            val candidates = addressCandidates()
            if (candidates.isEmpty()) {
                _tvSendingButton.value = null
                _tvSendStatus.value = appContext.getString(R.string.tv_no_address_long)
                return@launch
            }

            val restart = tokenStore.tvRestartPlayer.first()

            var success = false
            var usedAddress: String? = null
            var lastError: String? = null

            for (address in candidates) {
                when (val r = boxClient.sendOpen(
                    boxAddress = address,
                    packageName = packageName,
                    restart = restart
                )) {
                    is SendResult.Success -> {
                        success = true
                        usedAddress = address
                    }
                    is SendResult.BoxError -> lastError = r.message
                    is SendResult.Unreachable -> lastError = r.message
                }
                if (success) break
            }

            _tvSendingButton.value = null
            _tvSendStatus.value = if (success) {
                tokenStore.saveTvBoxLastGood(usedAddress)
                appContext.getString(R.string.tv_started)
            } else {
                appContext.getString(R.string.tv_box_unreachable_checked, candidates.joinToString(", ")) +
                        (lastError?.let { " — $it" } ?: "")
            }
        }
    }

    fun dismissTvStatus() {
        _tvSendStatus.value = null
    }

    // ---------- Ленивые блоки ----------

    fun toggleShowMore() {
        val content = _state.value as? DetailsUiState.Content ?: return
        _state.value = content.copy(showMoreExpanded = !content.showMoreExpanded)
    }

    fun toggleActorsExpanded() {
        val content = _state.value as? DetailsUiState.Content ?: return
        val nowShown = !content.showActors
        _state.value = content.copy(showActors = nowShown)
        if (nowShown && content.actors is LazyBlockState.Idle) {
            loadActors()
        }
    }

    fun toggleKeywordsExpanded() {
        val content = _state.value as? DetailsUiState.Content ?: return
        val nowShown = !content.showKeywords
        _state.value = content.copy(showKeywords = nowShown)
        if (nowShown && content.keywords is LazyBlockState.Idle) {
            loadKeywords()
        }
    }

    private fun loadActors() {
        if (_state.value !is DetailsUiState.Content) return
        val current = (_state.value as DetailsUiState.Content).actors
        if (current !is LazyBlockState.Idle) return

        viewModelScope.launch {
            setActors(LazyBlockState.Loading)
            setActors(
                try {
                    LazyBlockState.Loaded(detailsRepository.cast(type, id))
                } catch (e: Exception) {
                    LazyBlockState.Error(e.message ?: "error")
                }
            )
        }
    }

    private fun loadKeywords() {
        if (_state.value !is DetailsUiState.Content) return
        val current = (_state.value as DetailsUiState.Content).keywords
        if (current !is LazyBlockState.Idle) return

        viewModelScope.launch {
            setKeywords(LazyBlockState.Loading)
            setKeywords(
                try {
                    LazyBlockState.Loaded(detailsRepository.keywords(type, id))
                } catch (e: Exception) {
                    LazyBlockState.Error(e.message ?: "error")
                }
            )
        }
    }

    fun loadSimilar() {
        val content = _state.value as? DetailsUiState.Content ?: return
        if (content.similar !is LazyBlockState.Idle) return

        viewModelScope.launch {
            setSimilar(LazyBlockState.Loading)
            setSimilar(
                try {
                    LazyBlockState.Loaded(detailsRepository.similar(type, id))
                } catch (e: Exception) {
                    LazyBlockState.Error(e.message ?: "error")
                }
            )
        }
    }

    // ---------- Кнопки действий ----------

    fun toggleFavorite() {
        val details = (_state.value as? DetailsUiState.Content)?.details ?: return
        viewModelScope.launch { favoritesRepository.toggle(details) }
    }

    fun toggleAwait() {
        val details = (_state.value as? DetailsUiState.Content)?.details ?: return
        viewModelScope.launch { upcomingRepository.toggleAwait(details) }
    }

    fun toggleTrack() {
        val details = (_state.value as? DetailsUiState.Content)?.details ?: return
        viewModelScope.launch { upcomingRepository.toggleTrack(details) }
    }

    // ---------- Приватные хелперы ----------

    private fun setActors(blockState: LazyBlockState<List<Actor>>) {
        val content = _state.value as? DetailsUiState.Content ?: return
        _state.value = content.copy(actors = blockState)
    }

    private fun setKeywords(blockState: LazyBlockState<List<Keyword>>) {
        val content = _state.value as? DetailsUiState.Content ?: return
        _state.value = content.copy(keywords = blockState)
    }

    private fun setSimilar(blockState: LazyBlockState<List<Movie>>) {
        val content = _state.value as? DetailsUiState.Content ?: return
        _state.value = content.copy(similar = blockState)
    }
}