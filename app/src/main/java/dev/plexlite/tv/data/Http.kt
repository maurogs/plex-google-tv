package dev.plexlite.tv.data

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, message: String) : IOException(message)

object Http {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetch(request: Request, client: OkHttpClient = this.client): String =
        client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code} en ${request.url.encodedPath}")
            }
            response.body.string()
        }

    suspend inline fun <reified T> fetchJson(request: Request, client: OkHttpClient = this.client): T =
        json.decodeFromString(fetch(request, client))
}
