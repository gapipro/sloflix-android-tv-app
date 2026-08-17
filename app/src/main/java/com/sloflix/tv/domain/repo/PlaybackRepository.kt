package com.sloflix.tv.domain.repo

import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.session.Session

interface PlaybackRepository {
    suspend fun stream(session: Session, titleId: String): Result<StreamInfo>
    /**
     * Decrypts a StreamP2P embed (`sf.strp2p.com` / `playerp2p.com`) into an
     * ExoPlayer-ready HLS [StreamInfo]. Does not require a Sloflix session.
     */
    suspend fun resolveStreamP2P(embedUrl: String): Result<StreamInfo>
    suspend fun saveProgress(session: Session, progress: PlaybackProgress): Result<Unit>
    suspend fun loadProgress(session: Session, titleId: String): Result<PlaybackProgress?>
    /** Clears server watch time and drops the title from the local resume list. */
    suspend fun clearProgress(session: Session, titleId: String): Result<Unit>
}
