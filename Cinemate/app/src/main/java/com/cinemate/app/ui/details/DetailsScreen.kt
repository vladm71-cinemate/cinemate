package com.cinemate.app.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cinemate.app.R
import com.cinemate.app.data.repository.TorrentResult
import com.cinemate.app.domain.model.Actor
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Keyword
import com.cinemate.app.domain.model.Movie
import com.cinemate.app.domain.model.TitleDetails
import com.cinemate.app.util.Constants
import com.cinemate.app.util.DateUtils
import java.io.File
import java.time.LocalDate
import java.util.Locale

private fun eyeColor(buttonNumber: Int, default: Color): Color = when (buttonNumber) {
    1 -> default
    2 -> Color(0xFFC62828)
    3 -> Color(0xFF2E9E4F)
    4 -> Color(0xFF1E88E5)
    else -> default
}

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onOpenDetails: (String, Int) -> Unit = { _, _ -> },
    onOpenPerson: (Int) -> Unit = { },
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closeSheetTick by viewModel.closeSheet.collectAsStateWithLifecycle()
    val eyeIconFiles by viewModel.eyeIconFiles.collectAsStateWithLifecycle()
    val richView by viewModel.richView.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        when (val s = state) {
            DetailsUiState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is DetailsUiState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.search_error, s.message))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
                }
            }

            is DetailsUiState.Content -> DetailsContent(
                content = s,
                isFavorite = viewModel.isFavorite.collectAsStateWithLifecycle().value,
                isAwaited = viewModel.isAwaited.collectAsStateWithLifecycle().value,
                isTracked = viewModel.isTracked.collectAsStateWithLifecycle().value,
                tvEnabled = viewModel.tvEnabled.collectAsStateWithLifecycle().value,
                tvPlayer1 = viewModel.tvPlayer1.collectAsStateWithLifecycle().value,
                tvPlayer2 = viewModel.tvPlayer2.collectAsStateWithLifecycle().value,
                tvPlayer3 = viewModel.tvPlayer3.collectAsStateWithLifecycle().value,
                tvPlayer4 = viewModel.tvPlayer4.collectAsStateWithLifecycle().value,
                eyeIconFiles = viewModel.eyeIconFiles.collectAsStateWithLifecycle().value,
                richView = richView,
                onToggleRich = viewModel::toggleRichView,
                tvSendingButton = viewModel.tvSendingButton.collectAsStateWithLifecycle().value,
                tvSendStatus = viewModel.tvSendStatus.collectAsStateWithLifecycle().value,
                showOriginalTitles = viewModel.showOriginalTitles.collectAsStateWithLifecycle().value,
                onToggleFavorite = viewModel::toggleFavorite,
                onToggleAwait = viewModel::toggleAwait,
                onToggleTrack = viewModel::toggleTrack,
                onLoadSimilar = viewModel::loadSimilar,
                onToggleShowMore = viewModel::toggleShowMore,
                onToggleActors = viewModel::toggleActorsExpanded,
                onToggleKeywords = viewModel::toggleKeywordsExpanded,
                onOpenDetails = onOpenDetails,
                onOpenPerson = onOpenPerson,
                onSendToTv = viewModel::sendToTv,
                onDismissTvStatus = viewModel::dismissTvStatus,
                torrentsState = viewModel.torrentsState.collectAsStateWithLifecycle().value,
                torrentSort = viewModel.torrentSort.collectAsStateWithLifecycle().value,
                qualityFilter = viewModel.qualityFilter.collectAsStateWithLifecycle().value,
                seasonFilter = viewModel.seasonFilter.collectAsStateWithLifecycle().value,
                exactSearch = viewModel.exactSearch.collectAsStateWithLifecycle().value,
                torrentsOnTv = viewModel.torrentsOnTv.collectAsStateWithLifecycle().value,
                playOnPhone = viewModel.playOnPhone.collectAsStateWithLifecycle().value,
                magnetStatus = viewModel.magnetStatus.collectAsStateWithLifecycle().value,
                sendingMagnet = viewModel.sendingMagnet.collectAsStateWithLifecycle().value,
                onOpenTorrents = viewModel::openTorrents,
                onSetSort = viewModel::setSort,
                onSetQuality = viewModel::setQualityFilter,
                onSetSeason = viewModel::setSeasonFilter,
                onToggleExact = viewModel::toggleExactSearch,
                onSendTorrent = viewModel::sendTorrent,
                onStopOnTv = viewModel::stopOnTv,
                onDismissMagnetStatus = viewModel::dismissMagnetStatus,
                closeSheetTick = closeSheetTick
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsContent(
    content: DetailsUiState.Content,
    isFavorite: Boolean,
    isAwaited: Boolean,
    isTracked: Boolean,
    tvEnabled: Boolean,
    tvPlayer1: String?,
    tvPlayer2: String?,
    tvPlayer3: String?,
    tvPlayer4: String?,
    eyeIconFiles: List<File?>,
    richView: Boolean,
    onToggleRich: () -> Unit,
    tvSendingButton: Int?,
    tvSendStatus: String?,
    showOriginalTitles: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleAwait: () -> Unit,
    onToggleTrack: () -> Unit,
    onLoadSimilar: () -> Unit,
    onToggleShowMore: () -> Unit,
    onToggleActors: () -> Unit,
    onToggleKeywords: () -> Unit,
    onOpenDetails: (String, Int) -> Unit,
    onOpenPerson: (Int) -> Unit,
    onSendToTv: (Int) -> Unit,
    onDismissTvStatus: () -> Unit,
    torrentsState: TorrentsUiState,
    torrentSort: TorrentSort,
    qualityFilter: QualityFilter,
    seasonFilter: SeasonFilter,
    exactSearch: Boolean,
    torrentsOnTv: Boolean,
    playOnPhone: Boolean,
    magnetStatus: String?,
    sendingMagnet: String?,
    onOpenTorrents: () -> Unit,
    onSetSort: (TorrentSort) -> Unit,
    onSetQuality: (QualityFilter) -> Unit,
    onSetSeason: (SeasonFilter) -> Unit,
    onToggleExact: () -> Unit,
    onSendTorrent: (TorrentResult) -> Unit,
    onStopOnTv: () -> Unit,
    onDismissMagnetStatus: () -> Unit,
    closeSheetTick: Int
) {
    val details = content.details

    var torrentsSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(closeSheetTick) {
        if (closeSheetTick > 0) torrentsSheetOpen = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row {
                AsyncImage(
                    model = Constants.posterUrl(details.posterPath),
                    contentDescription = details.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        details.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showOriginalTitles &&
                        details.originalTitle != null &&
                        details.originalTitle != details.title
                    ) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            details.originalTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildString {
                            append(details.releaseDate?.take(4) ?: "—")
                            details.runtimeMinutes?.let { append(" · ${formatRuntime(it)}") }
                            details.numberOfSeasons?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        details.genres.joinToString(", ") { it.name }.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "%.1f".format(details.rating),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.details_votes, details.voteCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (tvEnabled) {
                        val eyeButtons = listOf(
                            1 to tvPlayer1, 2 to tvPlayer2,
                            3 to tvPlayer3, 4 to tvPlayer4
                        ).filter { it.second != null }

                        if (eyeButtons.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                eyeButtons.forEach { (number, _) ->
                                    SmallEyeButton(
                                        buttonNumber = number,
                                        iconFile = eyeIconFiles.getOrNull(number - 1),
                                        containerColor = eyeColor(number, MaterialTheme.colorScheme.primary),
                                        contentColor = Color.White,
                                        sending = tvSendingButton == number,
                                        onClick = { onSendToTv(number) },
                                        size = 36.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (tvEnabled) {
            item {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        torrentsSheetOpen = true
                        onOpenTorrents()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.watch_button))
                }
            }
        }

        tvSendStatus?.let { status ->
            item {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissTvStatus() }
                ) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isFavorite) stringResource(R.string.details_favorite_added)
                        else stringResource(R.string.details_favorite_add)
                    )
                }
                if (details.type == ContentType.MOVIE && isNotReleased(details)) {
                    OutlinedButton(onClick = onToggleAwait) {
                        Icon(
                            if (isAwaited) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isAwaited) stringResource(R.string.details_await_added)
                            else stringResource(R.string.details_await_add)
                        )
                    }
                }
                if (details.type == ContentType.TV) {
                    OutlinedButton(onClick = onToggleTrack) {
                        Icon(
                            if (isTracked) Icons.Filled.Tv else Icons.Filled.TvOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isTracked) stringResource(R.string.details_track_added)
                            else stringResource(R.string.details_track_add)
                        )
                    }
                }
            }
        }

        details.tagline?.let { tagline ->
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "«$tagline»",
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text(
                details.overview?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.details_no_overview),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            TextButton(onClick = onToggleShowMore) {
                Icon(
                    if (content.showMoreExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (content.showMoreExpanded) stringResource(R.string.details_less)
                    else stringResource(R.string.details_more)
                )
            }
            if (content.showMoreExpanded) {
                MoreInfoSection(details)
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            TextButton(onClick = onToggleActors) {
                Icon(
                    if (content.showActors) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (content.showActors) stringResource(R.string.details_hide_actors)
                    else stringResource(R.string.details_show_actors)
                )
            }
            if (content.showActors) {
                when (val actors = content.actors) {
                    is LazyBlockState.Idle -> SectionLoading()
                    is LazyBlockState.Loading -> SectionLoading()
                    is LazyBlockState.Error -> SectionError(actors.message, retry = onToggleActors)
                    is LazyBlockState.Loaded -> {
                        if (actors.data.isEmpty()) {
                            Text(
                                stringResource(R.string.details_actors_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ActorsRow(
                                actors = actors.data,
                                onOpenPerson = onOpenPerson
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                stringResource(R.string.details_similar_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            when (val similar = content.similar) {
                is LazyBlockState.Idle -> {
                    LaunchedEffect(details.id) { onLoadSimilar() }
                    SectionLoading()
                }
                is LazyBlockState.Loading -> SectionLoading()
                is LazyBlockState.Error -> SectionError(similar.message, retry = onLoadSimilar)
                is LazyBlockState.Loaded -> {
                    if (similar.data.isEmpty()) {
                        Text(
                            stringResource(R.string.details_similar_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        SimilarCarousel(
                            movies = similar.data,
                            typeKey = if (details.type == ContentType.TV) "tv" else "movie",
                            onOpenDetails = onOpenDetails
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            TextButton(onClick = onToggleKeywords) {
                Icon(
                    if (content.showKeywords) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (content.showKeywords) stringResource(R.string.details_hide_keywords)
                    else stringResource(R.string.details_show_keywords)
                )
            }
            if (content.showKeywords) {
                when (val kw = content.keywords) {
                    is LazyBlockState.Idle -> SectionLoading()
                    is LazyBlockState.Loading -> SectionLoading()
                    is LazyBlockState.Error -> SectionError(kw.message, retry = onToggleKeywords)
                    is LazyBlockState.Loaded -> {
                        if (kw.data.isEmpty()) {
                            Text(
                                stringResource(R.string.details_keywords_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            KeywordChipsFlow(kw.data)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.tmdb_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (torrentsSheetOpen) {
        TorrentsSheet(
            title = details.title,
            state = torrentsState,
            sort = torrentSort,
            qualityFilter = qualityFilter,
            seasonFilter = seasonFilter,
            exactSearch = exactSearch,
            richView = richView,
            onToggleRich = onToggleRich,
            onTv = torrentsOnTv,
            playOnPhone = playOnPhone,
            magnetStatus = magnetStatus,
            sendingMagnet = sendingMagnet,
            onSetSort = onSetSort,
            onSetQuality = onSetQuality,
            onSetSeason = onSetSeason,
            onToggleExact = onToggleExact,
            onSendTorrent = onSendTorrent,
            onStopOnTv = onStopOnTv,
            onDismissMagnetStatus = onDismissMagnetStatus,
            onDismiss = { torrentsSheetOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorrentsSheet(
    title: String,
    state: TorrentsUiState,
    sort: TorrentSort,
    qualityFilter: QualityFilter,
    seasonFilter: SeasonFilter,
    exactSearch: Boolean,
    richView: Boolean,
    onToggleRich: () -> Unit,
    onTv: Boolean,
    playOnPhone: Boolean,
    magnetStatus: String?,
    sendingMagnet: String?,
    onSetSort: (TorrentSort) -> Unit,
    onSetQuality: (QualityFilter) -> Unit,
    onSetSeason: (SeasonFilter) -> Unit,
    onToggleExact: () -> Unit,
    onSendTorrent: (TorrentResult) -> Unit,
    onStopOnTv: () -> Unit,
    onDismissMagnetStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listMaxHeight = if (magnetStatus != null) 280.dp else 400.dp

    val sortLabels = mapOf(
        TorrentSort.SEEDS to stringResource(R.string.torrents_sort_seeds),
        TorrentSort.SIZE to stringResource(R.string.torrents_sort_size),
        TorrentSort.DATE to stringResource(R.string.torrents_sort_date)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.torrents_title, title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleRich) {
                    Icon(
                        Icons.Filled.ViewAgenda,
                        contentDescription = stringResource(R.string.cd_view_toggle),
                        tint = if (richView) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = when {
                    playOnPhone -> stringResource(R.string.torrents_mode_phone_play)
                    onTv -> stringResource(R.string.torrents_mode_tv_list)
                    else -> stringResource(R.string.torrents_mode_phone)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(TorrentSort.entries.size) { index ->
                    val s = TorrentSort.entries[index]
                    FilterChip(
                        selected = sort == s,
                        onClick = { onSetSort(s) },
                        label = { Text(sortLabels[s] ?: "", maxLines = 1) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = exactSearch, onCheckedChange = { onToggleExact() })
                Text(
                    stringResource(R.string.torrents_exact),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onToggleExact() }
                )
            }

            magnetStatus?.let { status ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissMagnetStatus() }
                ) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            when (val s = state) {
                TorrentsUiState.Idle -> Box(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.torrents_searching)) }

                TorrentsUiState.Loading -> Box(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is TorrentsUiState.Error -> Column(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is TorrentsUiState.Loaded -> {
                    s.nodeName?.let { nodeName ->
                        Text(
                            if (s.fellBack) stringResource(R.string.torrents_node_fallback, nodeName)
                            else stringResource(R.string.torrents_node, nodeName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    // ---------- Каскад ----------
                    // Сезоны, доступные при выбранном качестве
                    val availableSeasons = remember(s.torrents, qualityFilter) {
                        s.torrents
                            .filter { t -> qualityFilter == null || t.quality == qualityFilter }
                            .flatMap { it.seasons }
                            .distinct()
                            .sorted()
                    }
                    // Качества, доступные в выбранном сезоне
                    // (раздачи без seasons — фильмы/полные паки — проходят всегда)
                    val availableQualities = remember(s.torrents, seasonFilter) {
                        s.torrents
                            .filter { t ->
                                seasonFilter == null || t.seasons.isEmpty() || seasonFilter in t.seasons
                            }
                            .mapNotNull { it.quality }
                            .distinct()
                            .sortedDescending()
                    }

                    // Выбор пропал из каскада — сбрасываем его
                    LaunchedEffect(availableSeasons) {
                        if (seasonFilter != null && seasonFilter !in availableSeasons) onSetSeason(null)
                    }
                    LaunchedEffect(availableQualities) {
                        if (qualityFilter != null && qualityFilter !in availableQualities) onSetQuality(null)
                    }

                    val filtered = remember(s.torrents, qualityFilter, seasonFilter) {
                        s.torrents.filter { t ->
                            val seasonOk = seasonFilter == null ||
                                    t.seasons.isEmpty() ||
                                    seasonFilter in t.seasons
                            val qualityOk = qualityFilter == null || t.quality == qualityFilter
                            seasonOk && qualityOk
                        }
                    }

                    val sorted = remember(filtered, sort) {
                        when (sort) {
                            TorrentSort.SEEDS -> filtered.sortedByDescending { it.seeders }
                            TorrentSort.SIZE -> filtered.sortedByDescending { it.sizeBytes }
                            TorrentSort.DATE -> filtered.sortedByDescending { it.date }
                        }
                    }

                    // ---------- Чипы сезонов (только присутствующие) ----------
                    if (availableSeasons.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                FilterChip(
                                    selected = seasonFilter == null,
                                    onClick = { onSetSeason(null) },
                                    label = { Text(stringResource(R.string.seasons_all), maxLines = 1) }
                                )
                            }
                            items(availableSeasons.size) { index ->
                                val sn = availableSeasons[index]
                                FilterChip(
                                    selected = seasonFilter == sn,
                                    onClick = { onSetSeason(sn) },
                                    label = { Text(stringResource(R.string.seasons_one, sn), maxLines = 1) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // ---------- Чипы качеств (только в выбранном сезоне) ----------
                    if (availableQualities.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                FilterChip(
                                    selected = qualityFilter == null,
                                    onClick = { onSetQuality(null) },
                                    label = { Text(stringResource(R.string.quality_all), maxLines = 1) }
                                )
                            }
                            items(availableQualities.size) { index ->
                                val q = availableQualities[index]
                                FilterChip(
                                    selected = qualityFilter == q,
                                    onClick = { onSetQuality(q) },
                                    label = { Text("${q}p", maxLines = 1) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (sorted.isEmpty() && (qualityFilter != null || seasonFilter != null)) {
                        Text(
                            stringResource(R.string.torrents_none_by_filters),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = listMaxHeight),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sorted.size) { index ->
                                if (richView) {
                                    TorrentRowRich(
                                        torrent = sorted[index],
                                        sending = sendingMagnet == sorted[index].magnetUri,
                                        onClick = { onSendTorrent(sorted[index]) }
                                    )
                                } else {
                                    TorrentRow(
                                        torrent = sorted[index],
                                        sending = sendingMagnet == sorted[index].magnetUri,
                                        onClick = { onSendTorrent(sorted[index]) }
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = if (playOnPhone) Modifier.fillMaxWidth() else Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.torrents_close))
                }
                if (!playOnPhone) {
                    OutlinedButton(
                        onClick = onStopOnTv,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.torrents_stop_tv))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TorrentRow(
    torrent: TorrentResult,
    sending: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!sending) onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    torrent.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildMeta(torrent),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val badges = buildList {
                    torrent.quality?.let { add("${it}p") }
                    if (torrent.voices.isNotEmpty()) add(torrent.voices.joinToString(", "))
                    if (torrent.languages.isNotEmpty()) add("🌐 " + torrent.languages.joinToString(" · "))
                    torrent.category?.let { add(it) }
                }
                if (badges.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        badges.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (sending) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * Расширенный ряд раздачи: дата словами + трекеры, чипы озвучки с микрофоном,
 * качество, битрейт, раздают/качают крупно, размер в белой плашке.
 */
@Composable
private fun TorrentRowRich(
    torrent: TorrentResult,
    sending: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!sending) onClick() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                torrent.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDateWords(torrent.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    torrent.tracker,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (torrent.voices.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    torrent.voices.forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    v,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            if (torrent.languages.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    torrent.languages.take(4).forEach { lang ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "🌐 $lang",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    torrent.quality?.let { "${it}p" } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (torrent.bitrate.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                        Text(
                            torrent.bitrate,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Раздают:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    " ↑${torrent.seeders}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF2E9E4F)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Качают:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    " ↓${torrent.leechers}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFC62828)
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(4.dp), color = Color.White) {
                    Text(
                        torrent.sizeName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            if (sending) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.torrents_searching),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDateWords(iso: String): String {
    val d = DateUtils.parse(iso.take(10)) ?: return iso.take(10)
    return d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", DateUtils.locale))
}

private fun buildMeta(torrent: TorrentResult) = buildAnnotatedString {
    val head = listOf(torrent.tracker, torrent.sizeName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    if (head.isNotEmpty()) {
        withStyle(SpanStyle(color = Color(0xFF948F99))) { append(head) }
        append("   ")
    }
    withStyle(SpanStyle(color = Color(0xFF2E9E4F))) { append("↑${torrent.seeders}") }
    append(" ")
    withStyle(SpanStyle(color = Color(0xFFC62828))) { append("↓${torrent.leechers}") }
    if (torrent.date.isNotBlank()) {
        append("   ")
        withStyle(SpanStyle(color = Color(0xFF948F99))) { append(torrent.date.take(10)) }
    }
}

@Composable
private fun SmallEyeButton(
    buttonNumber: Int,
    iconFile: File?,
    containerColor: Color,
    contentColor: Color,
    sending: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 36.dp
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (iconFile != null) Color.Transparent else containerColor,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .clickable { if (!sending) onClick() }
    ) {
        if (sending) {
            Box(
                Modifier
                    .size(size)
                    .background(containerColor, shape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        } else if (iconFile != null) {
            AsyncImage(
                model = iconFile,
                contentDescription = "Eye $buttonNumber",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )
        } else {
            Box(
                Modifier
                    .size(size)
                    .background(containerColor, shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = "Eye $buttonNumber",
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun KeywordChipsFlow(keywords: List<Keyword>) {
    Column {
        keywords.take(15).forEach { keyword ->
            AssistChip(
                onClick = {},
                label = { Text(keyword.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ActorsRow(
    actors: List<Actor>,
    onOpenPerson: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(actors, key = { it.id }) { actor ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clickable { onOpenPerson(actor.id) }
            ) {
                AsyncImage(
                    model = profileUrl(actor.profilePath),
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    actor.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                actor.character?.let { character ->
                    Text(
                        character,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarCarousel(
    movies: List<Movie>,
    typeKey: String,
    onOpenDetails: (String, Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(movies, key = { it.id }) { movie ->
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .clickable { onOpenDetails(typeKey, movie.id) }
            ) {
                AsyncImage(
                    model = Constants.posterUrlSmall(movie.posterPath),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    movie.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MoreInfoSection(details: TitleDetails) {
    Column {
        if (details.type == ContentType.MOVIE) {
            InfoRow(stringResource(R.string.details_budget), formatMoney(details.budget))
            InfoRow(stringResource(R.string.details_revenue), formatMoney(details.revenue))
        }
        if (details.companies.isNotEmpty()) {
            InfoRow(stringResource(R.string.details_studios), details.companies.joinToString(", "))
        }
        if (details.countries.isNotEmpty()) {
            InfoRow(stringResource(R.string.details_countries), details.countries.joinToString(", "))
        }
        if (details.languages.isNotEmpty()) {
            InfoRow(stringResource(R.string.details_languages), details.languages.joinToString(", "))
        }
        details.status?.let { InfoRow(stringResource(R.string.details_status), localizedStatus(it)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionLoading() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SectionError(message: String, retry: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.search_error, message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = retry) { Text(stringResource(R.string.common_retry)) }
    }
}

private fun profileUrl(profilePath: String?): String =
    if (profilePath.isNullOrBlank())
        Constants.FALLBACK_POSTER
    else
        "${Constants.IMAGE_BASE_URL}w185$profilePath"

private fun isNotReleased(details: TitleDetails): Boolean =
    details.releaseDate?.let {
        runCatching { LocalDate.parse(it).isAfter(LocalDate.now()) }.getOrDefault(false)
    } ?: false

private fun formatRuntime(minutes: Int): String =
    "${minutes / 60} ч ${minutes % 60} мин"

private fun formatMoney(amount: Long): String {
    if (amount <= 0) return "—"
    return "$" + String.format(Locale.US, "%,d", amount).replace(",", " ")
}

@Composable
private fun localizedStatus(status: String): String = when (status) {
    "Released" -> stringResource(R.string.status_released)
    "Post Production" -> stringResource(R.string.status_post_production)
    "In Production" -> stringResource(R.string.status_in_production)
    "Planned" -> stringResource(R.string.status_planned)
    "Rumored" -> stringResource(R.string.status_rumored)
    "Returning Series" -> stringResource(R.string.status_returning)
    "Ended" -> stringResource(R.string.status_ended)
    "Canceled" -> stringResource(R.string.status_canceled)
    else -> status
}