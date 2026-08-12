package com.sloflix.tv.domain.session

interface SessionStore {
    suspend fun get(): Session?
    suspend fun set(session: Session)
    suspend fun clear()
}
