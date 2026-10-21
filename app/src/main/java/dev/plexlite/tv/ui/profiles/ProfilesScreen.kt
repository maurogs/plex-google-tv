package dev.plexlite.tv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.plexlite.tv.R
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.HomeUser
import dev.plexlite.tv.data.HttpException
import dev.plexlite.tv.ui.components.CenteredMessage
import dev.plexlite.tv.ui.theme.Accent
import dev.plexlite.tv.ui.theme.SurfaceColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface ProfilesState {
    data object Loading : ProfilesState
    data class Users(val users: List<HomeUser>) : ProfilesState
    data class Pin(val user: HomeUser, val wrong: Boolean = false) : ProfilesState
    data class Switching(val user: HomeUser) : ProfilesState
    data class Error(val message: String) : ProfilesState
    data object Done : ProfilesState
}

class ProfilesViewModel(savedState: SavedStateHandle) : ViewModel() {
    private val auto: Boolean = savedState.get<Boolean>("auto") ?: false

    var state by mutableStateOf<ProfilesState>(ProfilesState.Loading)
        private set
    var countdown by mutableStateOf<Int?>(null)
        private set
    var rememberChoice by mutableStateOf(AppGraph.prefs.autoSignIn)
        private set
    var lastUuid by mutableStateOf(AppGraph.prefs.currentUser?.uuid)
        private set

    private var users: List<HomeUser> = emptyList()
    private var countdownJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = ProfilesState.Loading
            val token = AppGraph.prefs.authToken ?: run {
                state = ProfilesState.Done
                return@launch
            }
            if (AppGraph.prefs.accountUuid == null || AppGraph.prefs.currentUser == null) {
                runCatching { AppGraph.plexTv.account(token) }.onSuccess { account ->
                    AppGraph.prefs.accountUuid = account.uuid
                    if (AppGraph.prefs.currentUser == null) AppGraph.prefs.currentUser = account
                }
            }
            lastUuid = AppGraph.prefs.currentUser?.uuid
            val loaded = runCatching { AppGraph.plexTv.homeUsers(token) }.getOrDefault(emptyList())
            AppGraph.homeUserCount = loaded.size
            if (loaded.size <= 1) {
                state = ProfilesState.Done
                return@launch
            }
            users = loaded.sortedWith(compareByDescending<HomeUser> { it.uuid == lastUuid }.thenByDescending { it.admin })
            state = ProfilesState.Users(users)
            val last = users.firstOrNull { it.uuid == lastUuid }
            if (auto && last != null && !last.isProtected) startCountdown(last)
        }
    }

    private fun startCountdown(user: HomeUser) {
        countdownJob = viewModelScope.launch {
            for (seconds in 5 downTo 1) {
                countdown = seconds
                delay(1_000)
            }
            countdown = null
            select(user)
        }
    }

    fun toggleRemember() {
        rememberChoice = !rememberChoice
        AppGraph.prefs.autoSignIn = rememberChoice
    }

    fun cancelCountdown() {
        if (countdownJob == null) return
        countdownJob?.cancel()
        countdownJob = null
        countdown = null
    }

    fun select(user: HomeUser) {
        cancelCountdown()
        if (user.isProtected) {
            state = ProfilesState.Pin(user)
            return
        }
        if (user.uuid == AppGraph.prefs.currentUser?.uuid && AppGraph.server != null) {
            state = ProfilesState.Done
            return
        }
        activate(user, null)
    }

    fun submitPin(user: HomeUser, pin: String) = activate(user, pin)

    fun backToUsers() {
        state = ProfilesState.Users(users)
    }

    private fun activate(user: HomeUser, pin: String?) {
        viewModelScope.launch {
            state = ProfilesState.Switching(user)
            runCatching { AppGraph.activateUser(user, pin) }
                .onSuccess { state = ProfilesState.Done }
                .onFailure {
                    state = if (pin != null && it is HttpException && (it.code == 401 || it.code == 403)) {
                        ProfilesState.Pin(user, wrong = true)
                    } else {
                        ProfilesState.Error(AppGraph.str(R.string.profiles_error, it.message.orEmpty()))
                    }
                }
        }
    }
}

@Composable
fun ProfilesScreen(onDone: () -> Unit, vm: ProfilesViewModel = viewModel()) {
    val state = vm.state
    LaunchedEffect(state) { if (state is ProfilesState.Done) onDone() }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) vm.cancelCountdown()
                false
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ProfilesState.Loading, ProfilesState.Done -> CenteredMessage(stringResource(R.string.loading))
            is ProfilesState.Switching -> CenteredMessage(stringResource(R.string.profiles_switching, state.user.title))
            is ProfilesState.Users -> UserList(state.users, vm)
            is ProfilesState.Pin -> PinEntry(state.user, state.wrong, onSubmit = { vm.submitPin(state.user, it) }, onBack = vm::backToUsers)
            is ProfilesState.Error -> ErrorContent(state.message, onRetry = vm::load)
        }
    }
}

@Composable
private fun UserList(users: List<HomeUser>, vm: ProfilesViewModel) {
    val focus = remember { FocusRequester() }
    val initial = users.indexOfFirst { it.uuid == vm.lastUuid }.coerceAtLeast(0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.profiles_title),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(48.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 16.dp),
        ) {
            itemsIndexed(users, key = { _, user -> user.uuid }) { index, user ->
                ProfileCard(
                    user = user,
                    lastUsed = user.uuid == vm.lastUuid,
                    onClick = { vm.select(user) },
                    modifier = if (index == initial) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        RememberToggle(checked = vm.rememberChoice, onToggle = vm::toggleRemember)
        Spacer(Modifier.height(20.dp))
        val name = users.firstOrNull { it.uuid == vm.lastUuid }?.title
        Text(
            text = vm.countdown?.let { seconds -> name?.let { stringResource(R.string.profiles_auto, it, seconds) } } ?: " ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun RememberToggle(checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Accent.copy(alpha = 0.18f),
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.profiles_remember), style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
    }
}

@Composable
fun UserAvatar(user: HomeUser, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            user.title.take(1).uppercase(),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
        )
        AsyncImage(
            model = user.thumb,
            contentDescription = user.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ProfileCard(user: HomeUser, lastUsed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            border = ClickableSurfaceDefaults.border(
                border = if (lastUsed) Border(BorderStroke(2.dp, Accent.copy(alpha = 0.5f)), shape = CircleShape) else Border.None,
                focusedBorder = Border(BorderStroke(4.dp, Accent), shape = CircleShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        ) {
            UserAvatar(user, 120.dp, Modifier.padding(6.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            (if (user.isProtected) "🔒 " else "") + user.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            if (lastUsed) stringResource(R.string.profiles_last_used) else " ",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
        )
    }
}

@Composable
private fun PinEntry(user: HomeUser, wrong: Boolean, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember(user, wrong) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    fun type(digit: Char) {
        if (pin.length >= 4) return
        pin += digit
        if (pin.length == 4) onSubmit(pin)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val digit = DIGIT_KEYS[event.key]
            when {
                digit != null -> {
                    type(digit)
                    true
                }
                event.key == Key.Delete || event.key == Key.Backspace -> {
                    pin = pin.dropLast(1)
                    true
                }
                else -> false
            }
        },
    ) {
        UserAvatar(user, 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.profiles_enter_pin, user.title), style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (index < pin.length) Accent else SurfaceColor),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (wrong && pin.isEmpty()) stringResource(R.string.profiles_wrong_pin) else " ",
            color = Color(0xFFFF6B6B),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(12.dp))
        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('⌫', '0', ' '))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        if (key == ' ') {
                            Spacer(Modifier.width(72.dp))
                        } else {
                            Button(
                                onClick = { if (key == '⌫') pin = pin.dropLast(1) else type(key) },
                                modifier = Modifier
                                    .width(72.dp)
                                    .then(if (key == '1') Modifier.focusRequester(focus) else Modifier),
                            ) { Text(key.toString(), fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(user, wrong) { runCatching { focus.requestFocus() } }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(message, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.retry)) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

private val DIGIT_KEYS = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9',
)
