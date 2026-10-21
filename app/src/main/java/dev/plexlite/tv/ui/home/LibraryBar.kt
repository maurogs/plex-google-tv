package dev.plexlite.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import dev.plexlite.tv.ui.components.MinimalBringIntoView
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryBar(
    selectedKey: String?,
    actions: HeaderActions,
    modifier: Modifier = Modifier,
    focusSelected: Boolean = false,
) {
    val sections = AppGraph.libraryLayout.visible(AppGraph.sections)
    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    val edge = 8.dp
    val fade = 56.dp
    // The focused chip is kept out of the faded edges.
    val scrollSpec = with(LocalDensity.current) { remember { MinimalBringIntoView(fade.toPx()) } }
    fun Modifier.selectedFocus(selected: Boolean) = if (selected) focusRequester(selectedFocus) else this
    Row(modifier = modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        ServerMenuButton(actions = actions)
        Spacer(Modifier.width(24.dp))
        SearchChip(onClick = actions.onSearch)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(width = 1.dp, height = 32.dp).background(Color.White.copy(alpha = 0.25f)))
        Spacer(Modifier.width(16.dp - edge))
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // Vertical room so the focused (scaled-up) chip and its press effect aren't clipped by the list.
                contentPadding = PaddingValues(horizontal = edge, vertical = 6.dp),
                modifier = Modifier.weight(1f).fadingEdges(listState, fade),
            ) {
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
    }
    // Keep the current library fully visible, e.g. after coming back to a bar that was scrolled to the right.
    LaunchedEffect(selectedKey) {
        val index = sections.indexOfFirst { it.key == selectedKey } + 1
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val viewport = listState.layoutInfo.viewportEndOffset
        if (item == null || item.offset < 0 || item.offset + item.size > viewport) listState.scrollToItem(index)
        if (focusSelected) {
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        }
    }
}

/** Fades out the start/end edge of a horizontal list when there is more content to scroll to on that side. */
private fun Modifier.fadingEdges(state: LazyListState, width: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = width.toPx().coerceAtMost(size.width / 2)
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fadePx),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fadePx, endX = size.width),
                blendMode = BlendMode.DstIn,
            )
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
