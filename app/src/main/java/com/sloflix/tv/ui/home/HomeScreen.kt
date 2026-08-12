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
import androidx.compose.runtime.remember
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
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.ui.components.PosterCard
import com.sloflix.tv.ui.components.UiState

private val Background = Color(0xFF090C12)
private val Accent = Color(0xFFE52B3D)
private val SecondaryText = Color(0xFFC5CBD6)

@Composable
fun HomeScreen(
    state: UiState<HomeContent>,
    onRetry: () -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                onTitleClick = onTitleClick,
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
            }
        }

        if (firstFocusableRow < 0) {
            item {
                Text(
                    text = "There’s nothing here yet.",
                    style = MaterialTheme.typography.titleLarge,
                    color = SecondaryText,
                    modifier = Modifier.padding(horizontal = 56.dp),
                )
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
