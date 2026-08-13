package com.sloflix.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.sloflix.tv.R

@Composable
fun SloflixLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = 28.dp,
    textSize: TextUnit = 28.sp,
    color: Color = Color.White,
    showMark: Boolean = true,
    showWordmark: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showMark) {
            Image(
                painter = painterResource(R.drawable.ic_sloflix_s),
                contentDescription = null,
                modifier = Modifier.size(markSize),
                colorFilter = if (color == Color.White) null else ColorFilter.tint(color),
            )
        }
        if (showWordmark) {
            Text(
                text = "Sloflix",
                color = color,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SloflixSplash(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_sloflix_s),
            contentDescription = "Sloflix",
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = "Sloflix",
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
