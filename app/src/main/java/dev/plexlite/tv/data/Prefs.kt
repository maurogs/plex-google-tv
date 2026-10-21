package dev.plexlite.tv.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import dev.plexlite.tv.R
import java.util.UUID

data class SavedServer(
    val id: String,
    val name: String,
    val uri: String,
    val token: String,
    val relay: Boolean,
)

enum class Quality(val maxKbps: Int?, val resolution: String?) {
    ORIGINAL(null, null),
    MBPS_40(40_000, "3840x2160"),
    MBPS_20(20_000, "1920x1080"),
    MBPS_12(12_000, "1920x1080"),
    MBPS_8(8_000, "1920x1080"),
    MBPS_4(4_000, "1280x720"),
    MBPS_2(2_000, "1280x720"),
    ;

    val label: String
        get() {
            val kbps = maxKbps ?: return AppGraph.str(R.string.quality_original)
            val height = resolution?.substringAfter('x')
            return "${kbps / 1000} Mbps · ${if (height == "2160") "4K" else "${height}p"}"
        }
}

enum class SubtitleSize(@StringRes private val labelRes: Int, val scale: Float) {
    SMALL(R.string.subtitle_size_small, 0.75f),
    NORMAL(R.string.subtitle_size_normal, 1f),
    LARGE(R.string.subtitle_size_large, 1.3f),
    XLARGE(R.string.subtitle_size_xlarge, 1.6f),
    ;

    val label: String get() = AppGraph.str(labelRes)
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("plexlite", Context.MODE_PRIVATE)

    val clientId: String = prefs.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString(KEY_CLIENT_ID, it) }
    }

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_AUTH_TOKEN, value) }

    var server: SavedServer?
        get() {
            val id = prefs.getString(KEY_SERVER_ID, null) ?: return null
            return SavedServer(
                id = id,
                name = prefs.getString(KEY_SERVER_NAME, null).orEmpty(),
                uri = prefs.getString(KEY_SERVER_URI, null) ?: return null,
                token = prefs.getString(KEY_SERVER_TOKEN, null) ?: return null,
                relay = prefs.getBoolean(KEY_SERVER_RELAY, false),
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_SERVER_ID)
                remove(KEY_SERVER_NAME)
                remove(KEY_SERVER_URI)
                remove(KEY_SERVER_TOKEN)
                remove(KEY_SERVER_RELAY)
            } else {
                putString(KEY_SERVER_ID, value.id)
                putString(KEY_SERVER_NAME, value.name)
                putString(KEY_SERVER_URI, value.uri)
                putString(KEY_SERVER_TOKEN, value.token)
                putBoolean(KEY_SERVER_RELAY, value.relay)
            }
        }

    var quality: Quality
        get() = prefs.getString(KEY_QUALITY, null)?.let { name -> Quality.entries.firstOrNull { it.name == name } } ?: Quality.ORIGINAL
        set(value) = prefs.edit { putString(KEY_QUALITY, value.name) }

    var autoPlayNext: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_NEXT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTOPLAY_NEXT, value) }

    var language: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    var subtitleSize: SubtitleSize
        get() = prefs.getString(KEY_SUBTITLE_SIZE, null)?.let { name -> SubtitleSize.entries.firstOrNull { it.name == name } } ?: SubtitleSize.NORMAL
        set(value) = prefs.edit { putString(KEY_SUBTITLE_SIZE, value.name) }

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_SERVER_URI = "server_uri"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_SERVER_RELAY = "server_relay"
        const val KEY_QUALITY = "quality"
        const val KEY_AUTOPLAY_NEXT = "autoplay_next"
        const val KEY_SUBTITLE_SIZE = "subtitle_size"
        const val KEY_LANGUAGE = "language"
    }
}
