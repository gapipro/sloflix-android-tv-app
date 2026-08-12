package com.sloflix.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.ui.components.PosterCard
import com.sloflix.tv.ui.components.UiState

private val Background = Color(0xFF090C12)
private val Accent = Color(0xFFE52B3D)
private val SecondaryText = Color(0xFFC5CBD6)

@Composable
fun HomeScreen(
    state: UiState<HomeContent>,
    filter: FilterState,
    onRetry: () -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
    onQueryChanged: (String) -> Unit,
    onGenreToggle: (String) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onTypeSelected: (Int?) -> Unit,
    onSortSelected: (Int?) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        when (state) {
            UiState.Loading -> LoadingContent()
            is UiState.Error -> ErrorContent(
                message = state.message,
                onRetry = onRetry,
            )
            is UiState.Ready -> CatalogContent(
                content = state.value,
                filter = filter,
                onOpenFilters = { filtersOpen = true },
                onClearFilters = onClearFilters,
                onTitleClick = onTitleClick,
            )
        }

        if (filtersOpen) {
            FilterPanel(
                filter = filter,
                onQueryChanged = onQueryChanged,
                onGenreToggle = onGenreToggle,
                onYearSelected = onYearSelected,
                onTypeSelected = onTypeSelected,
                onSortSelected = onSortSelected,
                onClear = onClearFilters,
                onClose = { filtersOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SLOFLIX",
            color = Accent,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Loading your library…",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun ErrorContent(
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
            text = "Your library couldn’t be loaded",
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
private fun CatalogContent(
    content: HomeContent,
    filter: FilterState,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
) {
    val firstFocusableRow = content.rows.indexOfFirst { it.titles.isNotEmpty() }
    val firstPosterFocus = remember { FocusRequester() }

    if (firstFocusableRow >= 0) {
        LaunchedEffect(content) {
            firstPosterFocus.requestFocus()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 42.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 56.dp)) {
                Text(
                    text = "SLOFLIX",
                    color = Accent,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "What will you watch?",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onOpenFilters) {
                    Text("Filters")
                }
            }
        }

        if (firstFocusableRow < 0) {
            item {
                Column(modifier = Modifier.padding(horizontal = 56.dp)) {
                    Text(
                        text = if (filter.hasActiveFilters()) {
                            "No titles match"
                        } else {
                            "There’s nothing here yet."
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = SecondaryText,
                    )
                    if (filter.hasActiveFilters()) {
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onClearFilters) {
                            Text("Clear filters")
                        }
                    }
                }
            }
        } else {
            itemsIndexed(
                items = content.rows,
                key = { _, row -> row.title },
            ) { rowIndex, row ->
                if (row.titles.isNotEmpty()) {
                    CategoryRow(
                        row = row,
                        onTitleClick = onTitleClick,
                        firstPosterFocus = if (rowIndex == firstFocusableRow) {
                            firstPosterFocus
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

private fun FilterState.hasActiveFilters() =
    selectedGenreIds.isNotEmpty() ||
        selectedYear != null ||
        !query.isNullOrBlank() ||
        selectedType != null ||
        sortBy != null

@Composable
private fun CategoryRow(
    row: HomeRow,
    onTitleClick: (TitleSummary) -> Unit,
    firstPosterFocus: FocusRequester?,
) {
    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items = row.titles,
                key = { _, title -> title.id },
            ) { index, title ->
                PosterCard(
                    title = title,
                    onClick = { onTitleClick(title) },
                    modifier = if (index == 0 && firstPosterFocus != null) {
                        Modifier.focusRequester(firstPosterFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
