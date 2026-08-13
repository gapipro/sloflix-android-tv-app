package com.sloflix.tv.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sloflix.tv.domain.model.EpisodeSummary
import com.sloflix.tv.domain.model.MediaKind
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.ui.components.UiState
import com.sloflix.tv.ui.components.toUserMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsContent(
    val requestId: String,
    val title: TitleDetails,
    val resumePositionMs: Long,
    val selectedSeason: Int? = null,
    val episodes: List<EpisodeSummary> = emptyList(),
    val episodesLoading: Boolean = false,
) {
    val canResume: Boolean
        get() = resumePositionMs > 0 && !title.isSeriesUi

    val isSeries: Boolean
        get() = title.isSeriesUi
}

class DetailsViewModel(
    private val catalogRepository: CatalogRepository,
    private val playbackRepository: PlaybackRepository,
    private val sessionStore: SessionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<UiState<DetailsContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<DetailsContent>> = mutableUiState.asStateFlow()

    private var titleId: String? = null
    private var isLoading = false
    private var episodesJob: Job? = null

    fun load(titleId: String) {
        if (this.titleId == titleId && mutableUiState.value is UiState.Ready) return
        if (this.titleId != titleId) {
            mutableUiState.value = UiState.Loading
        }
        this.titleId = titleId
        loadDetails(titleId)
    }

    fun retry() {
        val titleId = titleId ?: return
        if (!isLoading) loadDetails(titleId)
    }

    fun selectSeason(season: Int) {
        val ready = mutableUiState.value as? UiState.Ready ?: return
        if (ready.value.selectedSeason == season) return
        val showId = ready.value.title.seriesShowId
        mutableUiState.value = UiState.Ready(
            ready.value.copy(selectedSeason = season, episodes = emptyList(), episodesLoading = true),
        )
        loadEpisodes(requestId = ready.value.requestId, showId = showId, season = season)
    }

    private fun loadDetails(titleId: String) {
        isLoading = true
        episodesJob?.cancel()
        mutableUiState.value = UiState.Loading
        viewModelScope.launch(dispatcher) {
            try {
                val session = checkNotNull(sessionStore.get()) { "Your session has expired" }
                var title = catalogRepository.details(session, titleId).getOrThrow()
                var highlightSeason: Int? = null

                if (title.kind == MediaKind.Episode) {
                    highlightSeason = title.season
                    val parentId = checkNotNull(title.parentId) { "Episode is missing its series" }
                    val parent = catalogRepository.details(session, parentId).getOrThrow()
                    title = parent.copy(
                        kind = MediaKind.Show,
                        showName = parent.name,
                        posterUrl = parent.posterUrl ?: title.posterUrl,
                        backdropUrl = parent.backdropUrl ?: title.backdropUrl,
                    )
                }

                val resumePosition = if (title.isSeriesUi) {
                    0L
                } else {
                    title.resumePositionMs
                        ?: playbackRepository
                            .loadProgress(session, titleId)
                            .getOrThrow()
                            ?.positionMs
                        ?: 0L
                }
                if (this@DetailsViewModel.titleId != titleId) return@launch

                val selectedSeason = when {
                    highlightSeason != null -> highlightSeason
                    title.seasons.isNotEmpty() -> title.seasons.first()
                    else -> null
                }
                mutableUiState.value = UiState.Ready(
                    DetailsContent(
                        requestId = titleId,
                        title = title,
                        resumePositionMs = resumePosition.coerceAtLeast(0L),
                        selectedSeason = selectedSeason,
                        episodesLoading = selectedSeason != null,
                    ),
                )
                if (selectedSeason != null) {
                    loadEpisodes(
                        requestId = titleId,
                        showId = title.seriesShowId,
                        season = selectedSeason,
                    )
                }
            } catch (error: Exception) {
                if (this@DetailsViewModel.titleId != titleId) return@launch
                mutableUiState.value = UiState.Error(
                    error.toUserMessage("Unable to load title details"),
                )
            } finally {
                if (this@DetailsViewModel.titleId == titleId) {
                    isLoading = false
                }
            }
        }
    }

    private fun loadEpisodes(
        requestId: String,
        showId: String,
        season: Int,
    ) {
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch(dispatcher) {
            try {
                val session = checkNotNull(sessionStore.get()) { "Your session has expired" }
                val episodes = catalogRepository.episodes(session, showId, season).getOrThrow()
                    .sortedBy { it.episodeIndex }
                if (this@DetailsViewModel.titleId != requestId) return@launch
                mutableUiState.update { state ->
                    val ready = state as? UiState.Ready ?: return@update state
                    if (ready.value.requestId != requestId) return@update state
                    UiState.Ready(
                        ready.value.copy(
                            episodes = episodes,
                            episodesLoading = false,
                            selectedSeason = season,
                        ),
                    )
                }
            } catch (_: Exception) {
                if (this@DetailsViewModel.titleId != requestId) return@launch
                mutableUiState.update { state ->
                    val ready = state as? UiState.Ready ?: return@update state
                    if (ready.value.requestId != requestId) return@update state
                    UiState.Ready(
                        ready.value.copy(
                            episodes = emptyList(),
                            episodesLoading = false,
                        ),
                    )
                }
            }
        }
    }
}
