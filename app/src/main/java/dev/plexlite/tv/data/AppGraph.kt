package dev.plexlite.tv.data

import android.content.Context
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

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val reconnectLock = Mutex()

    var server: PlexServer? = null
        private set

    fun init(context: Context) {
        prefs = Prefs(context)
        identity = PlexIdentity(prefs.clientId)
        plexTv = PlexTv(identity)
        picker = ConnectionPicker(identity)
        server = prefs.server?.let { PlexServer(it, identity) }
    }

    fun requireServer(): PlexServer = server ?: error("Sin servidor")

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

    fun signOut() {
        prefs.authToken = null
        prefs.server = null
        server = null
    }

    fun forgetServer() {
        prefs.server = null
        server = null
    }
}
