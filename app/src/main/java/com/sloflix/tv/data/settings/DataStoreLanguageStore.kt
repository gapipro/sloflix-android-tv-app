package com.sloflix.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sloflix.tv.domain.settings.AppLanguage
import com.sloflix.tv.domain.settings.LanguageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

class DataStoreLanguageStore(
    private val context: Context,
) : LanguageStore {
    override val language: Flow<AppLanguage> =
        context.languageDataStore.data.map { prefs ->
            AppLanguage.fromCode(prefs[LANGUAGE_KEY])
        }

    override suspend fun get(): AppLanguage =
        AppLanguage.fromCode(context.languageDataStore.data.first()[LANGUAGE_KEY])

    override suspend fun set(language: AppLanguage) {
        context.languageDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = language.code
        }
    }

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("language")
    }
}
