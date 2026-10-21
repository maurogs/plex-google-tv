package dev.plexlite.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.LibrarySort
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.CenteredMessage
import dev.plexlite.tv.ui.components.MediaCard
import dev.plexlite.tv.ui.components.MenuPopup
import dev.plexlite.tv.ui.components.PopupRow
import dev.plexlite.tv.ui.components.rememberPopupState
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.home.HeaderActions
import dev.plexlite.tv.ui.home.HeroBackdrop
import dev.plexlite.tv.ui.home.DebouncedHero
import dev.plexlite.tv.ui.home.LibraryBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LibraryViewModel(savedState: SavedStateHandle) : ViewModel() {
    val sectionId: String = checkNotNull(savedState["id"])
    val title: String = savedState.get<String>("title").orEmpty()
    val isShowLibrary: Boolean = savedState.get<String>("type") == "show"

    val items = mutableStateListOf<Metadata>()
    var total by mutableStateOf<Int?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var unwatchedOnly by mutableStateOf(false)
        private set
    var sort by mutableStateOf(
        AppGraph.prefs.librarySorts[sectionId]?.let { saved -> LibrarySort.entries.firstOrNull { it.name == saved } } ?: LibrarySort.TITLE,
    )
        private set
    var letters by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set
    var pendingFocus by mutableStateOf<Int?>(null)

    var lastFocusedIndex = 0
    private var loadJob: Job? = null

    init {
        loadMore()
        loadLetters()
    }

    fun toggleUnwatched() {
        unwatchedOnly = !unwatchedOnly
        reload()
    }

    fun selectSort(value: LibrarySort) {
        if (value == sort) return
        sort = value
        AppGraph.prefs.librarySorts = AppGraph.prefs.librarySorts + (sectionId to value.name)
        reload()
    }

    private fun reload() {
        lastFocusedIndex = 0
        loadJob?.cancel()
        val filter = unwatchedOnly
        val order = sort
        loadJob = viewModelScope.launch {
            runCatching { AppGraph.requireServer().sectionItems(sectionId, 0, PAGE, filter, isShowLibrary, order) }
                .onSuccess { page ->
                    items.clear()
                    items.addAll(page.metadata.distinctBy { it.ratingKey })
                    total = page.totalSize ?: page.metadata.size
                    error = null
                }
        }
        loadLetters()
    }

    private fun loadLetters() {
        letters = emptyList()
        if (sort != LibrarySort.TITLE) return
        val filter = unwatchedOnly
        viewModelScope.launch {
            runCatching { AppGraph.requireServer().firstCharacters(sectionId, filter, isShowLibrary) }.onSuccess { groups ->
                var offset = 0
                letters = groups.filter { it.size > 0 }.map { group ->
                    (group.title.ifBlank { group.key }) to offset.also { offset += group.size }
                }
            }
        }
    }

    fun jumpTo(offset: Int) {
        if (offset < items.size) {
            pendingFocus = offset
            return
        }
        loadJob?.cancel()
        val start = items.size
        loadJob = viewModelScope.launch {
            runCatching { AppGraph.requireServer().sectionItems(sectionId, start, offset - start + PAGE, unwatchedOnly, isShowLibrary, sort) }
                .onSuccess { page ->
                    val seen = items.mapTo(HashSet()) { it.ratingKey }
                    items.addAll(page.metadata.filter { seen.add(it.ratingKey) })
                    total = page.totalSize ?: items.size
                    pendingFocus = offset.coerceAtMost(items.lastIndex)
                }
        }
    }

    fun loadMore() {
        if (loadJob?.isActive == true) return
        val known = total
        if (known != null && items.size >= known) return
        loadJob = viewModelScope.launch {
            val start = items.size
            runCatching { AppGraph.requireServer().sectionItems(sectionId, start, PAGE, unwatchedOnly, isShowLibrary, sort) }
                .onSuccess { page ->
                    val seen = items.mapTo(HashSet()) { it.ratingKey }
                    items.addAll(page.metadata.filter { seen.add(it.ratingKey) })
                    total = page.totalSize ?: (start + page.metadata.size)
                    error = null
                }
                .onFailure { if (items.isEmpty()) error = AppGraph.str(R.string.library_error, it.message.orEmpty()) }
        }
    }

    private companion object {
        const val PAGE = 72
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenItem: (Metadata) -> Unit, actions: HeaderActions, vm: LibraryViewModel = viewModel()) {
    val restore = remember { FocusRequester() }
    val restoreIndex = remember { vm.lastFocusedIndex }

    val focusedItem = remember { mutableStateOf<Metadata?>(null) }
    val focusedIndex = remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val jumpFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        DebouncedHero(focusedItem, 400) { HeroBackdrop(it, alpha = 0.35f, widthFraction = 0.8f, heightFraction = 0.8f) }
        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            LibraryBar(selectedKey = vm.sectionId, actions = actions)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(vm.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                vm.total?.let {
                    Spacer(Modifier.width(16.dp))
                    Text(pluralStringResource(R.plurals.library_count, it, it), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(32.dp))
                val filterLabel = when {
                    vm.unwatchedOnly && vm.isShowLibrary -> R.string.library_all_shows
                    vm.unwatchedOnly -> R.string.library_all_movies
                    vm.isShowLibrary -> R.string.library_only_unwatched_shows
                    else -> R.string.library_only_unwatched_movies
                }
                OutlinedButton(onClick = vm::toggleUnwatched) { Text(stringResource(filterLabel)) }
                Spacer(Modifier.width(16.dp))
                SortButton(vm.sort, vm::selectSort)
            }
            when {
                vm.error != null -> CenteredMessage(vm.error!!)
                vm.items.isEmpty() && vm.total == null -> CenteredMessage(stringResource(R.string.loading))
                vm.items.isEmpty() -> CenteredMessage(stringResource(R.string.library_empty))
                else -> Row(Modifier.fillMaxSize()) {
                    val hasRail = vm.letters.size > 1
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(7),
                        contentPadding = PaddingValues(start = 48.dp, end = if (hasRail) 16.dp else 48.dp, top = 24.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        itemsIndexed(vm.items, key = { _, item -> item.ratingKey }) { index, item ->
                            if (index >= vm.items.size - 28) LaunchedEffect(vm.items.size) { vm.loadMore() }
                            MediaCard(
                                item = item,
                                style = CardStyle.POSTER,
                                onClick = { onOpenItem(item) },
                                width = 140.dp,
                                onFocused = {
                                    vm.lastFocusedIndex = index
                                    focusedIndex.intValue = index
                                    focusedItem.value = item
                                },
                                modifier = Modifier
                                    .then(if (index == restoreIndex) Modifier.focusRequester(restore) else Modifier)
                                    .then(if (index == vm.pendingFocus) Modifier.focusRequester(jumpFocus) else Modifier),
                            )
                        }
                    }
                    if (hasRail) LetterRail(vm.letters, focusedIndex, vm::jumpTo)
                }
            }
        }
    }

    LaunchedEffect(vm.items.isNotEmpty()) {
        if (vm.items.isNotEmpty()) runCatching { restore.requestFocus() }
    }
    LaunchedEffect(vm.pendingFocus) {
        val target = vm.pendingFocus ?: return@LaunchedEffect
        gridState.scrollToItem(target)
        withFrameNanos { }
        runCatching { jumpFocus.requestFocus() }
        vm.pendingFocus = null
    }
}

@Composable
private fun SortButton(current: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    val popup = rememberPopupState()
    Box {
        OutlinedButton(onClick = popup::show, modifier = Modifier.focusRequester(popup.anchorFocus)) {
            Text(stringResource(R.string.library_sort_by, stringResource(current.labelRes)) + "  ▾")
        }
        if (popup.open) {
            MenuPopup(
                rows = LibrarySort.entries.map { option ->
                    PopupRow(stringResource(option.labelRes), checked = option == current) {
                        popup.dismiss()
                        onSelect(option)
                    }
                },
                onDismiss = popup::dismiss,
                offsetY = 56.dp,
                width = 320.dp,
            )
        }
    }
    popup.RestoreFocus()
}

private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.TITLE -> R.string.sort_title
        LibrarySort.YEAR -> R.string.sort_year
        LibrarySort.ADDED -> R.string.sort_added
        LibrarySort.RATING -> R.string.sort_rating
    }

@Composable
private fun LetterRail(letters: List<Pair<String, Int>>, focusedIndex: IntState, onJump: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.width(40.dp).fillMaxHeight().padding(end = 8.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(letters, key = { it.first }) { (letter, offset) ->
            val current = letters.lastOrNull { it.second <= focusedIndex.intValue }?.first == letter
            Surface(
                onClick = { onJump(offset) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = if (current) Accent else Color.White.copy(alpha = 0.7f),
                    focusedContainerColor = Accent,
                    focusedContentColor = Color.Black,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.35f),
                modifier = Modifier.size(width = 26.dp, height = 15.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(letter, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
