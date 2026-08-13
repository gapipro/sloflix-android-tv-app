package com.sloflix.tv.domain.playback

import com.sloflix.tv.domain.model.ContinueWatchingEntry

interface ContinueWatchingStore {
    suspend fun all(): List<ContinueWatchingEntry>
    suspend fun get(titleId: String): ContinueWatchingEntry?
    suspend fun upsert(entry: ContinueWatchingEntry)
    suspend fun remove(titleId: String)
}
