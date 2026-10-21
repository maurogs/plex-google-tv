package dev.plexlite.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.plexlite.tv.ui.components.ToolbarChip
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.LibrarySort
import dev.plexlite.tv.data.Hub
import dev.plexlite.tv.data.LibraryTab
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
import dev.plexlite.tv.ui.home.HubFeed
import dev.plexlite.tv.ui.home.FeedFocus
import dev.plexlite.tv.ui.home.toFeedRow
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

    var recommended by mutableStateOf<List<Hub>?>(null)
        private set
    val feedFocus = FeedFocus()

    var lastFocusedIndex = 0
    /** Set when the user switches tabs, so the new tab keeps focus on the tab chip instead of the content. */
    var focusTabOnEnter = false
    /** The library bar is hidden while browsing the content; Back or ↑ from the tabs brings it back. */
    var barVisible by mutableStateOf(false)
    private var loadJob: Job? = null
    private var browseStarted = false
    private var recommendedJob: Job? = null

    fun ensureBrowse() {
        if (browseStarted) return
        browseStarted = true
        loadMore()
        loadLetters()
    }

    fun loadRecommended() {
        if (recommendedJob?.isActive == true) return
        recommendedJob = viewModelScope.launch {
            runCatching { AppGraph.requireServer().sectionHubs(sectionId) }
                .onSuccess { recommended = it }
                .onFailure { if (recommended == null) recommended = emptyList() }
        }
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

@Composable
fun LibraryScreen(onOpenItem: (Metadata) -> Unit, actions: HeaderActions, vm: LibraryViewModel = viewModel()) {
    val tab = AppGraph.libraryTab
    LaunchedEffect(tab) { if (tab == LibraryTab.BROWSE) vm.ensureBrowse() }
    LifecycleResumeEffect(tab) {
        if (tab == LibraryTab.RECOMMENDED) vm.loadRecommended()
        onPauseOrDispose { }
    }
    // Back from the content reveals the bar (focused on this library); Back from the bar returns Home.
    BackHandler(enabled = !vm.barVisible) { vm.barVisible = true }
    BackHandler(enabled = vm.barVisible) {
        AppGraph.focusHomeChip = true
        actions.onHome()
    }
    when (tab) {
        LibraryTab.RECOMMENDED -> RecommendedTab(onOpenItem, actions, vm)
        LibraryTab.BROWSE -> BrowseTab(onOpenItem, actions, vm)
    }
}

@Composable
private fun LibraryHeader(vm: LibraryViewModel, actions: HeaderActions) {
    AnimatedVisibility(
        visible = vm.barVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            LibraryBar(selectedKey = vm.sectionId, actions = actions, focusSelected = true)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TitleRow(vm: LibraryViewModel, focusTab: Boolean) {
    val tabFocus = remember { FocusRequester() }
    fun select(tab: LibraryTab) {
        if (AppGraph.libraryTab == tab) return
        vm.focusTabOnEnter = true
        AppGraph.libraryTab = tab
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 48.dp)
            .onFocusChanged { if (it.hasFocus) vm.barVisible = false }
            .onPreviewKeyEvent {
                val reveal = !vm.barVisible && it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown
                if (reveal) vm.barVisible = true
                reveal
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(vm.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.width(20.dp))
        Box(Modifier.size(width = 1.dp, height = 28.dp).background(Color.White.copy(alpha = 0.25f)))
        Spacer(Modifier.width(8.dp))
        LibraryTab.entries.forEach { tab ->
            val selected = AppGraph.libraryTab == tab
            TabLabel(
                title = stringResource(if (tab == LibraryTab.RECOMMENDED) R.string.library_tab_recommended else R.string.library_tab_browse),
                selected = selected,
                onClick = { select(tab) },
                modifier = if (selected) Modifier.focusRequester(tabFocus) else Modifier,
            )
        }
    }
    LaunchedEffect(Unit) { if (focusTab) runCatching { tabFocus.requestFocus() } }
}

/** Text tab: the selected one is white with an accent underline; focus adds a soft highlight. */
@Composable
private fun TabLabel(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(if (selected) Accent else Color.Transparent, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun RecommendedTab(onOpenItem: (Metadata) -> Unit, actions: HeaderActions, vm: LibraryViewModel) {
    val hubs = vm.recommended
    val focusTab = remember { vm.focusTabOnEnter.also { vm.focusTabOnEnter = false } }
    val header: @Composable () -> Unit = {
        LibraryHeader(vm, actions)
        TitleRow(vm, focusTab)
    }
    if (hubs.isNullOrEmpty()) {
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            header()
            CenteredMessage(stringResource(if (hubs == null) R.string.loading else R.string.library_empty))
        }
    } else {
        HubFeed(
            rows = hubs.map { it.toFeedRow() },
            focus = vm.feedFocus,
            onOpenItem = onOpenItem,
            heroHeight = 150.dp,
            focusFirstCard = !focusTab,
            onRowsFocused = { vm.barVisible = false },
            header = header,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowseTab(onOpenItem: (Metadata) -> Unit, actions: HeaderActions, vm: LibraryViewModel) {
    val restore = remember { FocusRequester() }
    val restoreIndex = remember { vm.lastFocusedIndex }
    val focusTab = remember { vm.focusTabOnEnter.also { vm.focusTabOnEnter = false } }

    val focusedItem = remember { mutableStateOf<Metadata?>(null) }
    val focusedIndex = remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val jumpFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        DebouncedHero(focusedItem, 400) { HeroBackdrop(it, alpha = 0.35f, widthFraction = 0.8f, heightFraction = 0.8f) }
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            LibraryHeader(vm, actions)
            TitleRow(vm, focusTab)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 48.dp).onFocusChanged { if (it.hasFocus) vm.barVisible = false },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val filterLabel = when {
                    vm.unwatchedOnly && vm.isShowLibrary -> R.string.library_all_shows
                    vm.unwatchedOnly -> R.string.library_all_movies
                    vm.isShowLibrary -> R.string.library_only_unwatched_shows
                    else -> R.string.library_only_unwatched_movies
                }
                ToolbarChip(R.drawable.ic_filter, stringResource(filterLabel), onClick = vm::toggleUnwatched)
                Spacer(Modifier.width(12.dp))
                SortButton(vm.sort, vm::selectSort)
                vm.total?.let {
                    Spacer(Modifier.width(20.dp))
                    Text(pluralStringResource(R.plurals.library_count, it, it), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                vm.error != null -> CenteredMessage(vm.error!!)
                vm.items.isEmpty() && vm.total == null -> CenteredMessage(stringResource(R.string.loading))
                vm.items.isEmpty() -> CenteredMessage(stringResource(R.string.library_empty))
                else -> Row(Modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) vm.barVisible = false }) {
                    val hasRail = vm.letters.size > 1
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(7),
                        contentPadding = PaddingValues(start = 48.dp, end = if (hasRail) 16.dp else 48.dp, top = 16.dp, bottom = 24.dp),
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
        if (vm.items.isNotEmpty() && !focusTab) runCatching { restore.requestFocus() }
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
        ToolbarChip(
            R.drawable.ic_sort,
            stringResource(R.string.library_sort_by, stringResource(current.labelRes)) + "  ▾",
            onClick = popup::show,
            modifier = Modifier.focusRequester(popup.anchorFocus),
        )
        if (popup.open) {
            MenuPopup(
                rows = LibrarySort.entries.map { option ->
                    PopupRow(stringResource(option.labelRes), checked = option == current) {
                        popup.dismiss()
                        onSelect(option)
                    }
                },
                onDismiss = popup::dismiss,
                offsetY = 48.dp,
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
