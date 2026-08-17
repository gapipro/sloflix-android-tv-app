package com.sloflix.tv.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sloflix.tv.R
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.model.SubtitleTrack
import com.sloflix.tv.ui.TestTags
import com.sloflix.tv.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient

private val PlayerBackground = Color.Black
private val ControlsScrim = Color(0xCC090C12)
private val Accent = Color(0xFFE50913)
private const val ControllerTimeoutMs = 5_000L
private const val SeekStepMs = 10_000L
/** Doubling jump sizes while holding seek; capped at 5 minutes. */
private val SeekAccelerationStepsMs = longArrayOf(
    10_000L,
    20_000L,
    40_000L,
    80_000L,
    160_000L,
    300_000L,
)
private const val SeekHoldInitialDelayMs = 400L
private const val SeekHoldMinDelayMs = 120L

@Composable
fun PlayerScreen(
    titleId: String,
    startPositionMs: Long,
    viewModel: PlayerViewModel,
    mediaOkHttpClient: OkHttpClient,
    onBack: () -> Unit,
    streamP2pEmbedUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, titleId, streamP2pEmbedUrl) {
        viewModel.load(titleId, streamP2pEmbedUrl)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerBackground),
    ) {
        when (val currentState = state) {
            PlayerUiState.Loading -> PlayerLoading()
            is PlayerUiState.Error -> {
                BackHandler(onBack = onBack)
                PlayerError(currentState.message, onBack)
            }
            is PlayerUiState.Ready -> PlayerContent(
                streamInfo = currentState.streamInfo,
                startPositionMs = startPositionMs,
                viewModel = viewModel,
                mediaOkHttpClient = mediaOkHttpClient,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun PlayerContent(
    streamInfo: StreamInfo,
    startPositionMs: Long,
    viewModel: PlayerViewModel,
    mediaOkHttpClient: OkHttpClient,
    onBack: () -> Unit,
) {
    val candidates = streamInfo.candidateUrls
    var candidateIndex by remember(streamInfo) { mutableIntStateOf(0) }
    var playbackFailed by remember(streamInfo) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var subtitlesEnabled by remember(streamInfo) { mutableStateOf(streamInfo.subtitles.isNotEmpty()) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    val rootFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val currentUrl = candidates[candidateIndex]
    val latestControlsVisible = rememberUpdatedState(controlsVisible)

    val player = remember(currentUrl, startPositionMs, mediaOkHttpClient, streamInfo.subtitles) {
        val dataSourceFactory = OkHttpDataSource.Factory(mediaOkHttpClient).apply {
            if (streamInfo.headers.isNotEmpty()) {
                setDefaultRequestProperties(streamInfo.headers)
            }
        }
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredTextLanguage("sl")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            if (candidateIndex < candidates.lastIndex) {
                                candidateIndex += 1
                            } else {
                                playbackFailed = true
                            }
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            setSubtitlesEnabled(this@apply, streamInfo.subtitles, subtitlesEnabled)
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }
                    },
                )
                setMediaItem(buildMediaItem(currentUrl, streamInfo.subtitles))
                if (startPositionMs > 0) seekTo(startPositionMs)
                prepare()
                playWhenReady = true
            }
    }

    LaunchedEffect(player, viewModel) {
        viewModel.startProgressReporting(
            positionMs = { player.currentPosition },
            durationMs = { player.duration },
        )
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, controlsTick) {
        if (controlsVisible && isPlaying) {
            delay(ControllerTimeoutMs)
            if (latestControlsVisible.value) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(subtitlesEnabled, player, streamInfo.subtitles) {
        setSubtitlesEnabled(player, streamInfo.subtitles, subtitlesEnabled)
    }

    // When the overlay leaves composition, reclaim focus so remote keys can show controls again.
    // When shown, park focus on Play so D-pad L/R can reach seek and CC.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            playFocusRequester.requestFocus()
        } else {
            rootFocusRequester.requestFocus()
        }
    }

    DisposableEffect(player, viewModel) {
        onDispose {
            viewModel.saveFinalProgress(player.currentPosition, player.duration)
            player.release()
        }
    }

    val saveAndBack = {
        viewModel.saveFinalProgress(player.currentPosition, player.duration)
        onBack()
    }
    BackHandler(onBack = saveAndBack)

    fun showControls() {
        controlsVisible = true
        controlsTick++
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        showControls()
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.coerceAtLeast(0L)
        val target = (player.currentPosition + deltaMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        showControls()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.PlayerRoot)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    -> {
                        if (!controlsVisible) {
                            showControls()
                            true
                        } else {
                            // Let the focused control button handle OK / Enter.
                            false
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        togglePlayPause()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        showControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
                        showControls()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    -> {
                        // D-pad L/R navigate focus among control buttons — never seek.
                        if (!controlsVisible) {
                            showControls()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    -> {
                        seekBy(-seekStepForRepeat(event.nativeKeyEvent.repeatCount))
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    -> {
                        seekBy(seekStepForRepeat(event.nativeKeyEvent.repeatCount))
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    -> {
                        if (!controlsVisible) {
                            showControls()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_CAPTIONS -> {
                        if (streamInfo.subtitles.isNotEmpty()) {
                            subtitlesEnabled = !subtitlesEnabled
                            showControls()
                        }
                        true
                    }
                    else -> {
                        showControls()
                        false
                    }
                }
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    subtitleView?.apply {
                        setApplyEmbeddedFontSizes(false)
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                        setBottomPaddingFraction(0.18f)
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null,
                            ),
                        )
                    }
                    this.player = player
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControlsOverlay(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                hasSubtitles = streamInfo.subtitles.isNotEmpty(),
                subtitlesEnabled = subtitlesEnabled,
                playFocusRequester = playFocusRequester,
                onPlayPause = { togglePlayPause() },
                onSeekBy = { deltaMs -> seekBy(deltaMs) },
                onToggleSubtitles = {
                    subtitlesEnabled = !subtitlesEnabled
                    showControls()
                },
            )
        }

        if (playbackFailed) {
            PlayerError(LocalStrings.current.playbackFailed, saveAndBack)
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasSubtitles: Boolean,
    subtitlesEnabled: Boolean,
    playFocusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleSubtitles: () -> Unit,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to ControlsScrim,
                ),
            )
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(Color(0x55FFFFFF), RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(5.dp)
                    .background(Accent, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularControlButton(
                iconRes = R.drawable.ic_seek_back,
                label = "10",
                contentDescription = "-10s",
                progressiveSeekDirection = -1,
                onSeekBy = onSeekBy,
            )
            CircularControlButton(
                iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                emphasized = true,
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playFocusRequester),
            )
            CircularControlButton(
                iconRes = R.drawable.ic_seek_forward,
                label = "10",
                contentDescription = "+10s",
                progressiveSeekDirection = 1,
                onSeekBy = onSeekBy,
            )
            if (hasSubtitles) {
                CircularControlButton(
                    iconRes = R.drawable.ic_cc,
                    contentDescription = if (subtitlesEnabled) "CC On" else "CC Off",
                    dimmed = !subtitlesEnabled,
                    onClick = onToggleSubtitles,
                )
            }
        }
        Text(
            text = "Hold ◀ ▶ to scrub faster",
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CircularControlButton(
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    emphasized: Boolean = false,
    dimmed: Boolean = false,
    progressiveSeekDirection: Int = 0,
    onSeekBy: ((Long) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val size = if (emphasized) 64.dp else 52.dp
    val iconSize = if (emphasized) 30.dp else 24.dp
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val latestOnSeekBy = rememberUpdatedState(onSeekBy)
    var acceleratedSeek by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed, progressiveSeekDirection) {
        if (!isPressed || progressiveSeekDirection == 0) return@LaunchedEffect
        val seek = latestOnSeekBy.value ?: return@LaunchedEffect
        // Immediate ±10s on press (covers short taps). Further ticks double while held.
        seek(SeekStepMs * progressiveSeekDirection)
        acceleratedSeek = true
        delay(SeekHoldInitialDelayMs)
        var stepIndex = 1
        var tickDelayMs = 300L
        while (isActive) {
            seek(SeekAccelerationStepsMs[stepIndex] * progressiveSeekDirection)
            if (stepIndex < SeekAccelerationStepsMs.lastIndex) {
                stepIndex++
            }
            delay(tickDelayMs)
            tickDelayMs = (tickDelayMs * 3 / 4).coerceAtLeast(SeekHoldMinDelayMs)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Surface(
            onClick = {
                when {
                    progressiveSeekDirection != 0 -> {
                        val seek = latestOnSeekBy.value
                        if (seek != null && !acceleratedSeek) {
                            seek(SeekStepMs * progressiveSeekDirection)
                        }
                        acceleratedSeek = false
                    }
                    onClick != null -> onClick()
                }
            },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (emphasized) Accent else Color(0xFF1B2230),
                focusedContainerColor = if (emphasized) Accent else Color(0xFF2A3344),
                pressedContainerColor = Accent.copy(alpha = 0.85f),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            interactionSource = interactionSource,
            modifier = Modifier.size(size),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    colorFilter = ColorFilter.tint(
                        if (dimmed) Color.White.copy(alpha = 0.45f) else Color.White,
                    ),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
        if (label != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun seekStepForRepeat(repeatCount: Int): Long {
    // Mirror hold acceleration for MEDIA_REWIND / MEDIA_FAST_FORWARD key repeats.
    val index = when {
        repeatCount < 2 -> 0
        repeatCount < 5 -> 1
        repeatCount < 9 -> 2
        repeatCount < 14 -> 3
        repeatCount < 20 -> 4
        else -> SeekAccelerationStepsMs.lastIndex
    }
    return SeekAccelerationStepsMs[index]
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun buildMediaItem(url: String, subtitles: List<SubtitleTrack>): MediaItem {
    val subtitleConfigs = subtitles.map { track ->
        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.url))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(track.language)
            .setLabel(track.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .build()
    }
    return MediaItem.Builder()
        .setUri(url)
        .setMimeType(MimeTypes.VIDEO_MP4)
        .setSubtitleConfigurations(subtitleConfigs)
        .build()
}

private fun setSubtitlesEnabled(
    player: ExoPlayer,
    preferred: List<SubtitleTrack>,
    enabled: Boolean,
) {
    if (!enabled || preferred.isEmpty()) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        return
    }
    val tracks = player.currentTracks
    val textGroup = tracks.groups.firstOrNull { group ->
        group.type == C.TRACK_TYPE_TEXT && group.length > 0
    } ?: return
    val preferredLanguages = preferred.map { it.language }.toSet()
    val index = (0 until textGroup.length).firstOrNull { i ->
        val format = textGroup.getTrackFormat(i)
        format.language != null && preferredLanguages.contains(format.language)
    } ?: 0
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(TrackSelectionOverride(textGroup.mediaTrackGroup, index))
        .build()
}

@Composable
private fun PlayerLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocalStrings.current.loadingPlayer,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerError(
    message: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerBackground.copy(alpha = 0.94f))
            .padding(horizontal = 72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1B2230),
                focusedContainerColor = Accent,
                pressedContainerColor = Accent.copy(alpha = 0.85f),
            ),
        ) {
            Text(
                text = LocalStrings.current.back,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}
