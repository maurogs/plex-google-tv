package dev.plexlite.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.CenteredMessage
import dev.plexlite.tv.ui.components.MediaRow
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.SurfaceColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SearchGroup(val type: String, val items: List<Metadata>)

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var groups by mutableStateOf<List<SearchGroup>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchedQuery by mutableStateOf("")
        private set

    private var job: Job? = null

    fun type(text: String) = update(query + text)

    fun delete() = update(query.dropLast(1))

    fun clear() = update("")

    private fun update(value: String) {
        if (value == query) return
        query = value
        job?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            groups = emptyList()
            searching = false
            searchedQuery = ""
            return
        }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            searching = true
            val hidden = AppGraph.libraryLayout.hidden
            val hubs = runCatching { AppGraph.requireServer().search(trimmed) }.getOrDefault(emptyList())
            groups = TYPES.mapNotNull { type ->
                val items = hubs.filter { it.type == type }
                    .flatMap { it.metadata }
                    .filter { it.type == type && it.librarySectionID !in hidden }
                    .distinctBy { it.ratingKey }
                if (items.isEmpty()) null else SearchGroup(type, items)
            }
            searchedQuery = trimmed
            searching = false
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        val TYPES = listOf("movie", "show", "episode")
    }
}

private const val LETTERS = "abcdefghijklmnñopqrstuvwxyz123456789"

@Composable
fun SearchScreen(onOpenItem: (Metadata) -> Unit, vm: SearchViewModel = viewModel()) {
    val firstKey = remember { FocusRequester() }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 40.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val char = event.utf16CodePoint.toChar()
                if (char.isLetterOrDigit()) {
                    vm.type(char.lowercase())
                    true
                } else {
                    false
                }
            },
    ) {
        Column(Modifier.width(344.dp)) {
            QueryField(vm.query)
            Spacer(Modifier.height(12.dp))
            Keyboard(vm, firstKey)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                vm.query.isBlank() -> CenteredMessage(stringResource(R.string.search_hint))
                vm.groups.isEmpty() && !vm.searching && vm.searchedQuery.isNotEmpty() ->
                    CenteredMessage(stringResource(R.string.search_no_results, vm.searchedQuery))
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(vm.groups, key = { it.type }) { group ->
                        MediaRow(
                            title = stringResource(
                                when (group.type) {
                                    "movie" -> R.string.search_movies
                                    "show" -> R.string.search_shows
                                    else -> R.string.search_episodes
                                },
                            ),
                            items = group.items,
                            style = if (group.type == "episode") CardStyle.WIDE else CardStyle.POSTER,
                            onClick = onOpenItem,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
}

@Composable
private fun QueryField(query: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                query + "▏",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Keyboard(vm: SearchViewModel, firstKey: FocusRequester) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LETTERS.chunked(6).forEachIndexed { row, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEachIndexed { column, key ->
                    Key(
                        onClick = { vm.type(key.toString()) },
                        modifier = if (row == 0 && column == 0) Modifier.focusRequester(firstKey) else Modifier,
                    ) { Text(key.toString(), fontSize = 20.sp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(onClick = { vm.type("0") }) { Text("0", fontSize = 20.sp) }
            Key(onClick = { vm.type(" ") }, width = 2) { Text("␣", fontSize = 20.sp) }
            Key(onClick = vm::delete) {
                Icon(painterResource(R.drawable.ic_backspace), contentDescription = stringResource(R.string.search_delete), modifier = Modifier.size(22.dp))
            }
            Key(onClick = vm::clear, width = 2) { Text(stringResource(R.string.search_clear), fontSize = 15.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun Key(onClick: () -> Unit, modifier: Modifier = Modifier, width: Int = 1, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceColor,
            contentColor = Color.White,
            focusedContainerColor = Accent,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        modifier = modifier
            .width(KEY_SIZE * width + 8.dp * (width - 1))
            .height(KEY_SIZE),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

private val KEY_SIZE = 48.dp
