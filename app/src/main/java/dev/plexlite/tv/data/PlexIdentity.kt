package dev.plexlite.tv.data

import android.os.Build
import dev.plexlite.tv.BuildConfig
import okhttp3.Request
import java.util.Locale

class PlexIdentity(val clientId: String) {
    val product = "PlexLite"

    val headers: Map<String, String> = mapOf(
        "X-Plex-Product" to product,
        "X-Plex-Version" to BuildConfig.VERSION_NAME,
        "X-Plex-Client-Identifier" to clientId,
        "X-Plex-Platform" to "Android",
        "X-Plex-Platform-Version" to Build.VERSION.RELEASE,
        "X-Plex-Device" to Build.MODEL,
        "X-Plex-Device-Name" to "$product (${Build.MODEL})",
        "X-Plex-Device-Vendor" to Build.MANUFACTURER,
        "X-Plex-Model" to Build.MODEL,
        "X-Plex-Provides" to "player",
        "X-Plex-Language" to Locale.getDefault().toLanguageTag(),
    )

    val queryParams: List<Pair<String, String>> = listOf(
        "X-Plex-Product" to product,
        "X-Plex-Version" to BuildConfig.VERSION_NAME,
        "X-Plex-Client-Identifier" to clientId,
        "X-Plex-Platform" to "Android",
        "X-Plex-Device" to Build.MODEL,
    )

    fun apply(builder: Request.Builder, token: String?): Request.Builder {
        headers.forEach { (name, value) -> builder.header(name, value.filter { it.code in 32..126 }) }
        builder.header("Accept", "application/json")
        if (token != null) builder.header("X-Plex-Token", token)
        return builder
    }
}
