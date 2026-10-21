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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.CaptioningManager
import android.widget.FrameLayout
import android.widget.ImageButton
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
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.R as Media3R
import dev.plexlite.tv.R
import dev.plexlite.tv.data.Quality
import dev.plexlite.tv.data.SubtitleSize
import dev.plexlite.tv.ui.components.formatClock
import dev.plexlite.tv.data.AppGraph
import dev.plexlite.tv.data.AppLocale
import dev.plexlite.tv.data.Http
import dev.plexlite.tv.data.Metadata
import dev.plexlite.tv.data.Part
import dev.plexlite.tv.data.PlexServer
import dev.plexlite.tv.data.Stream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private lateinit var server: PlexServer
    private lateinit var resolver: StreamResolver
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var hintView: TextView
    private lateinit var seekView: TextView
    private var pendingSeekMs = 0L
    private var seekBaseMs = 0L
    private var seekJob: Job? = null
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
    private var qualityOverride: Quality? = null
    private var audioOverride: Long? = null
    private var subtitleOverride: Long? = null
    private var subtitleSearch: SubtitleSearch = SubtitleSearch()
    private lateinit var menu: OverlayMenu
    private var menuOpener: View? = null

    private data class Attempt(val candidate: Int, val transcode: Boolean)

    private data class SubtitleSearch(
        val language: String? = null,
        val loading: Boolean = false,
        val results: List<Stream>? = null,
        val error: String? = null,
        val downloadingKey: String? = null,
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.prefs.language))
    }

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
                when {
                    menu.back() -> Unit
                    playerView.isControllerFullyVisible -> playerView.hideController()
                    else -> finish()
                }
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
            controllerAutoShow = false
            controllerShowTimeoutMs = 8_000
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setShowSubtitleButton(false)
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

        menu = OverlayMenu(this, root).apply { onClosed = ::onMenuClosed }
        seekView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0x80000000.toInt())
            }
            visibility = View.GONE
        }
        root.addView(seekView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        buildErrorPanel(root)

        setContentView(root)
        installControlButtons()
        styleControllerFocus(playerView)
        applySubtitleSize()
    }

    private fun load(ratingKey: String, fromStart: Boolean, attempt: Attempt = Attempt(0, false), resumeAtMs: Long? = null) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            hideError()
            val item = runCatching { server.metadata(ratingKey, checkFiles = true) }.getOrNull()
            if (item == null) {
                showError(getString(R.string.player_load_error), null)
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
                resolver.resolve(item, mediaIndex, sessionId, activeQuality, attempt.transcode, audioOverride, subtitleOverride, AppGraph.prefs.subtitleSize.scale)
            }.getOrElse {
                Log.e(StreamResolver.TAG, "Resolve failed", it)
                failOver(it.message ?: getString(R.string.player_start_error), null, resumeAtMs)
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

    private val activeQuality: Quality get() = qualityOverride ?: AppGraph.prefs.quality

    private fun activeAudioStream(part: Part): Stream? =
        part.streams.firstOrNull { it.streamType == Stream.AUDIO && it.id == audioOverride }
            ?: part.streams.firstOrNull { it.streamType == Stream.AUDIO && it.selected }

    private fun installControlButtons() {
        val controls = playerView.findViewById<ViewGroup>(Media3R.id.exo_basic_controls) ?: return
        playerView.findViewById<View>(Media3R.id.exo_settings)?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val template = playerView.findViewById<View>(Media3R.id.exo_subtitle)
        fun button(icon: Int, description: String, onClick: (View) -> Unit) = ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = description
            scaleType = ImageView.ScaleType.FIT_CENTER
            template?.let { setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
            setOnClickListener(onClick)
        }
        val width = template?.layoutParams?.width ?: dp(52)
        val height = template?.layoutParams?.height ?: dp(52)
        template?.let { (it.parent as? ViewGroup)?.removeView(it) }
        controls.addView(button(R.drawable.ic_subtitles, getString(R.string.player_subtitles)) { openSubtitleMenu(it) }, LinearLayout.LayoutParams(width, height))
        controls.addView(button(R.drawable.ic_audio, getString(R.string.player_audio)) { openAudioMenu(it) }, LinearLayout.LayoutParams(width, height))
        controls.addView(button(R.drawable.ic_settings, getString(R.string.settings)) { openSettingsMenu(it) }, LinearLayout.LayoutParams(width, height))
    }

    private fun openMenu(opener: View, title: String, entries: () -> List<MenuEntry>) {
        menuOpener = opener
        playerView.controllerShowTimeoutMs = 0
        playerView.showController()
        menu.open(title, entries)
    }

    private fun onMenuClosed() {
        playerView.controllerShowTimeoutMs = 8_000
        playerView.showController()
        menuOpener?.requestFocus()
        menuOpener = null
    }

    private fun openAudioMenu(opener: View) {
        val part = currentPart ?: return
        openMenu(opener, getString(R.string.player_audio)) {
            val active = activeAudioStream(part)
            part.streams.filter { it.streamType == Stream.AUDIO }.map { stream ->
                val title = stream.title?.takeIf { it.isNotBlank() }
                MenuEntry(
                    label = title ?: stream.displayTitle ?: stream.language ?: getString(R.string.player_track, (stream.index ?: stream.id).toString()),
                    value = if (title != null) stream.displayTitle else null,
                    checked = stream.id == active?.id,
                ) { selectAudio(part, stream) }
            }
        }
    }

    private fun selectAudio(part: Part, stream: Stream) {
        menu.close()
        if (stream.id == activeAudioStream(part)?.id) return
        audioOverride = stream.id
        lifecycleScope.launch {
            val saved = runCatching { server.selectAudioStream(part.id, stream.id) }
            if (saved.isFailure) Log.w(StreamResolver.TAG, "Could not save audio selection", saved.exceptionOrNull())
            if (transcoding) {
                restartAtCurrentPosition(currentAttempt.copy(transcode = true))
            } else {
                val override = findTrack(player.currentTracks, C.TRACK_TYPE_AUDIO, stream)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    if (override != null) setOverrideForType(override) else {
                        clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        setPreferredAudioLanguage(stream.languageCode)
                    }
                }.build()
            }
        }
    }

    private fun activeSubtitleStream(part: Part): Stream? = when (val override = subtitleOverride) {
        null -> part.streams.firstOrNull { it.streamType == Stream.SUBTITLE && it.selected }
        0L -> null
        else -> part.streams.firstOrNull { it.streamType == Stream.SUBTITLE && it.id == override }
    }

    private fun openSubtitleMenu(opener: View) {
        if (currentPart == null) return
        openMenu(opener, getString(R.string.player_subtitles), ::subtitleEntries)
    }

    private fun subtitleEntries(): List<MenuEntry> {
        val part = currentPart ?: return emptyList()
        val active = activeSubtitleStream(part)
        return buildList {
            add(MenuEntry(getString(R.string.none), checked = active == null) { selectSubtitle(null) })
            part.streams.filter { it.streamType == Stream.SUBTITLE }.forEach { stream ->
                val title = stream.title?.takeIf { it.isNotBlank() }
                val details = listOfNotNull(
                    stream.displayTitle.takeIf { title != null },
                    getString(R.string.player_forced).takeIf { stream.forced },
                    getString(R.string.player_external).takeIf { stream.key != null },
                ).joinToString(" · ")
                add(
                    MenuEntry(
                        label = title ?: stream.displayTitle ?: stream.language ?: getString(R.string.player_track, (stream.index ?: stream.id).toString()),
                        value = details.ifEmpty { null },
                        checked = stream.id == active?.id,
                    ) { selectSubtitle(stream) },
                )
            }
            add(MenuEntry(getString(R.string.player_search_subtitles), "OpenSubtitles") { openSubtitleSearch() })
        }
    }

    private fun selectSubtitle(stream: Stream?) {
        menu.close()
        val part = currentPart ?: return
        if (stream?.id == activeSubtitleStream(part)?.id) return
        subtitleOverride = stream?.id ?: 0L
        lifecycleScope.launch {
            val saved = runCatching { server.selectSubtitleStream(part.id, stream?.id ?: 0L) }
            if (saved.isFailure) Log.w(StreamResolver.TAG, "Could not save subtitle selection", saved.exceptionOrNull())
            if (transcoding) {
                restartAtCurrentPosition(currentAttempt)
                return@launch
            }
            val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
            if (stream == null) {
                player.trackSelectionParameters = builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                return@launch
            }
            val override = findTrack(player.currentTracks, C.TRACK_TYPE_TEXT, stream)
            if (override == null) {
                restartAtCurrentPosition(currentAttempt)
                return@launch
            }
            player.trackSelectionParameters = builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(override)
                .build()
        }
    }

    private fun openSubtitleSearch() {
        if (subtitleSearch.language == null) subtitleSearch = subtitleSearch.copy(language = defaultSearchLanguage())
        menu.push(getString(R.string.player_search_subtitles_title), ::searchEntries)
        if (subtitleSearch.results == null && !subtitleSearch.loading) runSubtitleSearch()
    }

    private fun defaultSearchLanguage(): String {
        val fromSubtitle = currentPart?.let { activeSubtitleStream(it)?.languageCode }
        val code = fromSubtitle?.let { Util.normalizeLanguageCode(it) } ?: Locale.getDefault().language
        return code.substringBefore('-').takeIf { it.length == 2 } ?: "es"
    }

    private fun runSubtitleSearch() {
        val item = current ?: return
        val language = subtitleSearch.language ?: return
        subtitleSearch = subtitleSearch.copy(loading = true, error = null, results = null)
        menu.refresh()
        lifecycleScope.launch {
            val result = runCatching { server.searchSubtitles(item.ratingKey, language) }
            if (current?.ratingKey != item.ratingKey || subtitleSearch.language != language) return@launch
            result.exceptionOrNull()?.let { Log.w(StreamResolver.TAG, "Subtitle search failed", it) }
            subtitleSearch = subtitleSearch.copy(
                loading = false,
                results = result.getOrNull(),
                error = result.exceptionOrNull()?.let { getString(R.string.player_search_failed) },
            )
            menu.refresh()
        }
    }

    private fun searchEntries(): List<MenuEntry> {
        val search = subtitleSearch
        val languageName = search.language?.let(::languageName).orEmpty()
        return buildList {
            add(MenuEntry(getString(R.string.player_language), languageName) { menu.push(getString(R.string.player_language), ::searchLanguageEntries) })
            val results = search.results
            when {
                search.loading -> add(MenuEntry(getString(R.string.player_searching)) {})
                search.error != null -> add(MenuEntry(getString(R.string.retry), search.error) { runSubtitleSearch() })
                results.isNullOrEmpty() -> add(MenuEntry(getString(R.string.player_no_results)) {})
                else -> results.forEach { candidate ->
                    val key = candidate.key ?: return@forEach
                    add(
                        MenuEntry(
                            label = candidate.title?.takeIf { it.isNotBlank() } ?: candidate.displayTitle ?: getString(R.string.player_subtitle_generic),
                            value = if (search.downloadingKey == key) {
                                getString(R.string.player_downloading)
                            } else {
                                val source = listOfNotNull(candidate.codec?.uppercase(), candidate.providerTitle).joinToString(" ")
                                listOfNotNull(
                                    candidate.displayTitle?.let { if (source.isEmpty()) it else "$it ($source)" },
                                    candidate.score?.let { getString(R.string.player_score, it) },
                                ).joinToString("  ·  ")
                            },
                        ) { downloadSubtitle(candidate) },
                    )
                }
            }
        }
    }

    private fun languageName(code: String): String {
        val appLocale = resources.configuration.locales[0]
        return Locale.forLanguageTag(code).getDisplayLanguage(appLocale).replaceFirstChar { it.titlecase(appLocale) }
    }

    private fun searchLanguageEntries(): List<MenuEntry> = SEARCH_LANGUAGES.map { code ->
        MenuEntry(languageName(code), checked = code == subtitleSearch.language) {
            subtitleSearch = SubtitleSearch(language = code)
            menu.back()
            runSubtitleSearch()
        }
    }

    private fun downloadSubtitle(candidate: Stream) {
        val item = current ?: return
        val part = currentPart ?: return
        val key = candidate.key ?: return
        if (subtitleSearch.downloadingKey != null) return
        subtitleSearch = subtitleSearch.copy(downloadingKey = key)
        menu.refresh()
        val existing = part.streams.filter { it.streamType == Stream.SUBTITLE }.map { it.id }.toSet()
        lifecycleScope.launch {
            val requested = runCatching { server.downloadSubtitle(item.ratingKey, key) }
            requested.exceptionOrNull()?.let { Log.w(StreamResolver.TAG, "Subtitle download failed", it) }
            var added: Stream? = null
            if (requested.isSuccess) {
                for (attempt in 0 until 15) {
                    delay(1_000)
                    val fresh = runCatching { server.metadata(item.ratingKey) }.getOrNull()
                    added = fresh?.media?.flatMap { it.parts }?.firstOrNull { it.id == part.id }
                        ?.streams?.firstOrNull { it.streamType == Stream.SUBTITLE && it.id !in existing }
                    if (added != null) break
                }
            }
            if (current?.ratingKey != item.ratingKey) return@launch
            subtitleSearch = subtitleSearch.copy(downloadingKey = null)
            val stream = added
            if (stream == null) {
                subtitleSearch = subtitleSearch.copy(error = getString(R.string.player_download_failed))
                menu.refresh()
                return@launch
            }
            menu.close()
            subtitleOverride = stream.id
            restartAtCurrentPosition(currentAttempt)
        }
    }

    private fun subtitleSizeEntries(): List<MenuEntry> = SubtitleSize.entries.map { size ->
        MenuEntry(size.label, checked = size == AppGraph.prefs.subtitleSize) {
            val changed = size != AppGraph.prefs.subtitleSize
            AppGraph.prefs.subtitleSize = size
            menu.close()
            if (!changed) return@MenuEntry
            applySubtitleSize()
            if (transcoding && currentPart?.let { activeSubtitleStream(it) } != null) restartAtCurrentPosition(currentAttempt)
        }
    }

    private fun applySubtitleSize() {
        val userScale = getSystemService(CaptioningManager::class.java)?.fontScale ?: 1f
        playerView.subtitleView?.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * userScale * AppGraph.prefs.subtitleSize.scale,
        )
    }

    private fun openSettingsMenu(opener: View) {
        openMenu(opener, getString(R.string.settings)) {
            listOf(
                MenuEntry(getString(R.string.player_mode), modeLabel()) { menu.push(getString(R.string.player_mode), ::modeEntries) },
                MenuEntry(getString(R.string.player_speed), speedLabel(player.playbackParameters.speed)) {
                    menu.push(getString(R.string.player_speed), ::speedEntries)
                },
                MenuEntry(getString(R.string.player_subtitle_size), AppGraph.prefs.subtitleSize.label) {
                    menu.push(getString(R.string.player_subtitle_size), ::subtitleSizeEntries)
                },
            )
        }
    }

    private fun modeLabel(): String {
        val quality = activeQuality
        return when {
            !transcoding -> getString(R.string.player_mode_direct)
            quality == Quality.ORIGINAL -> getString(R.string.player_mode_transcoding_original)
            else -> getString(R.string.player_mode_transcoding, quality.label)
        }
    }

    private fun modeEntries(): List<MenuEntry> = Quality.entries.map { quality ->
        val label = if (quality == Quality.ORIGINAL) getString(R.string.player_mode_direct_option) else getString(R.string.player_mode_transcode_option, quality.label)
        MenuEntry(label, checked = quality == activeQuality) {
            menu.close()
            if (quality == activeQuality && transcoding == (quality != Quality.ORIGINAL)) return@MenuEntry
            qualityOverride = quality
            restartAtCurrentPosition(Attempt(currentAttempt.candidate, transcode = quality != Quality.ORIGINAL))
        }
    }

    private fun speedEntries(): List<MenuEntry> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { speed ->
        MenuEntry(speedLabel(speed), checked = speed == player.playbackParameters.speed) {
            player.setPlaybackSpeed(speed)
            menu.close()
        }
    }

    private fun speedLabel(speed: Float): String = if (speed == 1f) getString(R.string.speed_normal) else "${speed.toString().removeSuffix(".0")}x"

    private fun accumulateSeek(deltaMs: Long) {
        if (current == null || player.playbackState == Player.STATE_IDLE) return
        if (pendingSeekMs == 0L) seekBaseMs = player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (seekBaseMs + pendingSeekMs + deltaMs).coerceIn(0L, duration)
        pendingSeekMs = target - seekBaseMs
        setHint(null, null)
        val amount = when {
            pendingSeekMs < 0 -> "«  ${formatAmount(-pendingSeekMs)}"
            pendingSeekMs > 0 -> "${formatAmount(pendingSeekMs)}  »"
            else -> "0 s"
        }
        val destination = formatClock(target) + if (duration != Long.MAX_VALUE) " / ${formatClock(duration)}" else ""
        seekView.text = SpannableStringBuilder(amount).append("\n").apply {
            val start = length
            append(destination)
            setSpan(RelativeSizeSpan(0.65f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(0xFFB8BCC6.toInt()), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.NORMAL), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        seekView.visibility = View.VISIBLE
        seekJob?.cancel()
        seekJob = lifecycleScope.launch {
            delay(700)
            if (pendingSeekMs != 0L) player.seekTo(target)
            pendingSeekMs = 0L
            delay(900)
            seekView.visibility = View.GONE
        }
    }

    private fun formatAmount(ms: Long): String {
        val seconds = ms / 1000
        return if (seconds < 60) "$seconds s" else "%d:%02d min".format(seconds / 60, seconds % 60)
    }

    private fun restartAtCurrentPosition(attempt: Attempt) {
        val item = current ?: return
        val position = player.currentPosition
        report("stopped", force = true)
        rotateSession()
        load(item.ratingKey, fromStart = false, attempt = attempt, resumeAtMs = position)
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
        val audio = activeAudioStream(part)
        val subtitle = activeSubtitleStream(part)
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
        activeAudioStream(part)?.let { stream ->
            findTrack(tracks, C.TRACK_TYPE_AUDIO, stream)?.let { builder.setOverrideForType(it) }
        }
        activeSubtitleStream(part)?.let { stream ->
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
            intro != null -> setHint(getString(R.string.hint_skip_intro)) { player.seekTo(intro.endTimeOffset) }
            credits != null && next != null -> setHint(getString(R.string.hint_next_episode)) { playNext(next) }
            credits != null && !credits.final -> setHint(getString(R.string.hint_skip_credits)) { player.seekTo(credits.endTimeOffset) }
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
        audioOverride = null
        subtitleOverride = null
        subtitleSearch = SubtitleSearch()
        load(next.ratingKey, fromStart = false, attempt = Attempt(0, transcode = qualityOverride != null && qualityOverride != Quality.ORIGINAL))
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
                text = getString(R.string.error_title)
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
            text = getString(R.string.ok)
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
        if (menu.isVisible) menu.close()
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
            httpCode == 404 -> getString(R.string.error_not_found)
            httpCode == 401 || httpCode == 403 -> getString(R.string.error_denied)
            httpCode != null && httpCode >= 500 -> getString(R.string.error_server)
            error.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                getString(R.string.error_network)
            error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED ->
                getString(R.string.error_decoder)
            else -> getString(R.string.error_unknown)
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
        if (menu.isVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true
            return super.dispatchKeyEvent(event)
        }
        if (!released && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    val action = hintAction
                    if (action != null) {
                        if (event.action == KeyEvent.ACTION_UP) action()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        accumulateSeek(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -SEEK_STEP_MS else SEEK_STEP_MS)
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
        private const val SEEK_STEP_MS = 10_000L
        private val SEARCH_LANGUAGES = listOf("es", "en", "fr", "it", "de", "pt", "ca", "zh", "hi", "ar", "bn", "ru", "ur")
        private const val EXTRA_RATING_KEY = "rating_key"
        private const val EXTRA_FROM_START = "from_start"

        private fun newSessionId() = UUID.randomUUID().toString()

        fun intent(context: Context, ratingKey: String, fromStart: Boolean): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_RATING_KEY, ratingKey)
                .putExtra(EXTRA_FROM_START, fromStart)
    }
}
