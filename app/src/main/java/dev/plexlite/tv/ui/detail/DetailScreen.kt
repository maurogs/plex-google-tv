package dev.plexlite.tv.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.data.Part
import dev.plexlite.tv.data.Stream
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.CenteredMessage
import dev.plexlite.tv.ui.components.MediaCard
import dev.plexlite.tv.ui.home.DebouncedHero
import dev.plexlite.tv.ui.components.formatClock
import dev.plexlite.tv.ui.components.formatDuration
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.Background
import kotlinx.coroutines.launch

class DetailViewModel(savedState: SavedStateHandle) : ViewModel() {
    private val ratingKey: String = checkNotNull(savedState["ratingKey"])

    var item by mutableStateOf<Metadata?>(null)
        private set
    var seasons by mutableStateOf<List<Metadata>>(emptyList())
        private set
    var selectedSeason by mutableStateOf<String?>(null)
        private set
    var episodes by mutableStateOf<List<Metadata>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            val server = AppGraph.requireServer()
            runCatching { server.metadata(ratingKey, extras = true) }
                .onSuccess { loaded ->
                    item = loaded
                    if (loaded?.type == "show") loadSeasons(loaded)
                }
                .onFailure { if (item == null) error = AppGraph.str(R.string.detail_error, it.message.orEmpty()) }
        }
    }

    private suspend fun loadSeasons(show: Metadata) {
        val list = runCatching { AppGraph.requireServer().children(show.ratingKey) }.getOrDefault(emptyList())
        seasons = list
        val onDeckSeason = show.onDeck?.metadata?.firstOrNull()?.parentRatingKey
        val target = selectedSeason ?: onDeckSeason?.takeIf { key -> list.any { it.ratingKey == key } }
            ?: list.firstOrNull { !it.isWatched }?.ratingKey ?: list.firstOrNull()?.ratingKey
        target?.let { selectSeason(it) }
    }

    fun selectSeason(seasonKey: String) {
        selectedSeason = seasonKey
        viewModelScope.launch {
            episodes = runCatching { AppGraph.requireServer().children(seasonKey) }.getOrDefault(emptyList())
        }
    }

    fun selectAudio(part: Part, stream: Stream) {
        viewModelScope.launch {
            runCatching { AppGraph.requireServer().selectAudioStream(part.id, stream.id) }
            refresh()
        }
    }

    fun selectSubtitle(part: Part, stream: Stream?) {
        viewModelScope.launch {
            runCatching { AppGraph.requireServer().selectSubtitleStream(part.id, stream?.id ?: 0L) }
            refresh()
        }
    }

    fun toggleWatched() {
        val current = item ?: return
        viewModelScope.launch {
            runCatching { AppGraph.requireServer().scrobble(current.ratingKey, !current.isWatched) }
            refresh()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(onPlay: (Metadata, Boolean) -> Unit, vm: DetailViewModel = viewModel()) {
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val item = vm.item
    when {
        item != null -> DetailContent(item, vm, onPlay)
        vm.error != null -> CenteredMessage(vm.error!!)
        else -> CenteredMessage(stringResource(R.string.loading))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DetailContent(item: Metadata, vm: DetailViewModel, onPlay: (Metadata, Boolean) -> Unit) {
    val server = AppGraph.server
    val playFocus = remember { FocusRequester() }
    val isShow = item.type == "show"
    val playTarget: Metadata? = if (isShow) item.onDeck?.metadata?.firstOrNull() ?: vm.episodes.firstOrNull() else item

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = server?.photoUrl(item.art, 1280, 720),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.35f,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Background, 0.75f to Background.copy(alpha = 0.6f), 1f to Color.Transparent),
            ),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "info") {
                Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.fillMaxWidth(0.6f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(metaLine(item), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RatingsRow(item)
                    item.summary?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    item.directors.takeIf { it.isNotEmpty() }?.let { directors ->
                        Text(
                            stringResource(R.string.detail_directed_by, directors.take(2).joinToString(", ") { it.tag }),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        if (playTarget != null) {
                            val resume = playTarget.viewOffset?.takeIf { it > 0 }
                            val playLabel = stringResource(if (resume != null) R.string.detail_resume else R.string.detail_play)
                            val label = buildString {
                                append(playLabel)
                                if (isShow) playTarget.episodeLabel?.let { append(" $it") }
                                if (resume != null) append(" (${formatClock(resume)})")
                            }
                            ActionButton(
                                icon = R.drawable.ic_play,
                                label = label,
                                onClick = { onPlay(playTarget, false) },
                                primary = true,
                                modifier = Modifier.focusRequester(playFocus),
                            )
                            if (resume != null) {
                                ActionButton(
                                    icon = R.drawable.ic_replay,
                                    label = stringResource(R.string.detail_from_start),
                                    onClick = { onPlay(playTarget, true) },
                                )
                            }
                        }
                        ActionButton(
                            icon = R.drawable.ic_check,
                            label = stringResource(if (item.isWatched) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
                            onClick = vm::toggleWatched,
                            iconTint = if (item.isWatched) Accent else Color.Unspecified,
                        )
                    }
                    if (item.type == "movie") {
                        StreamChips(item, onSelectAudio = vm::selectAudio, onSelectSubtitle = vm::selectSubtitle)
                    }
                }
            }
            if (isShow && vm.seasons.isNotEmpty()) {
                item(key = "seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(vm.seasons, key = { it.ratingKey }) { season ->
                            FilterChip(
                                selected = season.ratingKey == vm.selectedSeason,
                                onClick = { vm.selectSeason(season.ratingKey) },
                            ) { Text(season.title) }
                        }
                    }
                }
                item(key = "episodes") {
                    val focusedEpisode = remember(vm.selectedSeason) { mutableStateOf<Metadata?>(null) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EpisodeInfo(focusedEpisode, fallback = vm.episodes.firstOrNull { !it.isWatched } ?: vm.episodes.firstOrNull())
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(vm.episodes.distinctBy { it.ratingKey }, key = { it.ratingKey }) { episode ->
                                MediaCard(
                                    item = episode,
                                    style = CardStyle.WIDE,
                                    onClick = { onPlay(episode, false) },
                                    width = 280.dp,
                                    title = episode.title,
                                    subtitle = episode.duration?.let { formatDuration(it) },
                                    badge = episode.index?.let { stringResource(R.string.episode_badge, it) },
                                    onFocused = { focusedEpisode.value = episode },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "cast") { CastRow(item.roles) }
            item(key = "extras") { ExtrasRow(item.extras?.metadata.orEmpty()) { onPlay(it, true) } }
        }
        }
    }

    LaunchedEffect(item.ratingKey, playTarget != null) {
        if (playTarget != null) runCatching { playFocus.requestFocus() }
    }
}

@Composable
private fun EpisodeInfo(source: State<Metadata?>, fallback: Metadata?) {
    DebouncedHero(source, 200) { focused ->
        val episode = focused ?: fallback
        Column(
            Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth(0.7f)
                .height(84.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (episode == null) return@Column
            Text(
                listOfNotNull(
                    episode.index?.let { stringResource(R.string.episode_badge, it) },
                    episode.title,
                    episode.duration?.let { formatDuration(it) },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.summary?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val MinimalScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset
            else -> trailing - containerSize
        }
    }
}

private fun metaLine(item: Metadata): String = listOfNotNull(
    item.year?.toString(),
    item.contentRating,
    item.duration?.takeIf { item.type == "movie" }?.let { formatDuration(it) },
    item.childCount?.takeIf { item.type == "show" }?.let { AppGraph.plural(R.plurals.detail_seasons, it) },
    item.media.firstOrNull()?.videoDynamicRange?.takeIf { item.type == "movie" && it != "SDR" },
    item.genres.take(3).joinToString(", ") { it.tag }.ifEmpty { null },
).joinToString("  ·  ")
