package com.sloflix.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.ui.i18n.LocalStrings

private val CardShape = RoundedCornerShape(10.dp)
private val CardBackground = Color(0xFF171C25)
private val FocusAccent = Color(0xFFE50913)
private val SecondaryText = Color(0xFFC5CBD6)

/** Unscaled card body size. */
val PosterCardWidth: Dp = 152.dp
val PosterCardBodyHeight: Dp = 252.dp
private val PosterImageHeight = 214.dp

/**
 * Focus zoom applied only to the *visual* layer. Layout/focus stay on a fixed slot so
 * nested LazyColumn bring-into-view does not bounce when moving focus horizontally.
 */
const val PosterFocusScale = 1.06f

val PosterCardSlotWidth: Dp = PosterCardWidth * PosterFocusScale
val PosterCardSlotHeight: Dp = PosterCardBodyHeight * PosterFocusScale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: TitleSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) PosterFocusScale else 1f,
        label = "poster focus scale",
    )
    // Focus + click live on the fixed slot. Scaled visuals are not focus targets, so
    // reported focused bounds never change size during the zoom animation.
    Box(
        modifier = modifier
            .width(PosterCardSlotWidth)
            .height(PosterCardSlotHeight)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(PosterCardWidth)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
                .clip(CardShape)
                .background(CardBackground)
                .border(
                    width = 2.dp,
                    color = if (isFocused) FocusAccent else Color(0xFF303744),
                    shape = CardShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PosterImageHeight)
                    .background(Color(0xFF252C38)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFF737D8E),
                )
                if (title.posterUrl != null) {
                    AsyncImage(
                        model = title.posterUrl,
                        contentDescription = "${title.name} poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (title.isNew) {
                    Text(
                        text = LocalStrings.current.novo,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(FocusAccent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                title.progressFraction?.coerceIn(0f, 1f)?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color(0x99090C12)),
                    ) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(5.dp)
                                .background(FocusAccent),
                        )
                    }
                }
            }
            Text(
                text = title.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) Color.White else SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
    }
}
