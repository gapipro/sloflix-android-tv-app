package com.sloflix.tv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session",
)

class DataStoreSessionStore(
    private val context: Context,
) : SessionStore {
    override suspend fun get(): Session? {
        val prefs = context.sessionDataStore.data.first()
        val token = prefs[ACCESS_TOKEN_KEY] ?: return null
        if (token.isEmpty()) return null
        return Session(
            accessToken = token,
            cookieHeader = prefs[COOKIE_HEADER_KEY],
            username = prefs[USERNAME_KEY],
        )
    }

    override suspend fun set(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = session.accessToken
            if (session.cookieHeader != null) {
                prefs[COOKIE_HEADER_KEY] = session.cookieHeader
            } else {
                prefs.remove(COOKIE_HEADER_KEY)
            }
            if (!session.username.isNullOrBlank()) {
                prefs[USERNAME_KEY] = session.username
            } else {
                prefs.remove(USERNAME_KEY)
            }
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(COOKIE_HEADER_KEY)
            prefs.remove(USERNAME_KEY)
        }
    }

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val COOKIE_HEADER_KEY = stringPreferencesKey("cookie_header")
        private val USERNAME_KEY = stringPreferencesKey("username")
    }
}
