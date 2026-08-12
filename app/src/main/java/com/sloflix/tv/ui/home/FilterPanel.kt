package com.sloflix.tv.ui.home

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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sloflix.tv.domain.model.FilterState

private val PanelBackground = Color(0xFF141923)
private val FieldBackground = Color(0xFF090C12)
private val Accent = Color(0xFFE52B3D)
private val SecondaryText = Color(0xFFC5CBD6)

@Composable
fun FilterPanel(
    filter: FilterState,
    onQueryChanged: (String) -> Unit,
    onGenreToggle: (String) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onTypeSelected: (Int?) -> Unit,
    onSortSelected: (Int?) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeFocus.requestFocus()
    }

    LazyColumn(
        modifier = modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(PanelBackground)
            .padding(horizontal = 32.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(closeFocus),
            ) {
                Text("Close")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear filters")
            }
            Spacer(Modifier.height(24.dp))
            FilterQueryField(
                value = filter.query.orEmpty(),
                onValueChange = onQueryChanged,
            )
        }

        filter.availableTypes.takeIf { it.isNotEmpty() }?.let { types ->
            item { FilterHeading("Type") }
            item {
                FilterChoice(
                    label = "All types",
                    selected = filter.selectedType == null,
                    onClick = { onTypeSelected(null) },
                )
            }
            items(types, key = { it.first }) { (id, label) ->
                FilterChoice(
                    label = label,
                    selected = filter.selectedType == id,
                    onClick = { onTypeSelected(id) },
                )
            }
        }

        filter.availableGenres.takeIf { it.isNotEmpty() }?.let { genres ->
            item { FilterHeading("Genres") }
            items(genres, key = { it.first }) { (id, label) ->
                FilterChoice(
                    label = label,
                    selected = id in filter.selectedGenreIds,
                    onClick = { onGenreToggle(id) },
                )
            }
        }

        filter.availableYears.takeIf { it.isNotEmpty() }?.let { years ->
            item { FilterHeading("Year") }
            item {
                FilterChoice(
                    label = "Any year",
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
            item { FilterHeading("Sort by") }
            item {
                FilterChoice(
                    label = "Default",
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
private fun FilterQueryField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Column {
        Text(
            text = "Search",
            style = MaterialTheme.typography.titleMedium,
            color = SecondaryText,
        )
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .background(FieldBackground, RoundedCornerShape(8.dp))
                .border(
                    width = 2.dp,
                    color = if (isFocused) Accent else Color(0xFF596274),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Title name",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF8E98AA),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
