package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.ProgressRequest
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.data.playback.StreamP2PClient
import com.sloflix.tv.domain.model.ContinueWatchingEntry
import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.playback.ContinueWatchingStore
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.Session

class PlaybackRepositoryImpl(
    private val api: SloflixApi,
    private val sessionProvider: MutableSessionProvider,
    private val continueWatchingStore: ContinueWatchingStore,
    private val streamP2PClient: StreamP2PClient? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : PlaybackRepository {
    override suspend fun stream(session: Session, titleId: String): Result<StreamInfo> =
        runCatching {
            sessionProvider.update(session)
            val details = api.details(titleId).successfulData()
            val candidates = StreamSourceResolver.candidates(details.sources)
            check(candidates.isNotEmpty()) { "No playback source is available" }
            // The CDN behind player.sloflix.com refuses connections unless the request looks like
            // it came from the web player iframe (Referer/Origin). Confirmed against live sources.
            StreamInfo(
                url = candidates.first(),
                headers = PlayerPlaybackHeaders,
                fallbackUrls = candidates.drop(1),
                subtitles = StreamSourceResolver.subtitles(details.sources),
            )
        }

    override suspend fun resolveStreamP2P(embedUrl: String): Result<StreamInfo> {
        val client = streamP2PClient
            ?: return Result.failure(IllegalStateException("StreamP2P client is not configured"))
        return client.resolve(embedUrl)
    }

    companion object {
        val PlayerPlaybackHeaders = mapOf(
            "Referer" to "https://player.sloflix.com/",
            "Origin" to "https://player.sloflix.com",
        )
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
        runCatching { upsertContinueWatching(progress) }
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

    override suspend fun clearProgress(
        session: Session,
        titleId: String,
    ): Result<Unit> = runCatching {
        sessionProvider.update(session)
        val response = api.saveProgress(titleId, ProgressRequest(0.0))
        check(response.isSuccessful && response.body()?.status == "success") {
            "Progress clear failed with HTTP ${response.code()}"
        }
        continueWatchingStore.remove(titleId)
    }

    private suspend fun upsertContinueWatching(progress: PlaybackProgress) {
        val existing = continueWatchingStore.get(progress.titleId)
        val (name, posterUrl) = if (existing != null && existing.name.isNotBlank()) {
            existing.name to existing.posterUrl
        } else {
            val details = api.details(progress.titleId, dontCountView = true).successfulData()
            details.name to details.thumbnailUrl
        }
        continueWatchingStore.upsert(
            ContinueWatchingEntry(
                titleId = progress.titleId,
                name = name,
                posterUrl = posterUrl,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs.takeIf { it > 0 } ?: existing?.durationMs ?: 0L,
                updatedAtMs = clockMs(),
            ),
        )
    }
}
