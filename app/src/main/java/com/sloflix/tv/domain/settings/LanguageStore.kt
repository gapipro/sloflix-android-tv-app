package com.sloflix.tv.domain.settings

import kotlinx.coroutines.flow.Flow

interface LanguageStore {
    val language: Flow<AppLanguage>
    suspend fun get(): AppLanguage
    suspend fun set(language: AppLanguage)
}
