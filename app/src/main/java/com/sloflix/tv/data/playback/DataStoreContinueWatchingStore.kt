package com.sloflix.tv.data.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sloflix.tv.domain.model.ContinueWatchingEntry
import com.sloflix.tv.domain.playback.ContinueWatchingStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.continueWatchingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "continue_watching",
)

class DataStoreContinueWatchingStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : ContinueWatchingStore {
    override suspend fun all(): List<ContinueWatchingEntry> =
        readEntries().map { it.toDomain() }

    override suspend fun get(titleId: String): ContinueWatchingEntry? =
        readEntries().firstOrNull { it.titleId == titleId }?.toDomain()

    override suspend fun upsert(entry: ContinueWatchingEntry) {
        context.continueWatchingDataStore.edit { prefs ->
            val next = readEntries(prefs)
                .filterNot { it.titleId == entry.titleId }
                .toMutableList()
            next += ContinueWatchingEntryDto.fromDomain(entry)
            prefs[ENTRIES_KEY] = json.encodeToString(next)
        }
    }

    override suspend fun remove(titleId: String) {
        context.continueWatchingDataStore.edit { prefs ->
            val next = readEntries(prefs).filterNot { it.titleId == titleId }
            prefs[ENTRIES_KEY] = json.encodeToString(next)
        }
    }

    private suspend fun readEntries(): List<ContinueWatchingEntryDto> {
        val prefs = context.continueWatchingDataStore.data.first()
        return readEntries(prefs)
    }

    private fun readEntries(prefs: Preferences): List<ContinueWatchingEntryDto> {
        val raw = prefs[ENTRIES_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<ContinueWatchingEntryDto>>(raw)
        }.getOrDefault(emptyList())
    }

    companion object {
        private val ENTRIES_KEY = stringPreferencesKey("entries")
    }
}

@Serializable
private data class ContinueWatchingEntryDto(
    val titleId: String,
    val name: String,
    val posterUrl: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
) {
    fun toDomain() = ContinueWatchingEntry(
        titleId = titleId,
        name = name,
        posterUrl = posterUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtMs = updatedAtMs,
    )

    companion object {
        fun fromDomain(entry: ContinueWatchingEntry) = ContinueWatchingEntryDto(
            titleId = entry.titleId,
            name = entry.name,
            posterUrl = entry.posterUrl,
            positionMs = entry.positionMs,
            durationMs = entry.durationMs,
            updatedAtMs = entry.updatedAtMs,
        )
    }
}
