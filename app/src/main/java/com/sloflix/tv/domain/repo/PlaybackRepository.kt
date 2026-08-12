package com.sloflix.tv.domain.repo

import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.session.Session

interface PlaybackRepository {
    suspend fun stream(session: Session, titleId: String): Result<StreamInfo>
    suspend fun saveProgress(session: Session, progress: PlaybackProgress): Result<Unit>
    suspend fun loadProgress(session: Session, titleId: String): Result<PlaybackProgress?>
}
