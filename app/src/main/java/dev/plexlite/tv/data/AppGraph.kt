package dev.plexlite.tv.data

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import dev.plexlite.tv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppGraph {
    lateinit var prefs: Prefs
        private set
    lateinit var identity: PlexIdentity
        private set
    lateinit var plexTv: PlexTv
        private set
    lateinit var picker: ConnectionPicker
        private set
    lateinit var strings: Context
        private set
    private lateinit var appContext: Context

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val reconnectLock = Mutex()

    var server: PlexServer? = null
        private set

    var serverCount by mutableStateOf<Int?>(null)
    var homeUserCount by mutableStateOf<Int?>(null)
    var sections by mutableStateOf<List<Directory>>(emptyList())
    var libraryTab by mutableStateOf(LibraryTab.RECOMMENDED)
    /** Set by Back from a library's bar so Home opens with focus on its "Home" chip. */
    var focusHomeChip = false
    var libraryLayout by mutableStateOf(LibraryLayout())
        private set

    val plexToken: String? get() = prefs.userToken ?: prefs.authToken

    fun init(context: Context) {
        appContext = context
        prefs = Prefs(context)
        strings = AppLocale.wrap(context, prefs.language)
        identity = PlexIdentity(prefs.clientId)
        plexTv = PlexTv(identity)
        picker = ConnectionPicker(identity)
        server = prefs.server?.let { PlexServer(it, identity) }
        reloadLibraryLayout()
    }

    private fun layoutKey(): String = "${prefs.currentUser?.uuid ?: "account"}|${prefs.server?.id}"

    private fun reloadLibraryLayout() {
        libraryLayout = prefs.libraryLayouts[layoutKey()] ?: LibraryLayout()
    }

    fun updateLibraryLayout(layout: LibraryLayout) {
        libraryLayout = layout
        prefs.libraryLayouts = prefs.libraryLayouts + (layoutKey() to layout)
    }

    fun requireServer(): PlexServer = server ?: error(str(R.string.no_server))

    fun useServer(saved: SavedServer) {
        if (prefs.server?.id != saved.id) sections = emptyList()
        prefs.server = saved
        server = PlexServer(saved, identity)
        reloadLibraryLayout()
        val user = prefs.currentUser ?: return
        val token = plexToken ?: return
        if (!user.isProtected) prefs.sessions = prefs.sessions + (user.uuid to UserSession(token, saved))
    }

    suspend fun activateUser(user: HomeUser, pin: String?) {
        val cached = prefs.sessions[user.uuid]
        if (!user.isProtected && cached != null) {
            prefs.userToken = cached.token
            prefs.currentUser = user
            useServer(cached.server)
            return
        }
        val accountToken = prefs.authToken ?: error(str(R.string.no_server))
        val token = if (user.uuid == prefs.accountUuid && !user.isProtected) {
            accountToken
        } else {
            plexTv.switchUser(accountToken, user.uuid, pin)
        }
        val servers = plexTv.servers(token)
        val target = servers.firstOrNull { it.clientIdentifier == prefs.server?.id } ?: servers.firstOrNull()
            ?: error(str(R.string.login_error_no_servers))
        val saved = picker.pick(target) ?: error(str(R.string.login_error_connect, target.name))
        serverCount = servers.size
        prefs.userToken = token
        prefs.currentUser = user
        useServer(saved)
    }

    suspend fun reconnect(): Boolean = reconnectLock.withLock {
        val current = prefs.server ?: return false
        if (picker.isReachable(current)) return true
        val token = plexToken ?: return false
        val resource = runCatching { plexTv.servers(token) }.getOrNull()
            ?.firstOrNull { it.clientIdentifier == current.id } ?: return false
        val saved = picker.pick(resource) ?: return false
        useServer(saved)
        true
    }

    fun str(@StringRes id: Int, vararg args: Any): String = strings.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int): String = strings.resources.getQuantityString(id, count, count)

    fun setLanguage(tag: String?) {
        prefs.language = tag
        strings = AppLocale.wrap(appContext, tag)
    }

    fun signOut() {
        prefs.authToken = null
        prefs.clearUsers()
        homeUserCount = null
        serverCount = null
        prefs.server = null
        server = null
    }

}
