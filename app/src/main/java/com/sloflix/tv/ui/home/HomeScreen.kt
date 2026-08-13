package com.sloflix.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.settings.AppLanguage
import com.sloflix.tv.ui.TestTags
import com.sloflix.tv.ui.components.PosterCard
import com.sloflix.tv.ui.components.PosterCardSlotHeight
import com.sloflix.tv.ui.components.SloflixLogo
import com.sloflix.tv.ui.components.UiState
import com.sloflix.tv.ui.i18n.LocalStrings
import com.sloflix.tv.ui.i18n.SloflixStrings
import com.sloflix.tv.ui.settings.SettingsPanel

private val Background = Color(0xFF090C12)
private val Accent = Color(0xFFE50913)
private val SecondaryText = Color(0xFFC5CBD6)
private val ChipIdle = Color(0xFF1B2230)

@Composable
fun HomeScreen(
    state: UiState<HomeContent>,
    filter: FilterState,
    focusedTitleId: String?,
    username: String,
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onRetry: () -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
    onRemoveContinueWatching: (TitleSummary) -> Unit,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onGenreToggle: (String) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onTypeSelected: (Int?) -> Unit,
    onSortSelected: (Int?) -> Unit,
    onClearFilters: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var removeCandidate by remember { mutableStateOf<TitleSummary?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .testTag(TestTags.HomeRoot),
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
                focusedTitleId = focusedTitleId,
                username = username,
                filtersOpen = filtersOpen,
                onOpenFilters = { filtersOpen = true },
                onOpenProfile = { profileOpen = true },
                onClearFilters = onClearFilters,
                onQueryChanged = onQueryChanged,
                onCategorySelected = onCategorySelected,
                onTypeSelected = onTypeSelected,
                onTitleClick = onTitleClick,
                onRemoveContinueWatchingRequest = { removeCandidate = it },
            )
        }

        if (filtersOpen) {
            BackHandler { filtersOpen = false }
            FilterPanel(
                filter = filter,
                onGenreToggle = onGenreToggle,
                onYearSelected = onYearSelected,
                onTypeSelected = onTypeSelected,
                onSortSelected = onSortSelected,
                onClear = onClearFilters,
                onClose = { filtersOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (profileOpen) {
            BackHandler { profileOpen = false }
            ProfileMenu(
                username = username,
                onSettings = {
                    profileOpen = false
                    settingsOpen = true
                },
                onSignOut = onSignOut,
                onClose = { profileOpen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp, end = 56.dp),
            )
        }

        if (settingsOpen) {
            BackHandler { settingsOpen = false }
            SettingsPanel(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected,
                onClose = { settingsOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        removeCandidate?.let { title ->
            BackHandler { removeCandidate = null }
            RemoveContinueWatchingDialog(
                titleName = title.name,
                onConfirm = {
                    onRemoveContinueWatching(title)
                    removeCandidate = null
                },
                onDismiss = { removeCandidate = null },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

fun displayRowTitle(title: String, strings: SloflixStrings): String = when (title) {
    HomeRowKeys.ContinueWatching -> strings.continueWatching
    HomeRowKeys.SearchResults -> strings.searchResults
    HomeRowKeys.Results -> strings.results
    else -> title
}

@Composable
private fun LoadingContent() {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SloflixLogo(markSize = 28.dp, textSize = 24.sp, showMark = false)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = strings.loadingLibrary,
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
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.unableToLoadLibrary,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = SecondaryText,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(strings.retry)
        }
    }
}

@Composable
private fun CatalogContent(
    content: HomeContent,
    filter: FilterState,
    focusedTitleId: String?,
    username: String,
    filtersOpen: Boolean,
    onOpenFilters: () -> Unit,
    onOpenProfile: () -> Unit,
    onClearFilters: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onTypeSelected: (Int?) -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
    onRemoveContinueWatchingRequest: (TitleSummary) -> Unit,
) {
    val strings = LocalStrings.current
    val firstFocusableRow = content.rows.indexOfFirst { it.titles.isNotEmpty() }
    val restoreTarget = remember(content.rows, focusedTitleId) {
        focusedTitleId?.let { id ->
            content.rows.mapIndexedNotNull { rowIndex, row ->
                val titleIndex = row.titles.indexOfFirst { it.id == id }
                if (titleIndex >= 0) RestoreTarget(rowIndex, titleIndex, id) else null
            }.firstOrNull()
        }
    }
    val posterFocus = remember { FocusRequester() }
    val filmiChipFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var initialFocusDone by remember { mutableStateOf(false) }
    // Header + chips precede catalog rows in the LazyColumn.
    val rowListOffset = 2
    val availableTypes = filter.availableTypes.ifEmpty {
        listOf(1 to strings.movies, 2 to strings.series)
    }.map { (id, label) ->
        id to when (id) {
            1 -> strings.movies
            2 -> strings.series
            else -> label
        }
    }
    val filmiTypeId = availableTypes.firstOrNull { it.first == 1 }?.first
        ?: availableTypes.firstOrNull()?.first

    if (!filtersOpen) {
        LaunchedEffect(restoreTarget?.titleId, availableTypes.map { it.first }) {
            when {
                restoreTarget != null && firstFocusableRow >= 0 -> {
                    listState.scrollToItem(rowListOffset + restoreTarget.rowIndex)
                    kotlinx.coroutines.android.awaitFrame()
                    kotlinx.coroutines.android.awaitFrame()
                    runCatching { posterFocus.requestFocus() }
                    initialFocusDone = true
                }
                !initialFocusDone && filmiTypeId != null -> {
                    kotlinx.coroutines.android.awaitFrame()
                    kotlinx.coroutines.android.awaitFrame()
                    runCatching { filmiChipFocus.requestFocus() }
                    initialFocusDone = true
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 42.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SloflixLogo(markSize = 26.dp, textSize = 22.sp, showMark = false)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.whatWillYouWatch,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )
                }
                HomeSearchField(
                    value = filter.query.orEmpty(),
                    onValueChange = onQueryChanged,
                    modifier = Modifier
                        .width(280.dp)
                        .testTag(TestTags.HomeSearch),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onOpenFilters,
                    modifier = Modifier.testTag(TestTags.HomeFilters),
                ) {
                    Text(strings.filters)
                }
                Spacer(modifier = Modifier.width(12.dp))
                ProfileAvatarButton(
                    username = username,
                    onClick = onOpenProfile,
                    modifier = Modifier.testTag(TestTags.HomeProfile),
                )
            }
        }

        item {
            CategoryChips(
                categories = content.categories,
                selectedCategoryId = content.selectedCategoryId,
                selectedType = filter.selectedType,
                availableTypes = availableTypes,
                allLabel = strings.all,
                initialTypeFocusId = filmiTypeId,
                initialTypeFocus = filmiChipFocus,
                onTypeSelected = onTypeSelected,
                onCategorySelected = onCategorySelected,
            )
        }

        if (firstFocusableRow < 0) {
            item {
                Column(modifier = Modifier.padding(horizontal = 56.dp)) {
                    Text(
                        text = if (filter.hasActiveFilters()) {
                            strings.noTitlesMatch
                        } else {
                            strings.nothingHereYet
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = SecondaryText,
                    )
                    if (filter.hasActiveFilters()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(onClick = onClearFilters) {
                            Text(strings.clearFilters)
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
                    val restoreInRow = restoreTarget?.takeIf { it.rowIndex == rowIndex }
                    CategoryRow(
                        row = row,
                        onTitleClick = onTitleClick,
                        onLongClickTitle = if (row.title == HomeRowKeys.ContinueWatching) {
                            onRemoveContinueWatchingRequest
                        } else {
                            null
                        },
                        restoreTitleIndex = restoreInRow?.titleIndex,
                        posterFocus = if (restoreInRow != null) posterFocus else null,
                    )
                }
            }
        }
    }
}

private data class RestoreTarget(
    val rowIndex: Int,
    val titleIndex: Int,
    val titleId: String,
)

@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedCategoryId: String?,
    selectedType: Int?,
    availableTypes: List<Pair<Int, String>>,
    allLabel: String,
    initialTypeFocusId: Int?,
    initialTypeFocus: FocusRequester,
    onTypeSelected: (Int?) -> Unit,
    onCategorySelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Web order: Filmi / Serije first, then genres.
        items(availableTypes, key = { "type-${it.first}" }) { (id, label) ->
            CategoryChip(
                label = label,
                selected = selectedType == id,
                onClick = { onTypeSelected(id) },
                modifier = Modifier
                    .then(
                        when (id) {
                            1 -> Modifier.testTag(TestTags.HomeFilmi)
                            2 -> Modifier.testTag(TestTags.HomeSerije)
                            else -> Modifier
                        },
                    )
                    .then(
                        if (id == initialTypeFocusId) {
                            Modifier.focusRequester(initialTypeFocus)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        item(key = "type-genre-divider") {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(Color(0xFF596274)),
                )
            }
        }
        item {
            CategoryChip(
                label = allLabel,
                selected = selectedCategoryId == null,
                onClick = { onCategorySelected(null) },
                modifier = Modifier.testTag(TestTags.HomeVse),
            )
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                label = category.name,
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelected(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent else ChipIdle,
            focusedContainerColor = if (selected) Accent else Color(0xFF2A3344),
            pressedContainerColor = Accent.copy(alpha = 0.85f),
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

private fun FilterState.hasActiveFilters() =
    selectedGenreIds.isNotEmpty() ||
        selectedYear != null ||
        !query.isNullOrBlank() ||
        selectedType != null ||
        (sortBy != null && sortBy != 1)

@Composable
private fun CategoryRow(
    row: HomeRow,
    onTitleClick: (TitleSummary) -> Unit,
    onLongClickTitle: ((TitleSummary) -> Unit)?,
    restoreTitleIndex: Int?,
    posterFocus: FocusRequester?,
) {
    val strings = LocalStrings.current
    val rowState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoreTitleIndex?.coerceAtLeast(0) ?: 0,
    )

    LaunchedEffect(restoreTitleIndex) {
        val index = restoreTitleIndex ?: return@LaunchedEffect
        if (index > 0) {
            rowState.scrollToItem(index)
        }
    }

    Column(
        modifier = if (row.title == HomeRowKeys.ContinueWatching) {
            Modifier.testTag(TestTags.HomeContinueWatching)
        } else {
            Modifier
        },
    ) {
        Text(
            text = displayRowTitle(row.title, strings),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Fixed height + focusGroup: horizontal focus stays in-row and does not
        // re-trigger LazyColumn bring-into-view vertically.
        LazyRow(
            state = rowState,
            modifier = Modifier
                .height(PosterCardSlotHeight)
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                items = row.titles,
                key = { _, title -> title.id },
            ) { index, title ->
                PosterCard(
                    title = title,
                    onClick = { onTitleClick(title) },
                    onLongClick = onLongClickTitle?.let { handler -> { handler(title) } },
                    testTag = TestTags.poster(title.id),
                    modifier = if (posterFocus != null &&
                        (restoreTitleIndex == index || (restoreTitleIndex == null && index == 0))
                    ) {
                        Modifier.focusRequester(posterFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoveContinueWatchingDialog(
    titleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { confirmFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC090C12)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = modifier
                .width(420.dp)
                .background(Color(0xFF171C25), RoundedCornerShape(14.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.removeFromContinueWatching,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = titleName,
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .focusRequester(confirmFocus)
                        .testTag(TestTags.ContinueWatchingRemoveConfirm),
                ) {
                    Text(strings.removeFromContinueWatching)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(TestTags.ContinueWatchingRemoveCancel),
                ) {
                    Text(strings.close)
                }
            }
        }
    }
}
