package dev.plexlite.tv.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ConnectionPicker(private val identity: PlexIdentity) {

    suspend fun pick(resource: Resource): SavedServer? {
        val token = resource.accessToken ?: return null
        val usable = resource.connections
            .filter { !it.local || resource.owned || resource.publicAddressMatches }
            .filter { !(resource.httpsRequired && it.protocol == "http") }
            .sortedWith(
                compareBy<Connection> { if (it.local) 0 else 1 }
                    .thenBy { if (it.protocol == "https") 0 else 1 }
                    .thenBy { if (it.ipv6) 1 else 0 },
            )
        val direct = usable.filter { !it.relay }
        val relay = usable.filter { it.relay }
        val winner = race(direct, resource.clientIdentifier, token) ?: race(relay, resource.clientIdentifier, token)
            ?: return null
        return SavedServer(
            id = resource.clientIdentifier,
            name = resource.name,
            uri = winner.uri.trimEnd('/'),
            token = token,
            relay = winner.relay,
        )
    }

    suspend fun isReachable(server: SavedServer): Boolean =
        probe(server.uri, server.id, server.token, timeoutSeconds = 6)

    private suspend fun race(candidates: List<Connection>, machineId: String, token: String): Connection? {
        if (candidates.isEmpty()) return null
        return coroutineScope {
            val answers = Channel<Int>(Channel.UNLIMITED)
            val probes = candidates.mapIndexed { rank, connection ->
                launch {
                    val timeout = if (connection.local) 5L else 10L
                    if (probe(connection.uri, machineId, token, timeout)) answers.send(rank)
                }
            }
            launch {
                probes.joinAll()
                answers.close()
            }
            var best = answers.receiveCatching().getOrNull()
            if (best != null && best > 0) {
                withTimeoutOrNull(1_500) {
                    for (rank in answers) {
                        if (rank < best!!) best = rank
                        if (best == 0) break
                    }
                }
            }
            probes.forEach { it.cancel() }
            best?.let { candidates[it] }
        }
    }

    private suspend fun probe(uri: String, machineId: String, token: String, timeoutSeconds: Long): Boolean {
        val client = Http.client.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val request = identity.apply(Request.Builder().url("${uri.trimEnd('/')}/identity"), token).build()
        return runCatching {
            Http.fetchJson<Envelope>(request, client).container.machineIdentifier == machineId
        }.getOrDefault(false)
    }
}
