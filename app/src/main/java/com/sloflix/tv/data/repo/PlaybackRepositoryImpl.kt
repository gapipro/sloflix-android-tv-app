package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.ProgressRequest
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.Session

class PlaybackRepositoryImpl(
    private val api: SloflixApi,
    private val sessionProvider: MutableSessionProvider,
) : PlaybackRepository {
    override suspend fun stream(session: Session, titleId: String): Result<StreamInfo> =
        runCatching {
            sessionProvider.update(session)
            val details = api.details(titleId).successfulData()
            val source = details.sources.firstOrNull()
                ?: error("No playback source is available")
            StreamInfo(source.url)
        }

    override suspend fun saveProgress(
        session: Session,
        progress: PlaybackProgress,
    ): Result<Unit> = runCatching {
        sessionProvider.update(session)
        val response = api.saveProgress(
            progress.titleId,
            ProgressRequest(progress.positionMs / 1_000.0),
        )
        check(response.isSuccessful && response.body()?.status == "success") {
            "Progress save failed with HTTP ${response.code()}"
        }
    }

    override suspend fun loadProgress(
        session: Session,
        titleId: String,
    ): Result<PlaybackProgress?> = runCatching {
        sessionProvider.update(session)
        val watchTime = api.details(titleId, dontCountView = true)
            .successfulData()
            .metadata
            ?.watchTimeSeconds
            ?: return@runCatching null
        PlaybackProgress(
            titleId = titleId,
            positionMs = (watchTime * 1_000).toLong(),
            durationMs = 0,
        )
    }
}
