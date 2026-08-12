package com.sloflix.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.ui.components.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

    private var isLoading = false

    fun load() {
        if (isLoading || mutableUiState.value is UiState.Ready) return
        loadCatalog()
    }

    fun retry() {
        if (isLoading) return
        loadCatalog()
    }

    private fun loadCatalog() {
        isLoading = true
        mutableUiState.value = UiState.Loading
        viewModelScope.launch(dispatcher) {
            try {
                val session = checkNotNull(sessionStore.get()) { "Your session has expired" }
                val categories = catalogRepository.categories(session).getOrThrow()
                val categoryRows = categories.map { category ->
                    val titles = catalogRepository.titles(
                        session = session,
                        categoryId = category.id,
                        filter = FilterState(),
                    ).getOrThrow()
                    HomeRow(category.name, titles)
                }
                val continueWatching = catalogRepository.continueWatching(session).getOrThrow()
                val rows = buildList {
                    if (continueWatching.isNotEmpty()) {
                        add(HomeRow("Continue watching", continueWatching))
                    }
                    addAll(categoryRows)
                }
                mutableUiState.value = UiState.Ready(HomeContent(rows))
            } catch (error: Exception) {
                mutableUiState.value = UiState.Error(
                    error.message ?: "Unable to load your library",
                )
            } finally {
                isLoading = false
            }
        }
    }
}
