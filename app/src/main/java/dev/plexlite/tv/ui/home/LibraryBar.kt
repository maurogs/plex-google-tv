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

@Composable
fun LibraryBar(selectedKey: String?, actions: HeaderActions, modifier: Modifier = Modifier) {
    val sections = AppGraph.libraryLayout.visible(AppGraph.sections)
    Row(modifier = modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        ServerMenuButton(actions = actions)
        Spacer(Modifier.width(24.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            item(key = "search") { SearchChip(onClick = actions.onSearch) }
            item(key = "home") {
                LibraryChip(stringResource(R.string.nav_home), selected = selectedKey == null, onClick = actions.onHome)
            }
            items(sections, key = { it.key }) { section ->
                LibraryChip(section.title, selected = section.key == selectedKey, onClick = { actions.onOpenSection(section) })
            }
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
private fun LibraryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.colors(
                containerColor = Accent,
                contentColor = Color.Black,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        ) { Text(title) }
    } else {
        OutlinedButton(onClick = onClick) { Text(title) }
    }
}
