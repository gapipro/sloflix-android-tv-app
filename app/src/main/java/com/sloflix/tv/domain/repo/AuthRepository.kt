package com.sloflix.tv.domain.repo

import com.sloflix.tv.domain.session.Session

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Session>
    suspend fun validateSession(session: Session): Boolean
}
