package com.sloflix.tv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sloflix.tv.R
import com.sloflix.tv.ui.TestTags
import com.sloflix.tv.ui.i18n.LocalStrings

private val PanelBackground = Color(0xFF141923)
private val Accent = Color(0xFFE50913)

@Composable
fun ProfileAvatarButton(
    username: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A3344),
            focusedContainerColor = Accent,
            pressedContainerColor = Accent.copy(alpha = 0.85f),
        ),
        modifier = modifier.size(48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_profile),
                contentDescription = username.ifBlank { LocalStrings.current.username },
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
fun ProfileMenu(
    username: String,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val settingsFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        settingsFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .width(320.dp)
            .background(PanelBackground, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2A3344), RoundedCornerShape(12.dp))
            .padding(20.dp)
            .testTag(TestTags.ProfileMenu),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (username.isNotBlank()) {
            Text(
                text = username,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(TestTags.ProfileUsername),
            )
            Spacer(Modifier.height(8.dp))
        }
        MenuButton(
            label = strings.settings,
            onClick = onSettings,
            leadingIcon = {
                Image(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(20.dp),
                )
            },
            modifier = Modifier
                .focusRequester(settingsFocus)
                .testTag(TestTags.ProfileSettings),
        )
        MenuButton(
            label = strings.signOut,
            onClick = onSignOut,
            modifier = Modifier.testTag(TestTags.ProfileSignOut),
        )
        MenuButton(label = strings.close, onClick = onClose)
    }
}

@Composable
private fun MenuButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1B2230),
            focusedContainerColor = Accent,
            pressedContainerColor = Accent.copy(alpha = 0.85f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
