package com.sloflix.tv.ui.details

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sloflix.tv.ui.components.UiState
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Background = Color(0xFF090C12)
private val Accent = Color(0xFFE52B3D)
private val SecondaryText = Color(0xFFC5CBD6)

@Composable
fun DetailsScreen(
    state: UiState<DetailsContent>,
    onRetry: () -> Unit,
    onPlay: (titleId: String, startPositionMs: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        when (state) {
            UiState.Loading -> DetailsLoading()
            is UiState.Error -> DetailsError(state.message, onRetry)
            is UiState.Ready -> DetailsReady(state.value, onPlay)
        }
    }
}

@Composable
private fun DetailsReady(
    content: DetailsContent,
    onPlay: (titleId: String, startPositionMs: Long?) -> Unit,
) {
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(content.title.id) {
        primaryFocus.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RemoteImage(
            url = content.title.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Background,
                        0.58f to Background.copy(alpha = 0.88f),
                        1f to Background.copy(alpha = 0.34f),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Background,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(
                url = content.title.posterUrl,
                title = content.title.name,
            )
            Spacer(Modifier.width(42.dp))
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "SLOFLIX",
                    color = Accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = content.title.name,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                val metadata = buildList {
                    content.title.year?.let { add(it.toString()) }
                    addAll(content.title.genres)
                }.joinToString("  •  ")
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.titleMedium,
                        color = SecondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(22.dp))
                }
                Text(
                    text = content.title.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    lineHeight = 25.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.82f),
                )
                Spacer(Modifier.height(30.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = {
                            onPlay(
                                content.title.id,
                                content.resumePositionMs.takeIf { content.canResume },
                            )
                        },
                        modifier = Modifier.focusRequester(primaryFocus),
                    ) {
                        Text(if (content.canResume) "Resume" else "Play")
                    }
                    if (content.canResume) {
                        Button(onClick = { onPlay(content.title.id, null) }) {
                            Text("Play from beginning")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(
    url: String?,
    title: String,
) {
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 326.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF252C38)),
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            url = url,
            contentDescription = "$title poster",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (url == null) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFF737D8E),
            )
        }
    }
}

@Composable
private fun DetailsLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SLOFLIX", color = Accent, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Loading title details…",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun DetailsError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "This title couldn’t be loaded",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = SecondaryText,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun RemoteImage(
    url: String?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = url,
    ) {
        value = url?.let { imageUrl ->
            withContext(Dispatchers.IO) {
                runCatching {
                    URL(imageUrl).openStream().use(BitmapFactory::decodeStream).asImageBitmap()
                }.getOrNull()
            }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
