package dev.plexlite.tv.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.Http
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.data.Part
import dev.plexlite.tv.data.PlexServer
import dev.plexlite.tv.data.Stream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private lateinit var server: PlexServer
    private lateinit var resolver: StreamResolver
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var hintView: TextView
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var errorDetails: TextView
    private lateinit var errorButton: TextView

    private var current: Metadata? = null
    private var currentPart: Part? = null
    private var sessionId = newSessionId()
    private var transcoding = false
    private var pendingTrackPart: Part? = null
    private var queue: List<Metadata> = emptyList()
    private var queueId: Long? = null
    private var hintAction: (() -> Unit)? = null
    private var loadJob: Job? = null
    private var released = false
    private var lastReportedState: String? = null
    private var mediaCandidates: List<Int> = emptyList()
    private var currentAttempt = Attempt(0, false)

    private data class Attempt(val candidate: Int, val transcode: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val activeServer = AppGraph.server
        val ratingKey = intent.getStringExtra(EXTRA_RATING_KEY)
        if (activeServer == null || ratingKey == null) {
            finish()
            return
        }
        server = activeServer
        resolver = StreamResolver(server)
        player = buildPlayer()
        buildViews()
        player.addListener(listener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playerView.isControllerFullyVisible) playerView.hideController() else finish()
            }
        })

        load(ratingKey, fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false))
        startTickers()
    }

    private fun buildPlayer(): ExoPlayer {
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 1_500, 3_000)
            .setTargetBufferBytes(200 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val httpFactory = OkHttpDataSource.Factory(Http.client)
            .setDefaultRequestProperties(
                AppGraph.identity.headers.mapValues { (_, v) -> v.filter { it.code in 32..126 } } + ("X-Plex-Token" to server.saved.token),
            )
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory), extractors)
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun buildViews() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            player = this@PlayerActivity.player
            useController = true
            controllerShowTimeoutMs = 8_000
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowShuffleButton(false)
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                titleView.visibility = visibility
                updateHint()
            })
        }
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
            setPadding(dp(48), dp(32), dp(48), dp(16))
            visibility = View.GONE
        }
        root.addView(titleView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        hintView = TextView(this).apply {
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.WHITE)
            }
            visibility = View.GONE
        }
        root.addView(
            hintView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
                .apply { setMargins(0, 0, dp(64), dp(64)) },
        )

        buildErrorPanel(root)

        setContentView(root)
        styleControllerFocus(playerView)
    }

    private fun load(ratingKey: String, fromStart: Boolean, attempt: Attempt = Attempt(0, false), resumeAtMs: Long? = null) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            hideError()
            val item = runCatching { server.metadata(ratingKey, checkFiles = true) }.getOrNull()
            if (item == null) {
                showError("No se pudo cargar este vídeo desde el servidor.", null)
                return@launch
            }
            current = item
            titleView.text = when (item.type) {
                "episode" -> listOfNotNull(item.grandparentTitle, item.episodeLabel, item.title).joinToString("  ·  ")
                else -> item.title
            }
            if (item.type == "episode" && queue.none { it.ratingKey == item.ratingKey }) loadQueue(item.ratingKey)

            mediaCandidates = StreamResolver.playableMedia(item)
            val mediaIndex = mediaCandidates.getOrNull(attempt.candidate)
            if (mediaIndex == null) {
                showError(StreamResolver.unavailableMessage(item), null)
                return@launch
            }
            currentAttempt = attempt
            Log.i(StreamResolver.TAG, "Attempt media=$mediaIndex (${attempt.candidate + 1}/${mediaCandidates.size}) transcode=${attempt.transcode}")

            val plan = runCatching {
                resolver.resolve(item, mediaIndex, sessionId, AppGraph.prefs.quality, attempt.transcode)
            }.getOrElse {
                Log.e(StreamResolver.TAG, "Resolve failed", it)
                failOver(it.message ?: "No se pudo iniciar la reproducción.", null, resumeAtMs)
                return@launch
            }
            currentAttempt = attempt.copy(transcode = plan.transcode)
            transcoding = plan.transcode
            currentPart = plan.part
            pendingTrackPart = if (plan.transcode) null else plan.part
            applyDefaultTrackPreferences(plan.part, plan.transcode)

            val startMs = resumeAtMs ?: if (fromStart) 0L else item.viewOffset ?: 0L
            Log.i(StreamResolver.TAG, "Playing rk=$ratingKey transcode=${plan.transcode} startMs=$startMs url=${plan.mediaItem.localConfiguration?.uri?.toString()?.replace(Regex("X-Plex-Token=[^&]+"), "X-Plex-Token=REDACTED")?.take(400)}")
            player.setMediaItem(plan.mediaItem, startMs)
            player.prepare()
            player.playWhenReady = true
            report("buffering")
        }
    }

    private fun failOver(message: String, details: String?, positionMs: Long?) {
        val item = current
        val next = nextAttempt()
        if (item == null || next == null) {
            showError(message, details)
            return
        }
        rotateSession()
        load(item.ratingKey, fromStart = false, attempt = next, resumeAtMs = positionMs)
    }

    private fun nextAttempt(): Attempt? = when {
        !currentAttempt.transcode -> currentAttempt.copy(transcode = true)
        currentAttempt.candidate + 1 < mediaCandidates.size -> Attempt(currentAttempt.candidate + 1, false)
        else -> null
    }

    private fun loadQueue(ratingKey: String) {
        lifecycleScope.launch {
            runCatching { server.playQueue(ratingKey) }.onSuccess {
                queue = it.metadata
                queueId = it.playQueueID
            }
        }
    }

    private fun applyDefaultTrackPreferences(part: Part, transcode: Boolean) {
        val audio = part.streams.firstOrNull { it.streamType == Stream.AUDIO && it.selected }
        val subtitle = part.streams.firstOrNull { it.streamType == Stream.SUBTITLE && it.selected }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredAudioLanguage(audio?.languageCode)
            .setPreferredTextLanguage(if (transcode) null else subtitle?.languageCode)
            .setIgnoredTextSelectionFlags(if (subtitle == null || transcode) C.SELECTION_FLAG_DEFAULT else 0)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, transcode)
            .build()
    }

    private fun applyExactTracks(tracks: Tracks) {
        val part = pendingTrackPart ?: return
        if (tracks.groups.isEmpty()) return
        pendingTrackPart = null
        val builder = player.trackSelectionParameters.buildUpon()
        part.streams.firstOrNull { it.streamType == Stream.AUDIO && it.selected }?.let { stream ->
            findTrack(tracks, C.TRACK_TYPE_AUDIO, stream)?.let { builder.setOverrideForType(it) }
        }
        part.streams.firstOrNull { it.streamType == Stream.SUBTITLE && it.selected }?.let { stream ->
            findTrack(tracks, C.TRACK_TYPE_TEXT, stream)?.let { builder.setOverrideForType(it) }
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun findTrack(tracks: Tracks, type: Int, stream: Stream): TrackSelectionOverride? {
        val expectedIds = if (stream.key != null) {
            setOf(StreamResolver.sidecarId(stream))
        } else {
            setOfNotNull(stream.index?.let { (it + 1).toString() })
        }
        val wantedLanguage = stream.languageCode?.let { Util.normalizeLanguageCode(it) }
        val candidates = tracks.groups.filter { it.type == type }
        for (group in candidates) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val id = format.id?.substringAfterLast(':')
                val language = format.language?.let { Util.normalizeLanguageCode(it) }
                val languageOk = wantedLanguage == null || language == null || language == wantedLanguage
                if (id != null && id in expectedIds && languageOk) return TrackSelectionOverride(group.mediaTrackGroup, i)
            }
        }
        return null
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) = applyExactTracks(tracks)

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            report(
                when {
                    isPlaying -> "playing"
                    player.playbackState == Player.STATE_BUFFERING -> "buffering"
                    else -> "paused"
                },
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            report("stopped", force = true)
            val next = nextItem()
            if (next != null && AppGraph.prefs.autoPlayNext) playNext(next) else finish()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                report(if (player.isPlaying) "playing" else "paused", force = true)
                updateHint()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            logError(error)
            failOver(friendlyMessage(error), technicalDetails(error), player.currentPosition)
        }
    }

    private fun startTickers() {
        lifecycleScope.launch {
            while (isActive) {
                delay(500)
                updateHint()
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) report("playing", force = true)
            }
        }
    }

    private fun updateHint() {
        val item = current
        if (item == null || playerView.isControllerFullyVisible || player.playbackState == Player.STATE_IDLE) {
            setHint(null, null)
            return
        }
        val position = player.currentPosition
        val intro = item.markers.firstOrNull { it.type == "intro" && position >= it.startTimeOffset && position < it.endTimeOffset - 1_000 }
        val credits = item.markers.firstOrNull { it.type == "credits" && position >= it.startTimeOffset && (it.final || position < it.endTimeOffset - 1_000) }
        val next = nextItem()
        when {
            intro != null -> setHint("OK  ·  Saltar intro") { player.seekTo(intro.endTimeOffset) }
            credits != null && next != null -> setHint("OK  ·  Siguiente episodio") { playNext(next) }
            credits != null && !credits.final -> setHint("OK  ·  Saltar créditos") { player.seekTo(credits.endTimeOffset) }
            else -> setHint(null, null)
        }
    }

    private fun setHint(text: String?, action: (() -> Unit)?) {
        hintAction = action
        if (text == null) {
            hintView.visibility = View.GONE
        } else {
            hintView.text = text
            hintView.visibility = View.VISIBLE
        }
    }

    private fun nextItem(): Metadata? {
        val item = current ?: return null
        val index = queue.indexOfFirst { it.ratingKey == item.ratingKey }
        return if (index >= 0) queue.getOrNull(index + 1) else null
    }

    private fun playNext(next: Metadata) {
        report("stopped", force = true)
        rotateSession()
        player.stop()
        setHint(null, null)
        load(next.ratingKey, fromStart = false)
    }

    private fun rotateSession() {
        val oldSession = sessionId
        val wasTranscoding = transcoding
        sessionId = newSessionId()
        transcoding = false
        if (wasTranscoding) AppGraph.appScope.launch { runCatching { server.stopTranscode(oldSession) } }
    }

    private fun report(state: String, force: Boolean = false) {
        val item = current ?: return
        if (!force && state == lastReportedState) return
        lastReportedState = state
        val position = player.currentPosition.coerceAtLeast(0)
        val duration = player.duration.takeIf { it > 0 } ?: item.duration ?: 0L
        val session = sessionId
        val queueItem = queue.firstOrNull { it.ratingKey == item.ratingKey }
        val reported = if (queueItem?.playQueueItemID != null) item.copy(playQueueItemID = queueItem.playQueueItemID) else item
        val pq = queueId
        AppGraph.appScope.launch { runCatching { server.timeline(reported, state, position, duration, session, pq) } }
    }

    private fun logError(error: PlaybackException) {
        Log.e(StreamResolver.TAG, "Playback error transcoding=$transcoding code=${error.errorCodeName}", error)
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                val url = cause.dataSpec.uri.toString().replace(Regex("X-Plex-Token=[^&]+"), "X-Plex-Token=REDACTED")
                Log.e(StreamResolver.TAG, "HTTP ${cause.responseCode} for $url body=${String(cause.responseBody).take(300)}")
            }
            cause = cause.cause
        }
    }

    private fun buildErrorPanel(root: FrameLayout) {
        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), dp(40), dp(48), dp(36))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xF21C1E23.toInt())
            }
            visibility = View.GONE
        }
        errorPanel.addView(
            TextView(this).apply {
                text = "No se puede reproducir"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
        )
        errorMessage = TextView(this).apply {
            setTextColor(0xFFDADDE3.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(16), 0, 0)
        }
        errorPanel.addView(errorMessage)
        errorDetails = TextView(this).apply {
            setTextColor(0xFF8A8F99.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        errorPanel.addView(errorDetails)
        errorButton = TextView(this).apply {
            text = "OK"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(160)
            setPadding(dp(32), dp(12), dp(32), dp(12))
            isFocusable = true
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), pill(0xFFE5A00D.toInt()))
                addState(intArrayOf(), pill(0xFF3A3D44.toInt()))
            }
            setTextColor(Color.WHITE)
            setOnFocusChangeListener { view, hasFocus ->
                (view as TextView).setTextColor(if (hasFocus) Color.BLACK else Color.WHITE)
            }
            setOnClickListener { finish() }
        }
        errorPanel.addView(
            errorButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(28) },
        )
        root.addView(errorPanel, FrameLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun styleControllerFocus(view: View) {
        when (view) {
            is DefaultTimeBar -> view.setOnFocusChangeListener { _, hasFocus ->
                view.setPlayedColor(if (hasFocus) ACCENT else Color.WHITE)
                view.setScrubberColor(if (hasFocus) ACCENT else Color.WHITE)
            }
            is ImageView -> if (view.isFocusable) {
                view.background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x55E5A00D)
                        setStroke(dp(3), ACCENT)
                    })
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
            }
            is ViewGroup -> for (i in 0 until view.childCount) styleControllerFocus(view.getChildAt(i))
        }
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(28).toFloat()
        setColor(color)
    }

    private fun showError(message: String, details: String?) {
        player.stop()
        setHint(null, null)
        playerView.hideController()
        playerView.useController = false
        titleView.visibility = View.VISIBLE
        errorMessage.text = message
        errorDetails.text = details.orEmpty()
        errorDetails.visibility = if (details.isNullOrEmpty()) View.GONE else View.VISIBLE
        errorPanel.visibility = View.VISIBLE
        errorButton.requestFocus()
    }

    private fun hideError() {
        errorPanel.visibility = View.GONE
        playerView.useController = true
    }

    private val errorVisible: Boolean get() = errorPanel.visibility == View.VISIBLE

    private fun friendlyMessage(error: PlaybackException): String {
        val httpCode = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        return when {
            httpCode == 404 -> "El servidor Plex no encuentra el archivo de este vídeo. Puede que se haya movido o que el disco no esté disponible."
            httpCode == 401 || httpCode == 403 -> "El servidor Plex ha rechazado el acceso a este vídeo."
            httpCode != null && httpCode >= 500 -> "El servidor Plex ha tenido un error al preparar este vídeo."
            error.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "Se ha perdido la conexión con el servidor Plex."
            error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED ->
                "La TV no ha podido decodificar este vídeo."
            else -> "Ha ocurrido un error inesperado durante la reproducción."
        }
    }

    private fun technicalDetails(error: PlaybackException): String {
        val httpCode = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        return listOfNotNull(error.errorCodeName, httpCode?.let { "HTTP $it" }).joinToString(" · ")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (errorVisible) return super.dispatchKeyEvent(event)
        if (!released && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    val action = hintAction
                    if (action != null) {
                        if (event.action == KeyEvent.ACTION_UP) action()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        player.seekBack()
                        playerView.showController()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        player.seekForward()
                        playerView.showController()
                    }
                    return true
                }
            }
        }
        return (!released && playerView.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
        finish()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun releasePlayer() {
        if (released || !::player.isInitialized) return
        released = true
        report("stopped", force = true)
        if (transcoding) {
            val session = sessionId
            AppGraph.appScope.launch { runCatching { server.stopTranscode(session) } }
        }
        player.removeListener(listener)
        player.release()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ACCENT = 0xFFE5A00D.toInt()
        private const val EXTRA_RATING_KEY = "rating_key"
        private const val EXTRA_FROM_START = "from_start"

        private fun newSessionId() = UUID.randomUUID().toString()

        fun intent(context: Context, ratingKey: String, fromStart: Boolean): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_RATING_KEY, ratingKey)
                .putExtra(EXTRA_FROM_START, fromStart)
    }
}
