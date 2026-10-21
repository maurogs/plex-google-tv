package dev.plexlite.tv.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PlexServer(val saved: SavedServer, private val identity: PlexIdentity) {

    private val base: HttpUrl = saved.uri.toHttpUrl()

    suspend fun continueWatching(): List<Metadata> {
        val container = get("/hubs/continueWatching", "count" to "24")
        return container.hubs.firstOrNull()?.metadata ?: container.metadata
    }

    suspend fun homeHubs(): List<Hub> =
        get("/hubs", "count" to "16", "excludeContinueWatching" to "1").hubs
            .filter { it.type in VIDEO_TYPES || it.type == "mixed" }
            .map { hub -> hub.copy(metadata = hub.metadata.filter { it.type in VIDEO_TYPES && it.ratingKey.isNotEmpty() }) }
            .filter { it.metadata.isNotEmpty() }

    suspend fun sectionHubs(sectionId: String): List<Hub> =
        get("/hubs/sections/$sectionId", "count" to "16").hubs
            .filter { it.type in VIDEO_TYPES || it.type == "mixed" }
            .map { hub -> hub.copy(metadata = hub.metadata.filter { it.type in VIDEO_TYPES && it.ratingKey.isNotEmpty() }) }
            .filter { it.metadata.isNotEmpty() }

    private companion object {
        val VIDEO_TYPES = setOf("movie", "show", "season", "episode")
    }

    suspend fun search(query: String): List<Hub> =
        get("/hubs/search", "query" to query, "limit" to "20", "includeCollections" to "0", "includeExternalMedia" to "0").hubs

    suspend fun sections(): List<Directory> =
        get("/library/sections").directories.filter { it.type == "movie" || it.type == "show" }

    suspend fun sectionItems(
        sectionId: String,
        start: Int,
        size: Int,
        unwatchedOnly: Boolean,
        shows: Boolean,
        sort: LibrarySort = LibrarySort.TITLE,
    ): MediaContainer {
        val params = buildList {
            add("sort" to sort.param)
            add("X-Plex-Container-Start" to start.toString())
            add("X-Plex-Container-Size" to size.toString())
            add("excludeFields" to "summary,tagline")
            if (unwatchedOnly) add((if (shows) "unwatchedLeaves" else "unwatched") to "1")
        }
        return get("/library/sections/$sectionId/all", *params.toTypedArray())
    }

    suspend fun firstCharacters(sectionId: String, unwatchedOnly: Boolean, shows: Boolean): List<Directory> {
        val params = if (unwatchedOnly) arrayOf((if (shows) "unwatchedLeaves" else "unwatched") to "1") else emptyArray()
        return get("/library/sections/$sectionId/firstCharacter", *params).directories
    }

    suspend fun metadata(ratingKey: String, checkFiles: Boolean = false, extras: Boolean = false): Metadata? =
        get(
            "/library/metadata/$ratingKey",
            "checkFiles" to if (checkFiles) "1" else "0",
            "includeExtras" to if (extras) "1" else "0",
            "includeChapters" to "1",
            "includeMarkers" to "1",
            "includeOnDeck" to "1",
        ).metadata.firstOrNull()

    suspend fun children(ratingKey: String): List<Metadata> =
        get("/library/metadata/$ratingKey/children", "excludeAllLeaves" to "1").metadata

    suspend fun playQueue(ratingKey: String): MediaContainer {
        val url = url(
            "/playQueues",
            "type" to "video",
            "uri" to "server://${saved.id}/com.plexapp.plugins.library/library/metadata/$ratingKey",
            "continuous" to "1",
            "shuffle" to "0",
            "repeat" to "0",
            "includeMarkers" to "1",
        )
        return Http.fetchJson<Envelope>(request(url).post(ByteArray(0).toRequestBody()).build()).container
    }

    suspend fun decision(params: List<Pair<String, String>>, sessionId: String): MediaContainer {
        val url = url("/video/:/transcode/universal/decision", *params.toTypedArray())
        return Http.fetchJson<Envelope>(request(url).header("X-Plex-Session-Identifier", sessionId).build()).container
    }

    fun transcodeUrl(params: List<Pair<String, String>>): String =
        url("/video/:/transcode/universal/start.m3u8", *(params + tokenParams()).toTypedArray()).toString()

    fun partUrl(partKey: String, sessionId: String): String =
        url(partKey, *(tokenParams() + ("X-Plex-Session-Identifier" to sessionId)).toTypedArray()).toString()

    fun subtitleUrl(streamKey: String): String =
        url(streamKey, *(tokenParams() + ("encoding" to "utf-8")).toTypedArray()).toString()

    suspend fun timeline(
        item: Metadata,
        state: String,
        timeMs: Long,
        durationMs: Long,
        sessionId: String,
        playQueueId: Long?,
    ) {
        val params = buildList {
            add("ratingKey" to item.ratingKey)
            add("key" to "/library/metadata/${item.ratingKey}")
            add("identifier" to "com.plexapp.plugins.library")
            add("state" to state)
            add("time" to timeMs.toString())
            add("duration" to durationMs.toString())
            add("X-Plex-Session-Identifier" to sessionId)
            playQueueId?.let { add("playQueueID" to it.toString()) }
            item.playQueueItemID?.let { add("playQueueItemID" to it.toString()) }
        }
        val url = url("/:/timeline", *params.toTypedArray())
        Http.fetch(request(url).header("X-Plex-Session-Identifier", sessionId).post(ByteArray(0).toRequestBody()).build())
    }

    suspend fun stopTranscode(sessionId: String) {
        val url = url(
            "/video/:/transcode/universal/stop",
            "session" to sessionId,
            "closeResourceSession" to "1",
            "X-Plex-Session-Identifier" to sessionId,
        )
        Http.fetch(request(url).build())
    }

    suspend fun selectAudioStream(partId: Long, audioStreamId: Long) {
        val url = url("/library/parts/$partId", "allParts" to "1", "audioStreamID" to audioStreamId.toString())
        Http.fetch(request(url).put(ByteArray(0).toRequestBody()).build())
    }

    suspend fun selectSubtitleStream(partId: Long, subtitleStreamId: Long) {
        val url = url("/library/parts/$partId", "allParts" to "1", "subtitleStreamID" to subtitleStreamId.toString())
        Http.fetch(request(url).put(ByteArray(0).toRequestBody()).build())
    }

    suspend fun searchSubtitles(ratingKey: String, language: String): List<Stream> =
        get("/library/metadata/$ratingKey/subtitles", "language" to language, "hearingImpaired" to "0", "forced" to "0")
            .streams.filter { it.key != null }

    suspend fun downloadSubtitle(ratingKey: String, candidateKey: String) {
        val url = url("/library/metadata/$ratingKey/subtitles", "key" to candidateKey)
        Http.fetch(request(url).put(ByteArray(0).toRequestBody()).build())
    }

    suspend fun scrobble(ratingKey: String, watched: Boolean) {
        val path = if (watched) "/:/scrobble" else "/:/unscrobble"
        Http.fetch(request(url(path, "key" to ratingKey, "identifier" to "com.plexapp.plugins.library")).build())
    }

    fun photoUrl(path: String?, width: Int, height: Int): String? {
        if (path.isNullOrEmpty()) return null
        return url(
            "/photo/:/transcode",
            "width" to width.toString(),
            "height" to height.toString(),
            "minSize" to "1",
            "upscale" to "1",
            "url" to path,
            "X-Plex-Token" to saved.token,
        ).toString()
    }

    private fun tokenParams(): List<Pair<String, String>> = identity.queryParams + ("X-Plex-Token" to saved.token)

    private suspend fun get(path: String, vararg params: Pair<String, String>): MediaContainer =
        Http.fetchJson<Envelope>(request(url(path, *params)).build()).container

    private fun url(path: String, vararg params: Pair<String, String>): HttpUrl {
        val builder = base.newBuilder().encodedPath(path.substringBefore('?'))
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    private fun request(url: HttpUrl): Request.Builder = identity.apply(Request.Builder().url(url), saved.token)
}
