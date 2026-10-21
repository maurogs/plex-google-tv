package dev.plexlite.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Envelope(@SerialName("MediaContainer") val container: MediaContainer = MediaContainer())

@Serializable
data class MediaContainer(
    val size: Int = 0,
    val totalSize: Int? = null,
    val machineIdentifier: String? = null,
    val playQueueID: Long? = null,
    val playQueueSelectedItemID: Long? = null,
    val generalDecisionCode: Int? = null,
    val generalDecisionText: String? = null,
    val transcodeDecisionCode: Int? = null,
    val transcodeDecisionText: String? = null,
    @SerialName("Metadata") val metadata: List<Metadata> = emptyList(),
    @SerialName("Hub") val hubs: List<Hub> = emptyList(),
    @SerialName("Directory") val directories: List<Directory> = emptyList(),
)

@Serializable
data class Hub(
    val hubIdentifier: String = "",
    val title: String = "",
    val type: String = "",
    val key: String? = null,
    val size: Int = 0,
    val more: Boolean = false,
    @SerialName("Metadata") val metadata: List<Metadata> = emptyList(),
)

@Serializable
data class Directory(
    val key: String = "",
    val title: String = "",
    val type: String = "",
    val thumb: String? = null,
    val art: String? = null,
)

@Serializable
data class Metadata(
    val ratingKey: String = "",
    val key: String? = null,
    val type: String = "",
    val title: String = "",
    val year: Int? = null,
    val summary: String? = null,
    val tagline: String? = null,
    val thumb: String? = null,
    val art: String? = null,
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    val grandparentRatingKey: String? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val parentRatingKey: String? = null,
    val parentIndex: Int? = null,
    val index: Int? = null,
    val viewOffset: Long? = null,
    val duration: Long? = null,
    val viewCount: Int? = null,
    val leafCount: Int? = null,
    val viewedLeafCount: Int? = null,
    val childCount: Int? = null,
    val contentRating: String? = null,
    val audienceRating: Double? = null,
    val originallyAvailableAt: String? = null,
    val playQueueItemID: Long? = null,
    @SerialName("Media") val media: List<Media> = emptyList(),
    @SerialName("Marker") val markers: List<Marker> = emptyList(),
    @SerialName("Genre") val genres: List<Tag> = emptyList(),
    @SerialName("OnDeck") val onDeck: OnDeck? = null,
) {
    val isWatched: Boolean
        get() = when (type) {
            "show", "season" -> leafCount != null && leafCount > 0 && leafCount == viewedLeafCount
            else -> (viewCount ?: 0) > 0
        }

    val progress: Float?
        get() {
            val offset = viewOffset ?: return null
            val total = duration ?: return null
            if (offset <= 0 || total <= 0) return null
            return (offset.toFloat() / total).coerceIn(0f, 1f)
        }

    val episodeLabel: String?
        get() = if (parentIndex != null && index != null) "T$parentIndex · E$index" else null
}

@Serializable
data class OnDeck(@SerialName("Metadata") val metadata: List<Metadata> = emptyList())

@Serializable
data class Tag(val tag: String = "")

@Serializable
data class Marker(
    val type: String = "",
    val startTimeOffset: Long = 0,
    val endTimeOffset: Long = 0,
    val final: Boolean = false,
)

@Serializable
data class Media(
    val id: Long = 0,
    val duration: Long? = null,
    val bitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val videoResolution: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val container: String? = null,
    val videoDynamicRange: String? = null,
    @SerialName("Part") val parts: List<Part> = emptyList(),
)

@Serializable
data class Part(
    val id: Long = 0,
    val key: String = "",
    val duration: Long? = null,
    val size: Long? = null,
    val container: String? = null,
    val file: String? = null,
    val exists: Boolean? = null,
    val accessible: Boolean? = null,
    val decision: String? = null,
    @SerialName("Stream") val streams: List<Stream> = emptyList(),
)

@Serializable
data class Stream(
    val id: Long = 0,
    val streamType: Int = 0,
    val index: Int? = null,
    val key: String? = null,
    val codec: String? = null,
    val title: String? = null,
    val language: String? = null,
    val languageCode: String? = null,
    val displayTitle: String? = null,
    val selected: Boolean = false,
    val forced: Boolean = false,
    val default: Boolean = false,
    val decision: String? = null,
) {
    companion object {
        const val VIDEO = 1
        const val AUDIO = 2
        const val SUBTITLE = 3
    }
}

@Serializable
data class Pin(
    val id: Long = 0,
    val code: String = "",
    val authToken: String? = null,
    val expiresIn: Int? = null,
)

@Serializable
data class Resource(
    val name: String = "",
    val clientIdentifier: String = "",
    val provides: String = "",
    val owned: Boolean = false,
    val accessToken: String? = null,
    val publicAddressMatches: Boolean = false,
    val httpsRequired: Boolean = false,
    val presence: Boolean = false,
    val connections: List<Connection> = emptyList(),
) {
    val isServer: Boolean get() = provides.split(',').contains("server")
}

@Serializable
data class Connection(
    val protocol: String = "",
    val address: String = "",
    val port: Int = 0,
    val uri: String = "",
    val local: Boolean = false,
    val relay: Boolean = false,
    @SerialName("IPv6") val ipv6: Boolean = false,
)
