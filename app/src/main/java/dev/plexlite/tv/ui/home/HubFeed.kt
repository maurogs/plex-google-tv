package dev.plexlite.tv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plexlite.tv.data.Hub
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.MediaRow
import kotlinx.coroutines.launch

data class FeedRow(val key: String, val title: String, val items: List<Metadata>, val style: CardStyle)

class FeedFocus {
    var lastFocused: String? = null
    var heroItem: Metadata? = null
}

fun Hub.toFeedRow(): FeedRow = FeedRow(
    key = hubIdentifier,
    title = title,
    items = metadata,
    style = if (type == "episode" || type == "clip") CardStyle.WIDE else CardStyle.POSTER,
)

@OptIn(ExperimentalFoundationApi::class)
private object NoVerticalBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HubFeed(
    rows: List<FeedRow>,
    focus: FeedFocus,
    onOpenItem: (Metadata) -> Unit,
    heroHeight: Dp = 180.dp,
    focusFirstCard: Boolean = true,
    header: @Composable () -> Unit,
) {
    val restore = remember { FocusRequester() }
    val initialKey = remember { focus.lastFocused }
    val firstKey = rows.firstOrNull()?.let { row -> row.items.firstOrNull()?.let { "${row.key}/${it.ratingKey}" } }
    val targetKey = initialKey ?: firstKey

    val focusedItem = remember { mutableStateOf(focus.heroItem ?: rows.firstOrNull()?.items?.firstOrNull()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun onCardFocused(key: String, item: Metadata, row: Int) {
        if (listState.firstVisibleItemIndex != row || listState.firstVisibleItemScrollOffset != 0) {
            scope.launch { listState.animateScrollToItem(row) }
        }
        focus.lastFocused = key
        focus.heroItem = item
        focusedItem.value = item
    }

    Box(Modifier.fillMaxSize()) {
        DebouncedHero(focusedItem) { HeroBackdrop(it) }
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            header()
            DebouncedHero(focusedItem) { HeroInfo(it, height = heroHeight) }
            val rowSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoVerticalBringIntoView) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    rows.distinctBy { it.key }.forEachIndexed { index, row ->
                        item(key = "row:${row.key}") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                                MediaRow(
                                    title = row.title,
                                    items = row.items,
                                    style = row.style,
                                    onClick = onOpenItem,
                                    itemModifier = { item ->
                                        if ("${row.key}/${item.ratingKey}" == targetKey) Modifier.focusRequester(restore) else Modifier
                                    },
                                    onFocused = { onCardFocused("${row.key}/${it.ratingKey}", it, index) },
                                    cardWidth = if (row.style == CardStyle.POSTER) 120.dp else 230.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(targetKey) { if (targetKey != null && focusFirstCard) runCatching { restore.requestFocus() } }
}
