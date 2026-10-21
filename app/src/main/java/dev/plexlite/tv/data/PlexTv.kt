package dev.plexlite.tv.data

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PlexTv(private val identity: PlexIdentity) {

    suspend fun createPin(): Pin = Http.fetchJson(
        request("https://plex.tv/api/v2/pins?strong=false", token = null)
            .post(ByteArray(0).toRequestBody())
            .build(),
    )

    suspend fun pollPin(id: Long): Pin = Http.fetchJson(request("https://plex.tv/api/v2/pins/$id", token = null).build())

    suspend fun servers(token: String): List<Resource> {
        val resources: List<Resource> = Http.fetchJson(
            request("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1", token).build(),
        )
        return resources
            .filter { it.isServer && it.accessToken != null }
            .sortedWith(compareByDescending<Resource> { it.owned }.thenByDescending { it.presence }.thenBy { it.name })
    }

    suspend fun account(token: String): HomeUser = Http.fetchJson(request("https://plex.tv/api/v2/user", token).build())

    suspend fun homeUsers(token: String): List<HomeUser> =
        Http.fetchJson<HomeUsers>(request("https://plex.tv/api/v2/home/users", token).build()).users

    suspend fun switchUser(token: String, uuid: String, pin: String?): String {
        val url = "https://plex.tv/api/v2/home/users/$uuid/switch" + (pin?.let { "?pin=$it" } ?: "")
        val switched: SwitchedUser = Http.fetchJson(request(url, token).post(ByteArray(0).toRequestBody()).build())
        return switched.authToken ?: switched.authenticationToken ?: throw HttpException(401, "No token")
    }

    private fun request(url: String, token: String?): Request.Builder =
        identity.apply(Request.Builder().url(url), token)
}
