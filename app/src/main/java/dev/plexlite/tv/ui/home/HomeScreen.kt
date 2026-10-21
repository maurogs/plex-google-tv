package dev.plexlite.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import dev.plexlite.tv.data.Hub
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.ui.components.CardStyle
import dev.plexlite.tv.ui.components.CenteredMessage
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

    val feedFocus = FeedFocus()
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
    val continueWatching = stringResource(R.string.home_continue_watching)
    val rows = buildList {
        if (content.continueWatching.isNotEmpty()) add(FeedRow("cw", continueWatching, content.continueWatching, CardStyle.WIDE))
        AppGraph.libraryLayout.arrangeHubs(content.hubs, AppGraph.sections).forEach { add(it.toFeedRow()) }
    }
    val focusHomeChip = remember { AppGraph.focusHomeChip.also { AppGraph.focusHomeChip = false } }
    HubFeed(rows = rows, focus = vm.feedFocus, onOpenItem = onOpenItem, focusFirstCard = !focusHomeChip) {
        LibraryBar(selectedKey = null, actions = actions, focusSelected = focusHomeChip)
    }
}
