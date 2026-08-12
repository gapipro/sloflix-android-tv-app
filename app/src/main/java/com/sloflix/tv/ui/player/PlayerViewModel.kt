package com.sloflix.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(val streamInfo: StreamInfo) : PlayerUiState
    data object Error : PlayerUiState
}

class PlayerViewModel(
    private val playbackRepository: PlaybackRepository,
    private val sessionStore: SessionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = mutableUiState.asStateFlow()

    private var titleId: String? = null
    private var session: Session? = null
    private var progressJob: Job? = null
    private var finalProgress: PlaybackProgress? = null

    fun load(titleId: String) {
        this.titleId = titleId
        session = null
        finalProgress = null
        progressJob?.cancel()
        mutableUiState.value = PlayerUiState.Loading
        viewModelScope.launch(dispatcher) {
            try {
                val currentSession = checkNotNull(sessionStore.get()) { "Your session has expired" }
                val streamInfo = playbackRepository.stream(currentSession, titleId).getOrThrow()
                if (this@PlayerViewModel.titleId != titleId) return@launch
                session = currentSession
                mutableUiState.value = PlayerUiState.Ready(streamInfo)
            } catch (_: Exception) {
                if (this@PlayerViewModel.titleId == titleId) {
                    mutableUiState.value = PlayerUiState.Error
                }
            }
        }
    }

    fun startProgressReporting(
        positionMs: () -> Long,
        durationMs: () -> Long,
    ) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(dispatcher) {
            var lastSavedPositionMs = positionMs()
            while (isActive) {
                delay(ProgressIntervalMs)
                val position = positionMs()
                val duration = durationMs()
                if (duration > 0 && position - lastSavedPositionMs >= ProgressIntervalMs) {
                    saveProgress(position, duration)
                    lastSavedPositionMs = position
                }
            }
        }
    }

    fun saveFinalProgress(positionMs: Long, durationMs: Long) {
        progressJob?.cancel()
        val progress = createProgress(positionMs, durationMs) ?: return
        if (progress == finalProgress) return
        finalProgress = progress
        viewModelScope.launch(dispatcher) {
            saveProgress(progress)
        }
    }

    private suspend fun saveProgress(positionMs: Long, durationMs: Long) {
        createProgress(positionMs, durationMs)?.let { saveProgress(it) }
    }

    private suspend fun saveProgress(progress: PlaybackProgress) {
        val currentSession = session ?: return
        playbackRepository.saveProgress(currentSession, progress)
    }

    private fun createProgress(positionMs: Long, durationMs: Long): PlaybackProgress? {
        val currentTitleId = titleId ?: return null
        return PlaybackProgress(
            titleId = currentTitleId,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
        )
    }

    private companion object {
        const val ProgressIntervalMs = 15_000L
    }
}
