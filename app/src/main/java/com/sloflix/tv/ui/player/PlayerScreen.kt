package com.sloflix.tv.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sloflix.tv.domain.model.StreamInfo
import okhttp3.OkHttpClient

private val PlayerBackground = Color(0xFF090C12)

@Composable
fun PlayerScreen(
    titleId: String,
    startPositionMs: Long,
    viewModel: PlayerViewModel,
    mediaOkHttpClient: OkHttpClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, titleId) {
        viewModel.load(titleId)
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
    val context = LocalContext.current
    val currentUrl = candidates[candidateIndex]
    val player = remember(currentUrl, startPositionMs, mediaOkHttpClient) {
        // Media is served by third-party CDNs, so downloads use the credential-free client. The HLS
        // and DASH extensions are picked up by DefaultMediaSourceFactory from the classpath.
        val dataSourceFactory = OkHttpDataSource.Factory(mediaOkHttpClient).apply {
            if (streamInfo.headers.isNotEmpty()) {
                setDefaultRequestProperties(streamInfo.headers)
            }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
                    },
                )
                setMediaItem(MediaItem.fromUri(currentUrl))
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

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = true
                this.player = player
            }
        },
        update = { view -> view.player = player },
        modifier = Modifier.fillMaxSize(),
    )

    if (playbackFailed) {
        PlayerError("Can’t play this title", saveAndBack)
    }
}

@Composable
private fun PlayerLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Loading player…",
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
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
