package dev.plexlite.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.LibraryLayout
import dev.plexlite.tv.ui.components.CenteredMessage
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.SurfaceColor

private const val HEADER_ITEMS = 2
private val CENTER_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

@Composable
fun LibrariesScreen() {
    LaunchedEffect(Unit) {
        if (AppGraph.sections.isEmpty()) {
            runCatching { AppGraph.requireServer().sections() }.onSuccess { AppGraph.sections = it }
        }
    }
    val sections = AppGraph.sections
    if (sections.isEmpty()) {
        CenteredMessage(stringResource(R.string.loading))
        return
    }

    var order by remember(sections) { mutableStateOf(AppGraph.libraryLayout.arrange(sections).map { it.key }) }
    var hidden by remember { mutableStateOf(AppGraph.libraryLayout.hidden) }
    var moving by remember { mutableStateOf<String?>(null) }
    var orderBeforeMove by remember { mutableStateOf(order) }
    var swallowCenterUp by remember { mutableStateOf(false) }
    var focusKey by remember { mutableStateOf<String?>(null) }
    val rowFocus = remember(sections) { sections.associate { it.key to FocusRequester() } }
    val titles = sections.associate { it.key to it.title }
    val listState = rememberLazyListState()

    fun save() = AppGraph.updateLibraryLayout(LibraryLayout(order = order, hidden = hidden))

    fun shift(delta: Int) {
        val key = moving ?: return
        val from = order.indexOf(key)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from != to) order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    BackHandler(enabled = moving != null) {
        order = orderBeforeMove
        moving = null
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (swallowCenterUp && event.type == KeyEventType.KeyUp && event.key in CENTER_KEYS) {
                        swallowCenterUp = false
                        return@onPreviewKeyEvent true
                    }
                    if (moving == null) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) shift(if (event.key == Key.DirectionUp) -1 else 1)
                            true
                        }
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> true
                        event.key in CENTER_KEYS -> {
                            if (event.type == KeyEventType.KeyDown) {
                                focusKey = moving
                                moving = null
                                swallowCenterUp = true
                                save()
                            }
                            true
                        }
                        else -> false
                    }
                },
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_organize_libraries),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            item {
                Text(
                    stringResource(if (moving != null) R.string.libraries_moving_hint else R.string.libraries_hint),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (moving != null) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
            }
            items(order, key = { it }) { key ->
                val isHidden = key in hidden
                val isMoving = key == moving
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VisibilityButton(
                        hidden = isHidden,
                        enabled = moving == null,
                        onClick = {
                            hidden = if (isHidden) hidden - key else hidden + key
                            save()
                        },
                    )
                    ListItem(
                        selected = isMoving,
                        onClick = {
                            if (moving == null) {
                                orderBeforeMove = order
                                moving = key
                            }
                        },
                        leadingContent = if (isMoving) {
                            { Text("↕", style = MaterialTheme.typography.titleMedium) }
                        } else {
                            null
                        },
                        headlineContent = { Text(titles[key].orEmpty()) },
                        supportingContent = if (isHidden) {
                            { Text(stringResource(R.string.libraries_hidden)) }
                        } else {
                            null
                        },
                        trailingContent = if (isMoving) null else {
                            { Text("☰", style = MaterialTheme.typography.titleLarge) }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = SurfaceColor,
                            contentColor = if (isHidden) Color.White.copy(alpha = 0.45f) else Color.White,
                            selectedContainerColor = Accent.copy(alpha = 0.3f),
                            selectedContentColor = Color.White,
                        ),
                        scale = ListItemDefaults.scale(focusedScale = 1.02f),
                        border = ListItemDefaults.border(
                            focusedSelectedBorder = Border(BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(rowFocus.getValue(key)),
                    )
                }
            }
        }
    }

    LaunchedEffect(order, moving) {
        val target = moving ?: focusKey ?: return@LaunchedEffect
        val index = order.indexOf(target)
        val visible = listState.layoutInfo.visibleItemsInfo
        val listIndex = index + HEADER_ITEMS
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (listIndex <= first || listIndex >= last) {
            listState.animateScrollToItem((listIndex - 2).coerceAtLeast(0))
        }
        runCatching { rowFocus.getValue(target).requestFocus() }
    }
    LaunchedEffect(Unit) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { rowFocus.getValue(order.first()).requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

@Composable
private fun VisibilityButton(hidden: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceColor,
            contentColor = if (hidden) Color.White.copy(alpha = 0.6f) else Color.White,
            focusedContainerColor = Accent,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Icon(
            painter = painterResource(if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
            contentDescription = stringResource(if (hidden) R.string.libraries_show else R.string.libraries_hide),
            modifier = Modifier
                .padding(14.dp)
                .size(26.dp),
        )
    }
}
