package dev.plexlite.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Directory
import dev.plexlite.tv.data.Hub
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.CenteredMessage
import dev.plexlite.tv.ui.components.MediaRow
import dev.plexlite.tv.ui.theme.Accent
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class HomeContent(
    val continueWatching: List<Metadata>,
    val hubs: List<Hub>,
)

class HomeViewModel : ViewModel() {
    var content by mutableStateOf<HomeContent?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var lastFocused: String? = null
    private var loading = false

    fun refresh() {
        if (loading) return
        if (AppGraph.serverCount == null) loadServerCount()
        if (AppGraph.homeUserCount == null) loadHomeUserCount()
        loading = true
        viewModelScope.launch {
            val result = runCatching { load() }.recoverCatching {
                if (AppGraph.reconnect()) load() else throw it
            }
            result.onSuccess {
                content = it
                error = null
            }.onFailure {
                if (content == null) error = AppGraph.str(R.string.home_error_connect, it.message.orEmpty())
            }
            loading = false
        }
    }

    private fun loadHomeUserCount() {
        val token = AppGraph.prefs.authToken ?: return
        viewModelScope.launch {
            runCatching { AppGraph.plexTv.homeUsers(token).size }.onSuccess {
                AppGraph.homeUserCount = it
            }
        }
    }

    private fun loadServerCount() {
        val token = AppGraph.plexToken ?: return
        viewModelScope.launch {
            runCatching { AppGraph.plexTv.servers(token).size }.onSuccess {
                AppGraph.serverCount = it
            }
        }
    }

    private suspend fun load(): HomeContent {
        val server = AppGraph.requireServer()
        val sections = viewModelScope.async { server.sections() }
        val continueWatching = viewModelScope.async { runCatching { server.continueWatching() }.getOrDefault(emptyList()) }
        val hubs = viewModelScope.async { server.homeHubs() }
        AppGraph.sections = sections.await()
        return HomeContent(continueWatching.await(), hubs.await())
    }
}

@Composable
fun HomeScreen(
    onOpenItem: (Metadata) -> Unit,
    actions: HeaderActions,
    vm: HomeViewModel = viewModel(),
) {
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    val content = vm.content
    when {
        content != null -> HomeContentView(content, vm, onOpenItem, actions)
        vm.error != null -> ErrorView(vm.error!!, onRetry = vm::refresh, onOpenSettings = actions.onOpenSettings)
        else -> CenteredMessage(stringResource(R.string.loading))
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun HomeContentView(
    content: HomeContent,
    vm: HomeViewModel,
    onOpenItem: (Metadata) -> Unit,
    actions: HeaderActions,
) {
    val hubs = AppGraph.libraryLayout.arrangeHubs(content.hubs, AppGraph.sections)
    val restore = remember { FocusRequester() }
    val initialKey = remember { vm.lastFocused }
    val firstKey = content.continueWatching.firstOrNull()?.let { "cw/${it.ratingKey}" }
        ?: hubs.firstOrNull()?.let { hub -> hub.metadata.firstOrNull()?.let { "${hub.hubIdentifier}/${it.ratingKey}" } }
    val targetKey = initialKey ?: firstKey

    fun rowModifier(prefix: String): (Metadata) -> Modifier = { item ->
        if ("$prefix/${item.ratingKey}" == targetKey) Modifier.focusRequester(restore) else Modifier
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "header") { LibraryBar(selectedKey = null, actions = actions) }
        if (content.continueWatching.isNotEmpty()) {
            item(key = "cw") {
                MediaRow(
                    title = stringResource(R.string.home_continue_watching),
                    items = content.continueWatching,
                    style = CardStyle.WIDE,
                    onClick = onOpenItem,
                    itemModifier = rowModifier("cw"),
                    onFocused = { vm.lastFocused = "cw/${it.ratingKey}" },
                )
            }
        }
        hubs.distinctBy { it.hubIdentifier }.forEach { hub ->
            item(key = "hub:${hub.hubIdentifier}") {
                val style = if (hub.type == "episode" || hub.type == "clip") CardStyle.WIDE else CardStyle.POSTER
                MediaRow(
                    title = hub.title,
                    items = hub.metadata,
                    style = style,
                    onClick = onOpenItem,
                    itemModifier = rowModifier(hub.hubIdentifier),
                    onFocused = { vm.lastFocused = "${hub.hubIdentifier}/${it.ratingKey}" },
                )
            }
        }
    }

    LaunchedEffect(targetKey) { if (targetKey != null) runCatching { restore.requestFocus() } }
}
