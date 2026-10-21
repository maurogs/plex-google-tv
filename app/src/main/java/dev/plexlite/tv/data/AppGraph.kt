package dev.plexlite.tv.data

import android.content.Context
import androidx.annotation.PluralsRes
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

    var serverCount: Int? = null

    fun init(context: Context) {
        appContext = context
        prefs = Prefs(context)
        strings = AppLocale.wrap(context, prefs.language)
        identity = PlexIdentity(prefs.clientId)
        plexTv = PlexTv(identity)
        picker = ConnectionPicker(identity)
        server = prefs.server?.let { PlexServer(it, identity) }
    }

    fun requireServer(): PlexServer = server ?: error(str(R.string.no_server))

    fun useServer(saved: SavedServer) {
        prefs.server = saved
        server = PlexServer(saved, identity)
    }

    suspend fun reconnect(): Boolean = reconnectLock.withLock {
        val current = prefs.server ?: return false
        if (picker.isReachable(current)) return true
        val token = prefs.authToken ?: return false
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
        prefs.server = null
        server = null
    }

}
