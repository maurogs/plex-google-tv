package dev.plexlite.tv.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.HttpException
import dev.plexlite.tv.data.Resource
import dev.plexlite.tv.ui.theme.Accent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Loading : LoginState
    data class ShowPin(val code: String) : LoginState
    data class PickServer(val servers: List<Resource>) : LoginState
    data class Connecting(val name: String) : LoginState
    data class Error(val message: String) : LoginState
    data object Done : LoginState
}

class LoginViewModel : ViewModel() {
    var state by mutableStateOf<LoginState>(LoginState.Loading)
        private set

    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            val token = AppGraph.prefs.authToken ?: signIn() ?: return@launch
            loadServers(token)
        }
    }

    private suspend fun signIn(): String? {
        while (true) {
            state = LoginState.Loading
            val pin = runCatching { AppGraph.plexTv.createPin() }.getOrElse {
                state = LoginState.Error("No se pudo contactar con plex.tv: ${it.message}")
                return null
            }
            state = LoginState.ShowPin(pin.code)
            val deadline = System.currentTimeMillis() + (pin.expiresIn ?: 900).coerceIn(60, 1800) * 1000L
            var backoff = 2_000L
            while (System.currentTimeMillis() < deadline) {
                delay(backoff)
                val result = runCatching { AppGraph.plexTv.pollPin(pin.id) }
                val polled = result.getOrNull()
                val failure = result.exceptionOrNull()
                if (failure is HttpException && (failure.code == 404 || failure.code == 410)) break
                backoff = if (failure != null) (backoff * 2).coerceAtMost(16_000) else 2_000
                val token = polled?.authToken
                if (!token.isNullOrEmpty()) {
                    AppGraph.prefs.authToken = token
                    return token
                }
            }
        }
    }

    private suspend fun loadServers(token: String) {
        state = LoginState.Loading
        val servers = runCatching { AppGraph.plexTv.servers(token) }.getOrElse {
            if (it is HttpException && it.code == 401) {
                AppGraph.signOut()
                start()
            } else {
                state = LoginState.Error("No se pudo obtener la lista de servidores: ${it.message}")
            }
            return
        }
        when (servers.size) {
            0 -> state = LoginState.Error("Tu cuenta no tiene ningún servidor Plex disponible.")
            1 -> connect(servers.first())
            else -> state = LoginState.PickServer(servers)
        }
    }

    fun connect(resource: Resource) {
        job?.cancel()
        job = viewModelScope.launch {
            state = LoginState.Connecting(resource.name)
            val saved = AppGraph.picker.pick(resource)
            if (saved == null) {
                state = LoginState.Error("No se pudo conectar con ${resource.name}. ¿Está encendido y accesible desde fuera?")
                return@launch
            }
            AppGraph.useServer(saved)
            state = LoginState.Done
        }
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit, vm: LoginViewModel = viewModel()) {
    val state = vm.state
    LaunchedEffect(state) { if (state is LoginState.Done) onDone() }

    Box(Modifier.fillMaxSize().padding(64.dp), contentAlignment = Alignment.Center) {
        when (state) {
            LoginState.Loading, LoginState.Done -> Text("Cargando…", color = Color.White)
            is LoginState.Connecting -> Text("Conectando con ${state.name}…", color = Color.White)
            is LoginState.ShowPin -> PinContent(state.code)
            is LoginState.PickServer -> ServerList(state.servers, vm::connect)
            is LoginState.Error -> ErrorContent(state.message, vm::start)
        }
    }
}

@Composable
private fun PinContent(code: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PlexLite", style = MaterialTheme.typography.displaySmall, color = Accent, fontWeight = FontWeight.Bold)
        Text(
            "Desde el móvil o el ordenador, entra en",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Text("plex.tv/link", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text("e introduce este código:", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(
            code,
            fontSize = 72.sp,
            letterSpacing = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServerList(servers: List<Resource>, onPick: (Resource) -> Unit) {
    val focus = remember { FocusRequester() }
    Column(Modifier.width(560.dp)) {
        Text("Elige un servidor", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(servers, key = { _, it -> it.clientIdentifier }) { index, server ->
                ListItem(
                    selected = false,
                    onClick = { onPick(server) },
                    headlineContent = { Text(server.name) },
                    supportingContent = {
                        Text(
                            buildString {
                                append(if (server.owned) "Tuyo" else "Compartido")
                                if (!server.presence) append(" · desconectado")
                            },
                        )
                    },
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(message, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text("Reintentar") }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
