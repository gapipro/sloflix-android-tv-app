package com.sloflix.tv.ui.player

import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @Test
    fun `saves progress every fifteen seconds when duration is known`() = runTest {
        val repository = FakePlaybackRepository()
        val viewModel = playerViewModel(repository, StandardTestDispatcher(testScheduler))
        var positionMs = 30_000L

        viewModel.load(TitleId)
        advanceUntilIdle()
        viewModel.startProgressReporting(
            positionMs = { positionMs },
            durationMs = { 120_000L },
        )

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(emptyList<PlaybackProgress>(), repository.savedProgress)

        positionMs = 45_000L
        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(PlaybackProgress(TitleId, 45_000L, 120_000L)),
            repository.savedProgress,
        )
        viewModel.saveFinalProgress(positionMs, 120_000L)
    }

    @Test
    fun `does not save periodic progress while duration is unknown`() = runTest {
        val repository = FakePlaybackRepository()
        val viewModel = playerViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.load(TitleId)
        advanceUntilIdle()
        viewModel.startProgressReporting(
            positionMs = { 15_000L },
            durationMs = { -1L },
        )

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(emptyList<PlaybackProgress>(), repository.savedProgress)
        viewModel.saveFinalProgress(15_000L, -1L)
    }

    @Test
    fun `final progress is saved once when player exits`() = runTest {
        val repository = FakePlaybackRepository()
        val viewModel = playerViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.load(TitleId)
        advanceUntilIdle()
        viewModel.saveFinalProgress(positionMs = 52_000L, durationMs = 120_000L)
        viewModel.saveFinalProgress(positionMs = 52_000L, durationMs = 120_000L)
        advanceUntilIdle()

        assertEquals(
            listOf(PlaybackProgress(TitleId, 52_000L, 120_000L)),
            repository.savedProgress,
        )
    }

    private fun playerViewModel(
        repository: PlaybackRepository,
        dispatcher: CoroutineDispatcher,
    ) = PlayerViewModel(
        playbackRepository = repository,
        sessionStore = FakeSessionStore(Session("token")),
        dispatcher = dispatcher,
    )

    private companion object {
        const val TitleId = "arrival"
    }
}

private class FakePlaybackRepository : PlaybackRepository {
    val savedProgress = mutableListOf<PlaybackProgress>()

    override suspend fun stream(session: Session, titleId: String): Result<StreamInfo> =
        Result.success(StreamInfo("https://example.com/movie.m3u8"))

    override suspend fun saveProgress(
        session: Session,
        progress: PlaybackProgress,
    ): Result<Unit> {
        savedProgress += progress
        return Result.success(Unit)
    }

    override suspend fun loadProgress(
        session: Session,
        titleId: String,
    ): Result<PlaybackProgress?> = error("Not used")
}

private class FakeSessionStore(
    private val session: Session?,
) : SessionStore {
    override suspend fun get(): Session? = session
    override suspend fun set(session: Session) = Unit
    override suspend fun clear() = Unit
}
