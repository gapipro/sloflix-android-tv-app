package com.sloflix.tv.domain.repo

import com.sloflix.tv.domain.session.Session

/**
 * Outcome of checking a stored session. [Unverified] means the server could not be reached, which
 * must never be confused with [Invalid]: only a proven rejection may drop a stored session.
 */
enum class SessionValidity {
    Valid,
    Unverified,
    Invalid,
}

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Session>
    suspend fun validateSession(session: Session): SessionValidity
}
