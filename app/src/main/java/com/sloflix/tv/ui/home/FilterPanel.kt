package com.sloflix.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.ui.TestTags
import com.sloflix.tv.ui.components.forceShowSoftKeyboard
import com.sloflix.tv.ui.components.hideSoftKeyboard
import com.sloflix.tv.ui.i18n.LocalStrings

private val PanelBackground = Color(0xFF141923)
private val FieldBackground = Color(0xFF090C12)
private val Accent = Color(0xFFE50913)
private val SecondaryText = Color(0xFFC5CBD6)

@Composable
fun FilterPanel(
    filter: FilterState,
    onGenreToggle: (String) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onTypeSelected: (Int?) -> Unit,
    onSortSelected: (Int?) -> Unit,
    onClear: () -> Unit,
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
            .testTag(TestTags.FilterPanel),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = strings.filters,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(closeFocus)
                    .testTag(TestTags.FilterClose),
            ) {
                Text(strings.close)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.clearFilters)
            }
        }

        filter.availableTypes.takeIf { it.isNotEmpty() }?.let { types ->
            item { FilterHeading(strings.type) }
            item {
                FilterChoice(
                    label = strings.allTypes,
                    selected = filter.selectedType == null,
                    onClick = { onTypeSelected(null) },
                )
            }
            items(types, key = { it.first }) { (id, label) ->
                val localized = when (id) {
                    1 -> strings.movies
                    2 -> strings.series
                    else -> label
                }
                FilterChoice(
                    label = localized,
                    selected = filter.selectedType == id,
                    onClick = { onTypeSelected(id) },
                )
            }
        }

        filter.availableGenres.takeIf { it.isNotEmpty() }?.let { genres ->
            item { FilterHeading(strings.genres) }
            items(genres, key = { it.first }) { (id, label) ->
                FilterChoice(
                    label = label,
                    selected = id in filter.selectedGenreIds,
                    onClick = { onGenreToggle(id) },
                )
            }
        }

        filter.availableYears.takeIf { it.isNotEmpty() }?.let { years ->
            item { FilterHeading(strings.year) }
            item {
                FilterChoice(
                    label = strings.anyYear,
                    selected = filter.selectedYear == null,
                    onClick = { onYearSelected(null) },
                )
            }
            items(years, key = { it }) { year ->
                FilterChoice(
                    label = year.toString(),
                    selected = filter.selectedYear == year,
                    onClick = { onYearSelected(year) },
                )
            }
        }

        filter.availableSorts.takeIf { it.isNotEmpty() }?.let { sorts ->
            item { FilterHeading(strings.sortBy) }
            item {
                FilterChoice(
                    label = strings.defaultSort,
                    selected = filter.sortBy == null,
                    onClick = { onSortSelected(null) },
                )
            }
            items(sorts, key = { it.first }) { (id, label) ->
                FilterChoice(
                    label = label,
                    selected = filter.sortBy == id,
                    onClick = { onSortSelected(id) },
                )
            }
        }
    }
}

@Composable
private fun FilterHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = SecondaryText,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun FilterChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (selected) "✓  $label" else label)
    }
}

@Composable
fun HomeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // D-pad focus alone must not open the IME. Edit only after OK/click.
    var isEditing by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var textHadFocus by remember { mutableStateOf(false) }
    var restoreShellFocus by remember { mutableStateOf(false) }
    val textFocus = remember { FocusRequester() }
    val shellFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val placeholder = strings.searchTitles

    fun handleBack(): Boolean {
        return when {
            isEditing -> {
                restoreShellFocus = true
                isEditing = false
                hideSoftKeyboard(view, keyboard)
                true
            }
            isFocused -> {
                focusManager.clearFocus()
                true
            }
            else -> false
        }
    }

    BackHandler(enabled = isEditing || isFocused) {
        handleBack()
    }

    val backKeyModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            handleBack()
        } else {
            false
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            textHadFocus = false
            forceShowSoftKeyboard(view, textFocus, keyboard)
        } else {
            textHadFocus = false
            hideSoftKeyboard(view, keyboard)
            if (restoreShellFocus) {
                shellFocus.requestFocus()
                restoreShellFocus = false
            }
        }
    }

    if (isEditing) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(Accent),
            modifier = modifier
                .then(backKeyModifier)
                .focusRequester(textFocus)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        textHadFocus = true
                    } else if (textHadFocus) {
                        isEditing = false
                    }
                }
                .background(FieldBackground, RoundedCornerShape(8.dp))
                .border(
                    width = 2.dp,
                    color = Accent,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF8E98AA),
                        )
                    }
                    innerTextField()
                }
            },
        )
    } else {
        val fieldShape = RoundedCornerShape(8.dp)
        val idleBorder = Border(BorderStroke(2.dp, Color(0xFF596274)), shape = fieldShape)
        val accentBorder = Border(BorderStroke(2.dp, Accent), shape = fieldShape)
        Surface(
            onClick = { isEditing = true },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                border = idleBorder,
                focusedBorder = accentBorder,
                pressedBorder = accentBorder,
            ),
            shape = ClickableSurfaceDefaults.shape(shape = fieldShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = FieldBackground,
                focusedContainerColor = FieldBackground,
                pressedContainerColor = FieldBackground,
            ),
            modifier = modifier
                .then(backKeyModifier)
                .focusRequester(shellFocus)
                .onFocusChanged { isFocused = it.isFocused },
        ) {
            Text(
                text = value.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) Color(0xFF8E98AA) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
