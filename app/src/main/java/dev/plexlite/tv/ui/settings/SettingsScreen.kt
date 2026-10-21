package dev.plexlite.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.plexlite.tv.BuildConfig
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.AppLocale
import dev.plexlite.tv.data.DeviceProfile
import dev.plexlite.tv.data.Quality
import dev.plexlite.tv.ui.components.MenuPopup
import dev.plexlite.tv.ui.components.PopupRow
import dev.plexlite.tv.ui.components.rememberPopupState
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.SurfaceColor

@Composable
fun SettingsScreen(
    onChangeServer: () -> Unit,
    onSignOut: () -> Unit,
    onOrganizeLibraries: () -> Unit,
    onLanguageChanged: (String?) -> Unit,
) {
    val prefs = AppGraph.prefs
    var quality by remember { mutableStateOf(prefs.quality) }
    var autoPlay by remember { mutableStateOf(prefs.autoPlayNext) }
    var autoSignIn by remember { mutableStateOf(prefs.autoSignIn) }
    val currentUser = prefs.currentUser
    val server = AppGraph.server?.saved
    val firstFocus = remember { FocusRequester() }
    val allSections = AppGraph.sections
    val visibleSections = AppGraph.libraryLayout.visible(allSections)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.settings),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            item {
                SettingsGroup(stringResource(R.string.settings_playback)) {
                    DropdownSetting(
                        title = stringResource(R.string.settings_quality),
                        value = quality.label,
                        rows = Quality.entries.map { option ->
                            PopupRow(option.label, checked = option == quality) {
                                quality = option
                                prefs.quality = option
                            }
                        },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    ListItem(
                        selected = false,
                        onClick = {
                            autoPlay = !autoPlay
                            prefs.autoPlayNext = autoPlay
                        },
                        headlineContent = { Text(stringResource(R.string.settings_autoplay)) },
                        trailingContent = { Switch(checked = autoPlay, onCheckedChange = null) },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_libraries)) {
                    ListItem(
                        selected = false,
                        onClick = onOrganizeLibraries,
                        headlineContent = { Text(stringResource(R.string.settings_organize_libraries)) },
                        supportingContent = if (allSections.isNotEmpty()) {
                            { Text(stringResource(R.string.settings_libraries_visible, visibleSections.size, allSections.size)) }
                        } else {
                            null
                        },
                        trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
                    )
                }
            }
            if (currentUser != null && (AppGraph.homeUserCount ?: 0) > 1) {
                item {
                    SettingsGroup(stringResource(R.string.settings_users)) {
                        ListItem(
                            selected = false,
                            onClick = {
                                autoSignIn = !autoSignIn
                                prefs.autoSignIn = autoSignIn
                            },
                            headlineContent = { Text(stringResource(R.string.settings_auto_user)) },
                            supportingContent = {
                                Text(
                                    if (autoSignIn) {
                                        stringResource(R.string.settings_auto_user_on, currentUser.title)
                                    } else {
                                        stringResource(R.string.settings_auto_user_off)
                                    },
                                )
                            },
                            trailingContent = { Switch(checked = autoSignIn, onCheckedChange = null) },
                        )
                    }
                }
            }
            item {
                val currentTag = prefs.language
                val systemLabel = stringResource(R.string.menu_system_language)
                SettingsGroup(stringResource(R.string.player_language)) {
                    DropdownSetting(
                        title = stringResource(R.string.menu_app_language),
                        value = AppLocale.supported.firstOrNull { it.first == currentTag }?.second ?: systemLabel,
                        rows = listOf(PopupRow(systemLabel, checked = currentTag == null) {
                            if (currentTag != null) onLanguageChanged(null)
                        }) + AppLocale.supported.map { (tag, name) ->
                            PopupRow(name, checked = tag == currentTag) { if (tag != currentTag) onLanguageChanged(tag) }
                        },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_server)) {
                    val relay = stringResource(R.string.settings_relay)
                    ListItem(
                        selected = false,
                        enabled = (AppGraph.serverCount ?: 0) > 1,
                        onClick = onChangeServer,
                        headlineContent = { Text(stringResource(R.string.menu_change_server)) },
                        supportingContent = {
                            Text(
                                server?.let { "${it.name} · ${it.uri}${if (it.relay) " ($relay)" else ""}" }
                                    ?: stringResource(R.string.none),
                            )
                        },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_device)) {
                    ListItem(
                        selected = false,
                        onClick = {},
                        headlineContent = { Text(stringResource(R.string.settings_hw_codecs)) },
                        supportingContent = { Text(DeviceProfile.videoCodecs.joinToString(", ")) },
                    )
                    ListItem(
                        selected = false,
                        onClick = {},
                        headlineContent = { Text(stringResource(R.string.app_name)) },
                        supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                    )
                }
            }
            item {
                ListItem(
                    selected = false,
                    onClick = onSignOut,
                    headlineContent = {
                        Text(
                            stringResource(R.string.settings_sign_out),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Danger.copy(alpha = 0.15f),
                        contentColor = Danger,
                        focusedContainerColor = Danger,
                        focusedContentColor = Color.White,
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

private val Danger = Color(0xFFE5484D)

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(SurfaceColor, RoundedCornerShape(16.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun DropdownSetting(title: String, value: String, rows: List<PopupRow>, modifier: Modifier = Modifier) {
    val popup = rememberPopupState()
    Box {
        ListItem(
            selected = false,
            onClick = popup::show,
            headlineContent = { Text(title) },
            supportingContent = { Text(value) },
            trailingContent = { Text("▾", style = MaterialTheme.typography.titleLarge) },
            modifier = modifier.focusRequester(popup.anchorFocus),
        )
        if (popup.open) {
            MenuPopup(
                rows = rows.map { row ->
                    row.copy(onClick = {
                        popup.dismiss()
                        row.onClick()
                    })
                },
                onDismiss = popup::dismiss,
                offsetY = 72.dp,
                alignment = Alignment.TopEnd,
            )
        }
    }
    popup.RestoreFocus()
}
