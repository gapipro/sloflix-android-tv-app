package com.sloflix.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.repo.CatalogRepository
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
    val rows: List<HomeRow>,
)

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val sessionStore: SessionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeContent>> = mutableUiState.asStateFlow()

    private val mutableFilterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = mutableFilterState.asStateFlow()

    private var isLoading = false
    private var filterOptionsLoaded = false
    private var reloadRequested = false
    private var queryDebounceJob: Job? = null

    fun load() {
        if (isLoading || mutableUiState.value is UiState.Ready) return
        loadCatalog()
    }

    fun retry() {
        if (isLoading) return
        loadCatalog()
    }

    fun toggleGenre(genreId: String) {
        updateFilter { current ->
            val genres = current.selectedGenreIds.toMutableSet()
            if (!genres.add(genreId)) genres.remove(genreId)
            current.copy(selectedGenreIds = genres, offset = 0)
        }
    }

    fun selectYear(year: Int?) {
        updateFilter { it.copy(selectedYear = year, offset = 0) }
    }

    fun selectType(type: Int?) {
        updateFilter { it.copy(selectedType = type, offset = 0) }
    }

    fun selectSort(sort: Int?) {
        updateFilter { it.copy(sortBy = sort, offset = 0) }
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
        updateFilter { current ->
            current.copy(
                selectedGenreIds = emptySet(),
                selectedYear = null,
                query = null,
                selectedType = null,
                sortBy = null,
                offset = 0,
            )
        }
    }

    private fun updateFilter(transform: (FilterState) -> FilterState) {
        val updated = transform(mutableFilterState.value)
        if (updated == mutableFilterState.value) return
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
        // Refreshing an already populated screen keeps the rows on screen instead of wiping the
        // whole grid back to a spinner on every filter change.
        if (!keepCurrentContent || mutableUiState.value !is UiState.Ready) {
            mutableUiState.value = UiState.Loading
        }
        viewModelScope.launch(dispatcher) {
            try {
                val session = checkNotNull(sessionStore.get()) { "Your session has expired" }
                if (!filterOptionsLoaded) {
                    mutableFilterState.value = catalogRepository.filterOptions(session).getOrThrow()
                    filterOptionsLoaded = true
                }
                val filter = mutableFilterState.value
                val categories = catalogRepository.categories(session).getOrThrow()
                val categoryRows = coroutineScope {
                    categories.map { category ->
                        async {
                            val titles = catalogRepository.titles(
                                session = session,
                                categoryId = category.id,
                                filter = filter,
                            ).getOrThrow()
                            HomeRow(category.name, titles)
                        }
                    }.awaitAll()
                }
                val continueWatching = if (filter.hasActiveFilters()) {
                    emptyList()
                } else {
                    catalogRepository.continueWatching(session).getOrThrow()
                }
                val rows = buildList {
                    if (continueWatching.isNotEmpty()) {
                        add(HomeRow("Continue watching", continueWatching))
                    }
                    addAll(categoryRows)
                }
                mutableUiState.value = UiState.Ready(HomeContent(rows))
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

    private companion object {
        const val QueryDebounceMs = 400L
    }
}

private fun FilterState.hasActiveFilters() =
    selectedGenreIds.isNotEmpty() ||
        selectedYear != null ||
        !query.isNullOrBlank() ||
        selectedType != null ||
        sortBy != null
