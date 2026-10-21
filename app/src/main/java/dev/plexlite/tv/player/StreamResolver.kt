package dev.plexlite.tv.player

import android.net.Uri
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

    suspend fun resolve(item: Metadata, sessionId: String, quality: Quality, forceTranscode: Boolean): PlaybackPlan {
        val part = item.media.firstOrNull()?.parts?.firstOrNull() ?: error("Este elemento no tiene ningún archivo reproducible")
        val effectiveQuality = if (server.saved.relay && quality == Quality.ORIGINAL) Quality.MBPS_2 else quality
        val audio = part.streams.firstOrNull { it.streamType == Stream.AUDIO && it.selected }
        val subtitle = part.streams.firstOrNull { it.streamType == Stream.SUBTITLE && it.selected }

        val common = buildList {
            add("path" to "/library/metadata/${item.ratingKey}")
            add("mediaIndex" to "0")
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
        server.decision(transcodeParams, sessionId)
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
        fun sidecarId(stream: Stream) = "plex-${stream.id}"
    }
}
