package dev.plexlite.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.DeviceProfile
import dev.plexlite.tv.data.Quality

@Composable
fun SettingsScreen(onChangeServer: () -> Unit, onSignOut: () -> Unit) {
    val prefs = AppGraph.prefs
    var quality by remember { mutableStateOf(prefs.quality) }
    var autoPlay by remember { mutableStateOf(prefs.autoPlayNext) }
    val focus = remember { FocusRequester() }
    val server = AppGraph.server?.saved

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 96.dp).width(720.dp),
        contentPadding = PaddingValues(vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Header("Calidad de reproducción") }
        items(Quality.entries) { option ->
            ListItem(
                selected = option == quality,
                onClick = {
                    quality = option
                    prefs.quality = option
                },
                headlineContent = { Text(option.label) },
                trailingContent = { RadioButton(selected = option == quality, onClick = null) },
                modifier = if (option == quality) Modifier.focusRequester(focus) else Modifier,
            )
        }
        item { Header("Reproducción") }
        item {
            ListItem(
                selected = false,
                onClick = {
                    autoPlay = !autoPlay
                    prefs.autoPlayNext = autoPlay
                },
                headlineContent = { Text("Reproducir el siguiente episodio automáticamente") },
                trailingContent = { Switch(checked = autoPlay, onCheckedChange = null) },
            )
        }
        item { Header("Servidor") }
        item {
            ListItem(
                selected = false,
                onClick = onChangeServer,
                headlineContent = { Text("Cambiar de servidor") },
                supportingContent = {
                    Text(
                        server?.let { "${it.name} · ${it.uri}${if (it.relay) " (relay, limitado a ~2 Mbps)" else ""}" }
                            ?: "Ninguno",
                    )
                },
            )
        }
        item {
            ListItem(
                selected = false,
                onClick = onSignOut,
                headlineContent = { Text("Cerrar sesión") },
            )
        }
        item { Header("Dispositivo") }
        item {
            ListItem(
                selected = false,
                onClick = {},
                headlineContent = { Text("Códecs de vídeo por hardware") },
                supportingContent = { Text(DeviceProfile.videoCodecs.joinToString(", ")) },
            )
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}
