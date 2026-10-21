package dev.plexlite.tv.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.plexlite.tv.data.DeviceProfile
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.data.Part
import dev.plexlite.tv.data.PlexServer
import dev.plexlite.tv.data.Quality
import dev.plexlite.tv.data.Stream

data class PlaybackPlan(
    val mediaItem: MediaItem,
    val transcode: Boolean,
    val part: Part,
)

class StreamResolver(private val server: PlexServer) {

    suspend fun resolve(
        item: Metadata,
        mediaIndex: Int,
        sessionId: String,
        quality: Quality,
        forceTranscode: Boolean,
        audioStreamId: Long? = null,
    ): PlaybackPlan {
        val part = item.media.getOrNull(mediaIndex)?.parts?.firstOrNull() ?: error("Este elemento no tiene ningún archivo reproducible")
        val effectiveQuality = if (server.saved.relay && quality == Quality.ORIGINAL) Quality.MBPS_2 else quality
        val audio = part.streams.firstOrNull { it.streamType == Stream.AUDIO && it.id == audioStreamId }
            ?: part.streams.firstOrNull { it.streamType == Stream.AUDIO && it.selected }
        val subtitle = part.streams.firstOrNull { it.streamType == Stream.SUBTITLE && it.selected }

        val common = buildList {
            add("path" to "/library/metadata/${item.ratingKey}")
            add("mediaIndex" to mediaIndex.toString())
            add("partIndex" to "0")
            add("session" to sessionId)
            add("X-Plex-Session-Identifier" to sessionId)
            add("X-Plex-Client-Profile-Name" to "Generic")
            add("X-Plex-Client-Profile-Extra" to DeviceProfile.profileExtra)
            add("mediaBufferSize" to "102400")
            audio?.let { add("audioStreamID" to it.id.toString()) }
            effectiveQuality.maxKbps?.let {
                add("maxVideoBitrate" to it.toString())
                add("videoBitrate" to it.toString())
                add("videoQuality" to "100")
            }
            effectiveQuality.resolution?.let { add("videoResolution" to it) }
        }

        if (!forceTranscode) {
            val directParams = common + listOf(
                "protocol" to "http",
                "hasMDE" to "1",
                "directPlay" to "1",
                "directStream" to "1",
                "directStreamAudio" to "1",
                "subtitles" to "none",
                "subtitleStreamID" to (subtitle?.id ?: 0).toString(),
            )
            val decision = runCatching { server.decision(directParams, sessionId) }
            val partDecision = decision.getOrNull()?.metadata?.firstOrNull()?.media?.firstOrNull()?.parts?.firstOrNull()?.decision
            val container = decision.getOrNull()
            Log.i(TAG, "MDE decision rk=${item.ratingKey} part=$partDecision general=${container?.generalDecisionCode} ${container?.generalDecisionText} transcode=${container?.transcodeDecisionCode} ${container?.transcodeDecisionText} error=${decision.exceptionOrNull()?.message}")
            if (partDecision == "directplay" || (decision.isFailure && effectiveQuality == Quality.ORIGINAL)) {
                val mediaItem = MediaItem.Builder()
                    .setUri(server.partUrl(part.key, sessionId))
                    .setMediaId(item.ratingKey)
                    .setSubtitleConfigurations(sidecarSubtitles(part))
                    .build()
                return PlaybackPlan(mediaItem, transcode = false, part = part)
            }
        }

        val transcodeParams = common + buildList {
            add("protocol" to "hls")
            add("directPlay" to "0")
            add("directStream" to "1")
            add("directStreamAudio" to "1")
            add("fastSeek" to "1")
            if (subtitle != null) {
                add("subtitles" to "burn")
                add("subtitleStreamID" to subtitle.id.toString())
                add("subtitleSize" to "100")
            } else {
                add("subtitles" to "none")
                add("subtitleStreamID" to "0")
            }
        }
        val transcodeDecision = server.decision(transcodeParams, sessionId)
        Log.i(TAG, "Transcode decision rk=${item.ratingKey} general=${transcodeDecision.generalDecisionCode} ${transcodeDecision.generalDecisionText} transcode=${transcodeDecision.transcodeDecisionCode} ${transcodeDecision.transcodeDecisionText}")
        val mediaItem = MediaItem.Builder()
            .setUri(server.transcodeUrl(transcodeParams))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaId(item.ratingKey)
            .build()
        return PlaybackPlan(mediaItem, transcode = true, part = part)
    }

    private fun sidecarSubtitles(part: Part): List<MediaItem.SubtitleConfiguration> =
        part.streams
            .filter { it.streamType == Stream.SUBTITLE && it.key != null }
            .mapNotNull { stream ->
                val mime = when (stream.codec?.lowercase()) {
                    "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                    "ttml" -> MimeTypes.APPLICATION_TTML
                    else -> null
                } ?: return@mapNotNull null
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(server.subtitleUrl(stream.key!!)))
                    .setId(sidecarId(stream))
                    .setMimeType(mime)
                    .setLanguage(stream.languageCode)
                    .setLabel(stream.title ?: stream.displayTitle)
                    .setSelectionFlags(if (stream.forced) C.SELECTION_FLAG_FORCED else 0)
                    .build()
            }

    companion object {
        const val TAG = "PlexLite"

        fun playableMedia(item: Metadata): List<Int> {
            item.media.forEachIndexed { index, media ->
                media.parts.forEach { p -> Log.i(TAG, "Media[$index] id=${media.id} part=${p.id} file=${p.file} exists=${p.exists} accessible=${p.accessible}") }
            }
            return item.media.indices.filter { index ->
                val parts = item.media[index].parts
                parts.isNotEmpty() && parts.all { it.exists != false && it.accessible != false }
            }
        }

        fun unavailableMessage(item: Metadata): String {
            val part = item.media.firstOrNull()?.parts?.firstOrNull()
                ?: return "Este elemento no tiene ningún archivo reproducible."
            val reason = if (part.exists == false) "no existe" else "sin acceso"
            return "El servidor Plex no puede leer el archivo de este vídeo ($reason):\n${part.file.orEmpty()}"
        }

        fun sidecarId(stream: Stream) = "plex-${stream.id}"
    }
}
