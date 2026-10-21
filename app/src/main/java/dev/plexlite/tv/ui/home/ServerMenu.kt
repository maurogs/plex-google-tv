package dev.plexlite.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.AppLocale
import dev.plexlite.tv.ui.profiles.UserAvatar
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.SurfaceColor

private enum class MenuPage { MAIN, LANGUAGE }

private data class MenuRow(val label: String, val value: String? = null, val checked: Boolean = false, val onClick: () -> Unit)

@Composable
fun ServerMenuButton(serverName: String, actions: HeaderActions) {
    val canChangeServer = (AppGraph.serverCount ?: 0) > 1
    val canChangeUser = (AppGraph.homeUserCount ?: 0) > 1
    var expanded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(MenuPage.MAIN) }
    var opened by remember { mutableStateOf(false) }
    val buttonFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)

    Box {
        Surface(
            onClick = {
                page = MenuPage.MAIN
                expanded = true
                opened = true
            },
            modifier = Modifier.focusRequester(buttonFocus),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Accent.copy(alpha = 0.18f),
                pressedContainerColor = Accent.copy(alpha = 0.28f),
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val user = AppGraph.prefs.currentUser
                if (user != null && canChangeUser) {
                    UserAvatar(user, 36.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(serverName, style = MaterialTheme.typography.headlineSmall, color = Accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("▾", style = MaterialTheme.typography.titleLarge, color = Accent)
            }
        }

        LaunchedEffect(expanded) {
            if (!expanded && opened) runCatching { buttonFocus.requestFocus() }
        }

        if (expanded) {
            val currentTag = AppGraph.prefs.language
            val systemLabel = stringResource(R.string.menu_system_language)
            val currentLanguage = AppLocale.supported.firstOrNull { it.first == currentTag }?.second ?: systemLabel
            val rows = when (page) {
                MenuPage.MAIN -> listOfNotNull(
                    MenuRow(stringResource(R.string.menu_change_user), AppGraph.prefs.currentUser?.title) {
                        expanded = false
                        actions.onChangeUser()
                    }.takeIf { canChangeUser },
                    MenuRow(stringResource(R.string.menu_change_server)) {
                        expanded = false
                        actions.onChangeServer()
                    }.takeIf { canChangeServer },
                    MenuRow(stringResource(R.string.menu_app_language), currentLanguage) { page = MenuPage.LANGUAGE },
                    MenuRow(stringResource(R.string.settings_organize_libraries)) {
                        expanded = false
                        actions.onOrganizeLibraries()
                    },
                    MenuRow(stringResource(R.string.settings)) {
                        expanded = false
                        actions.onOpenSettings()
                    },
                )
                MenuPage.LANGUAGE -> listOf(MenuRow(systemLabel, checked = currentTag == null) {
                    expanded = false
                    if (currentTag != null) actions.onLanguageChanged(null)
                }) + AppLocale.supported.map { (tag, name) ->
                    MenuRow(name, checked = tag == currentTag) {
                        expanded = false
                        if (tag != currentTag) actions.onLanguageChanged(tag)
                    }
                }
            }
            val firstFocus = remember(page) { FocusRequester() }
            val focusIndex = rows.indexOfFirst { it.checked }.coerceAtLeast(0)

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(LocalDensity.current) { 60.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true, dismissOnBackPress = false),
            ) {
                Column(
                    Modifier
                        .width(420.dp)
                        .heightIn(max = 400.dp)
                        .background(SurfaceColor, RoundedCornerShape(16.dp))
                        .padding(vertical = 10.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.key != Key.Back) return@onPreviewKeyEvent false
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
                                if (page == MenuPage.LANGUAGE) page = MenuPage.MAIN else expanded = false
                            }
                            true
                        },
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(rows, key = { _, row -> "${page.name}:${row.label}" }) { index, row ->
                            ListItem(
                                selected = false,
                                onClick = row.onClick,
                                headlineContent = { Text(row.label) },
                                supportingContent = row.value?.let { value -> { Text(value) } },
                                trailingContent = if (row.checked) {
                                    { Text("✓", color = Accent, fontWeight = FontWeight.Bold) }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .then(if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier),
                            )
                        }
                    }
                }
                LaunchedEffect(page) { runCatching { firstFocus.requestFocus() } }
            }
        }
    }
}
