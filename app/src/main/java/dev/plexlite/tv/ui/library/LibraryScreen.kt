package dev.plexlite.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.CenteredMessage
import dev.plexlite.tv.ui.components.MediaCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LibraryViewModel(savedState: SavedStateHandle) : ViewModel() {
    val sectionId: String = checkNotNull(savedState["id"])
    val title: String = savedState.get<String>("title").orEmpty()
    private val isShowLibrary: Boolean = savedState.get<String>("type") == "show"

    val items = mutableStateListOf<Metadata>()
    var total by mutableStateOf<Int?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var unwatchedOnly by mutableStateOf(false)
        private set

    var lastFocusedIndex = 0
    private var loadJob: Job? = null

    init {
        loadMore()
    }

    fun toggleUnwatched() {
        unwatchedOnly = !unwatchedOnly
        loadJob?.cancel()
        loadJob = null
        items.clear()
        total = null
        lastFocusedIndex = 0
        loadMore()
    }

    fun loadMore() {
        if (loadJob?.isActive == true) return
        val known = total
        if (known != null && items.size >= known) return
        loadJob = viewModelScope.launch {
            val start = items.size
            runCatching { AppGraph.requireServer().sectionItems(sectionId, start, PAGE, unwatchedOnly, isShowLibrary) }
                .onSuccess { page ->
                    val seen = items.mapTo(HashSet()) { it.ratingKey }
                    items.addAll(page.metadata.filter { seen.add(it.ratingKey) })
                    total = page.totalSize ?: (start + page.metadata.size)
                    error = null
                }
                .onFailure { if (items.isEmpty()) error = "Error cargando la biblioteca: ${it.message}" }
        }
    }

    private companion object {
        const val PAGE = 72
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenItem: (Metadata) -> Unit, vm: LibraryViewModel = viewModel()) {
    val restore = remember { FocusRequester() }
    val restoreIndex = remember(vm.unwatchedOnly) { vm.lastFocusedIndex }

    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(vm.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            vm.total?.let {
                Spacer(Modifier.width(16.dp))
                Text("$it títulos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(32.dp))
            FilterChip(selected = vm.unwatchedOnly, onClick = vm::toggleUnwatched) { Text("Sin ver") }
        }
        when {
            vm.error != null -> CenteredMessage(vm.error!!)
            vm.items.isEmpty() && vm.total == null -> CenteredMessage("Cargando…")
            vm.items.isEmpty() -> CenteredMessage("No hay nada aquí")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(vm.items, key = { _, item -> item.ratingKey }) { index, item ->
                    if (index >= vm.items.size - 28) LaunchedEffect(vm.items.size) { vm.loadMore() }
                    MediaCard(
                        item = item,
                        style = CardStyle.POSTER,
                        onClick = { onOpenItem(item) },
                        width = 140.dp,
                        onFocused = { vm.lastFocusedIndex = index },
                        modifier = if (index == restoreIndex) Modifier.focusRequester(restore) else Modifier,
                    )
                }
            }
        }
    }

    LaunchedEffect(vm.items.isNotEmpty(), vm.unwatchedOnly) {
        if (vm.items.isNotEmpty()) runCatching { restore.requestFocus() }
    }
}
