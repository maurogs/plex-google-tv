package dev.plexlite.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Directory
import dev.plexlite.tv.ui.theme.Accent

data class HeaderActions(
    val onHome: () -> Unit,
    val onSearch: () -> Unit,
    val onOpenSection: (Directory) -> Unit,
    val onChangeServer: () -> Unit,
    val onChangeUser: () -> Unit,
    val onOrganizeLibraries: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onLanguageChanged: (String?) -> Unit,
)

/** [focusSelected] moves focus to the selected chip when the bar enters the composition. */
@Composable
fun LibraryBar(selectedKey: String?, actions: HeaderActions, modifier: Modifier = Modifier, focusSelected: Boolean = false) {
    val sections = AppGraph.libraryLayout.visible(AppGraph.sections)
    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    fun Modifier.selectedFocus(selected: Boolean) = if (selected) focusRequester(selectedFocus) else this
    Row(modifier = modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        ServerMenuButton(actions = actions)
        Spacer(Modifier.width(24.dp))
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            item(key = "search") { SearchChip(onClick = actions.onSearch) }
            item(key = "home") {
                LibraryChip(
                    stringResource(R.string.nav_home),
                    selected = selectedKey == null,
                    onClick = actions.onHome,
                    modifier = Modifier.selectedFocus(selectedKey == null),
                )
            }
            items(sections, key = { it.key }) { section ->
                LibraryChip(
                    section.title,
                    selected = section.key == selectedKey,
                    onClick = { actions.onOpenSection(section) },
                    modifier = Modifier.selectedFocus(section.key == selectedKey),
                )
            }
        }
    }
    if (focusSelected) {
        LaunchedEffect(Unit) {
            val index = sections.indexOfFirst { it.key == selectedKey }.let { if (it < 0) 1 else it + 2 }
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        }
    }
}

@Composable
private fun SearchChip(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = stringResource(R.string.search_title),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun LibraryChip(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.colors(
                containerColor = Accent,
                contentColor = Color.Black,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        ) { Text(title) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(title) }
    }
}
