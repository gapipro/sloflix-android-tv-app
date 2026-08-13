package com.sloflix.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sloflix.tv.domain.settings.AppLanguage
import com.sloflix.tv.ui.TestTags
import com.sloflix.tv.ui.i18n.LocalStrings

private val PanelBackground = Color(0xFF141923)
private val SecondaryText = Color(0xFFC5CBD6)

@Composable
fun SettingsPanel(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeFocus.requestFocus()
    }

    LazyColumn(
        modifier = modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(PanelBackground)
            .padding(horizontal = 32.dp)
            .testTag(TestTags.SettingsPanel),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(closeFocus)
                    .testTag(TestTags.SettingsClose),
            ) {
                Text(strings.close)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = strings.languageLabel,
                style = MaterialTheme.typography.titleMedium,
                color = SecondaryText,
            )
            Spacer(Modifier.height(10.dp))
        }
        items(AppLanguage.entries.toList(), key = { it }) { language ->
            val selected = language == selectedLanguage
            val languageTag = when (language) {
                AppLanguage.Slovenian -> TestTags.SettingsLanguageSl
                AppLanguage.English -> TestTags.SettingsLanguageEn
            }
            Button(
                onClick = { onLanguageSelected(language) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(languageTag),
            ) {
                Text(if (selected) "✓  ${language.displayName}" else language.displayName)
            }
        }
    }
}
