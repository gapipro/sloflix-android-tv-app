package com.sloflix.tv.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.ui.components.UiState
import com.sloflix.tv.ui.components.toUserMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailsContent(
    val title: TitleDetails,
    val resumePositionMs: Long,
) {
    val canResume: Boolean
        get() = resumePositionMs > 0
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

    private fun loadDetails(titleId: String) {
        isLoading = true
        mutableUiState.value = UiState.Loading
        viewModelScope.launch(dispatcher) {
            try {
                val session = checkNotNull(sessionStore.get()) { "Your session has expired" }
                val title = catalogRepository.details(session, titleId).getOrThrow()
                // Details already carries `watch_time`; loading progress hits the very same endpoint,
                // so it is only worth a request when details reported no watch time at all.
                val resumePosition = title.resumePositionMs
                    ?: playbackRepository
                        .loadProgress(session, titleId)
                        .getOrThrow()
                        ?.positionMs
                    ?: 0L
                val startPosition = resumePosition.coerceAtLeast(0L)
                if (this@DetailsViewModel.titleId != titleId) return@launch
                mutableUiState.value = UiState.Ready(
                    DetailsContent(
                        title = title,
                        resumePositionMs = startPosition,
                    ),
                )
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
}
