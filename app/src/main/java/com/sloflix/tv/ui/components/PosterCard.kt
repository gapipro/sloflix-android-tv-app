package com.sloflix.tv.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sloflix.tv.domain.model.TitleSummary
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(10.dp)
private val CardBackground = Color(0xFF171C25)
private val FocusAccent = Color(0xFFE52B3D)
private val SecondaryText = Color(0xFFC5CBD6)

@Composable
fun PosterCard(
    title: TitleSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "poster focus scale",
    )
    val posterBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = title.posterUrl,
    ) {
        value = title.posterUrl?.let { url ->
            withContext(Dispatchers.IO) {
                runCatching {
                    URL(url).openStream().use(BitmapFactory::decodeStream).asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Column(
        modifier = modifier
            .width(152.dp)
            .scale(scale)
            .clip(CardShape)
            .background(CardBackground)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusAccent else Color(0xFF303744),
                shape = CardShape,
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(214.dp)
                .background(Color(0xFF252C38)),
            contentAlignment = Alignment.Center,
        ) {
            if (posterBitmap != null) {
                Image(
                    bitmap = posterBitmap!!,
                    contentDescription = "${title.name} poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(
                    text = title.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFF737D8E),
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
