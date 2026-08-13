package com.sloflix.tv.domain.playback

import com.sloflix.tv.domain.model.ContinueWatchingEntry

/** In-memory store for unit tests. */
class InMemoryContinueWatchingStore(
    initial: List<ContinueWatchingEntry> = emptyList(),
) : ContinueWatchingStore {
    private val entries = initial.associateBy { it.titleId }.toMutableMap()

    override suspend fun all(): List<ContinueWatchingEntry> = entries.values.toList()

    override suspend fun get(titleId: String): ContinueWatchingEntry? = entries[titleId]

    override suspend fun upsert(entry: ContinueWatchingEntry) {
        entries[entry.titleId] = entry
    }

    override suspend fun remove(titleId: String) {
        entries.remove(titleId)
    }
}
