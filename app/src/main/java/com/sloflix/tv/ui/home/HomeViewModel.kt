package com.sloflix.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.ui.components.UiState
import com.sloflix.tv.ui.components.toUserMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeRow(
    val title: String,
    val titles: List<TitleSummary>,
)

data class HomeContent(
    val categories: List<Category>,
    val selectedCategoryId: String?,
    val rows: List<HomeRow>,
)

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val playbackRepository: PlaybackRepository,
    private val sessionStore: SessionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeContent>> = mutableUiState.asStateFlow()

    private val mutableFilterState = MutableStateFlow(FilterState(sortBy = DefaultSortNewestAdded))
    val filterState: StateFlow<FilterState> = mutableFilterState.asStateFlow()

    /** Last focused/opened poster on Home — restored after returning from Details. */
    private val mutableFocusedTitleId = MutableStateFlow<String?>(null)
    val focusedTitleId: StateFlow<String?> = mutableFocusedTitleId.asStateFlow()

    private var isLoading = false
    private var filterOptionsLoaded = false
    private var reloadRequested = false
    private var queryDebounceJob: Job? = null
    private var categoriesCache: List<Category> = emptyList()
    private var selectedCategoryId: String? = null

    fun load() {
        if (isLoading || mutableUiState.value is UiState.Ready) return
        loadCatalog()
    }

    fun retry() {
        if (isLoading) return
        loadCatalog()
    }

    /** Soft refresh when returning to Home (e.g. after playback) without blanking the catalog. */
    fun refreshIfLoaded() {
        if (isLoading || mutableUiState.value !is UiState.Ready) return
        loadCatalog(keepCurrentContent = true)
    }

    fun removeFromContinueWatching(titleId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                val session = checkNotNull(sessionStore.get()) { "Your session has expired" }
                playbackRepository.clearProgress(session, titleId).getOrThrow()
                // Drop immediately so the row updates even if a full reload is already in flight.
                val current = mutableUiState.value
                if (current is UiState.Ready) {
                    val updatedRows = current.value.rows.mapNotNull { row ->
                        if (row.title != HomeRowKeys.ContinueWatching) return@mapNotNull row
                        val remaining = row.titles.filterNot { it.id == titleId }
                        if (remaining.isEmpty()) null else row.copy(titles = remaining)
                    }
                    mutableUiState.value = UiState.Ready(current.value.copy(rows = updatedRows))
                }
                if (mutableFocusedTitleId.value == titleId) {
                    mutableFocusedTitleId.value = null
                }
            } catch (_: Exception) {
                // Keep the row; viewer can retry remove later.
            }
        }
    }

    fun rememberFocusedTitle(titleId: String) {
        mutableFocusedTitleId.value = titleId
    }

    fun reset() {
        queryDebounceJob?.cancel()
        isLoading = false
        reloadRequested = false
        filterOptionsLoaded = false
        categoriesCache = emptyList()
        selectedCategoryId = null
        mutableFocusedTitleId.value = null
        mutableFilterState.value = FilterState(sortBy = DefaultSortNewestAdded)
        mutableUiState.value = UiState.Loading
    }

    fun selectCategory(categoryId: String?) {
        if (selectedCategoryId == categoryId &&
            mutableFilterState.value.selectedGenreIds == (categoryId?.let(::setOf) ?: emptySet<String>())
        ) {
            return
        }
        selectedCategoryId = categoryId
        updateFilter { current ->
            current.copy(
                selectedGenreIds = categoryId?.let(::setOf) ?: emptySet(),
                sortBy = current.sortBy ?: DefaultSortNewestAdded,
                offset = 0,
            )
        }
    }

    fun toggleGenre(genreId: String) {
        updateFilter { current ->
            val genres = current.selectedGenreIds.toMutableSet()
            if (!genres.add(genreId)) genres.remove(genreId)
            selectedCategoryId = genres.singleOrNull()
            current.copy(
                selectedGenreIds = genres,
                sortBy = current.sortBy ?: DefaultSortNewestAdded,
                offset = 0,
            )
        }
    }

    fun selectYear(year: Int?) {
        updateFilter { it.copy(selectedYear = year, offset = 0) }
    }

    fun selectType(type: Int?) {
        updateFilter { current ->
            // Chip taps toggle: selecting the active type again clears it (web Filmi/Serije).
            val next = if (type != null && current.selectedType == type) null else type
            current.copy(selectedType = next, offset = 0)
        }
    }

    fun selectSort(sort: Int?) {
        updateFilter { it.copy(sortBy = sort ?: DefaultSortNewestAdded, offset = 0) }
    }

    /**
     * Typing on a TV remote produces a burst of single-character edits, so the catalog reload waits
     * for the viewer to stop typing while the field itself updates immediately.
     */
    fun updateQuery(query: String) {
        val updated = mutableFilterState.value
            .copy(query = query.trim().ifEmpty { null }, offset = 0)
        if (updated == mutableFilterState.value) return
        mutableFilterState.value = updated
        queryDebounceJob?.cancel()
        queryDebounceJob = viewModelScope.launch(dispatcher) {
            delay(QueryDebounceMs)
            requestReload()
        }
    }

    fun clearFilters() {
        selectedCategoryId = null
        updateFilter { current ->
            current.copy(
                selectedGenreIds = emptySet(),
                selectedYear = null,
                query = null,
                selectedType = null,
                sortBy = DefaultSortNewestAdded,
                offset = 0,
            )
        }
    }

    private fun updateFilter(transform: (FilterState) -> FilterState) {
        val updated = transform(mutableFilterState.value)
        if (updated == mutableFilterState.value &&
            (mutableUiState.value as? UiState.Ready)?.value?.selectedCategoryId == selectedCategoryId
        ) {
            return
        }
        mutableFocusedTitleId.value = null
        mutableFilterState.value = updated
        queryDebounceJob?.cancel()
        requestReload()
    }

    private fun requestReload() {
        if (isLoading) {
            reloadRequested = true
        } else {
            loadCatalog(keepCurrentContent = true)
        }
    }

    private fun loadCatalog(keepCurrentContent: Boolean = false) {
        isLoading = true
        if (!keepCurrentContent || mutableUiState.value !is UiState.Ready) {
            mutableUiState.value = UiState.Loading
        }
        viewModelScope.launch(dispatcher) {
            try {
                val session = checkNotNull(sessionStore.get()) { "Your session has expired" }
                if (!filterOptionsLoaded) {
                    val options = catalogRepository.filterOptions(session).getOrThrow()
                    mutableFilterState.value = options.copy(
                        sortBy = options.sortBy ?: DefaultSortNewestAdded,
                        selectedGenreIds = selectedCategoryId?.let(::setOf)
                            ?: mutableFilterState.value.selectedGenreIds,
                    )
                    categoriesCache = options.availableGenres.map { (id, name) -> Category(id, name) }
                    filterOptionsLoaded = true
                }
                val filter = mutableFilterState.value.copy(
                    sortBy = mutableFilterState.value.sortBy ?: DefaultSortNewestAdded,
                )
                mutableFilterState.value = filter
                val continueWatching = if (filter.hasNarrowingFilters()) {
                    emptyList()
                } else {
                    catalogRepository.continueWatching(session).getOrThrow()
                }
                val rows = buildList {
                    if (continueWatching.isNotEmpty()) {
                        add(HomeRow(HomeRowKeys.ContinueWatching, continueWatching))
                    }
                    addAll(loadTitleRows(session, filter))
                }
                mutableUiState.value = UiState.Ready(
                    HomeContent(
                        categories = categoriesCache,
                        selectedCategoryId = selectedCategoryId,
                        rows = rows,
                    ),
                )
            } catch (error: Exception) {
                mutableUiState.value = UiState.Error(
                    error.toUserMessage("Unable to load your library"),
                )
            } finally {
                isLoading = false
                if (reloadRequested) {
                    reloadRequested = false
                    loadCatalog(keepCurrentContent = true)
                }
            }
        }
    }

    private suspend fun loadTitleRows(
        session: com.sloflix.tv.domain.session.Session,
        filter: FilterState,
    ): List<HomeRow> {
        // Chip or filter panel narrowing → single results row.
        if (selectedCategoryId != null || filter.hasNarrowingFilters()) {
            val titles = catalogRepository.titles(
                session = session,
                categoryId = selectedCategoryId,
                filter = filter,
            ).getOrThrow()
            val title = categoriesCache.firstOrNull { it.id == selectedCategoryId }?.name
                ?: if (!filter.query.isNullOrBlank()) HomeRowKeys.SearchResults else HomeRowKeys.Results
            return listOf(HomeRow(title, titles))
        }

        // Default home: one horizontal row per category (newest added).
        return coroutineScope {
            categoriesCache.map { category ->
                async {
                    val titles = catalogRepository.titles(
                        session = session,
                        categoryId = category.id,
                        filter = filter.copy(selectedGenreIds = emptySet()),
                    ).getOrThrow()
                    category to titles
                }
            }.awaitAll()
                .filter { (_, titles) -> titles.isNotEmpty() }
                .map { (category, titles) -> HomeRow(category.name, titles) }
        }
    }

    private companion object {
        const val QueryDebounceMs = 400L
        const val DefaultSortNewestAdded = 1
    }
}

/** Genre/search/sort filters that collapse the home into a single results list.
 *  Type (Filmi/Serije) alone still keeps per-category rows, matching the web browse UX. */
private fun FilterState.hasNarrowingFilters() =
    selectedGenreIds.isNotEmpty() ||
        selectedYear != null ||
        !query.isNullOrBlank() ||
        (sortBy != null && sortBy != 1)