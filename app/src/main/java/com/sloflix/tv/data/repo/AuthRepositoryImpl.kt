package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.LoginRequest
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.data.net.NetworkStatus
import com.sloflix.tv.domain.repo.AuthRepository
import com.sloflix.tv.domain.repo.SessionValidity
import com.sloflix.tv.domain.session.Session
import java.io.IOException
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthRepositoryImpl(
    private val api: SloflixApi,
    private val sessionProvider: MutableSessionProvider,
    private val networkStatus: NetworkStatus = NetworkStatus.AlwaysOnline,
) : AuthRepository {
    override suspend fun login(username: String, password: String): Result<Session> = runCatching {
        val response = api.login(LoginRequest(username, password))
        val body = response.body()
        check(response.isSuccessful && body?.status == "success") {
            signInFailureMessage(response.code())
        }
        val token = requireNotNull(body.metadata?.accessToken) {
            "Login response did not contain an access token"
        }
        val cookie = response.headers()
            .values("Set-Cookie")
            .map { it.substringBefore(';') }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
        Session(token, cookie).also(sessionProvider::update)
    }

    override suspend fun validateSession(session: Session): SessionValidity {
        if (isExpired(session.accessToken)) {
            sessionProvider.update(null)
            return SessionValidity.Invalid
        }
        sessionProvider.update(session)
        if (!networkStatus.isOnline()) return SessionValidity.Unverified
        return try {
            val response = api.preferences()
            if (response.isSuccessful && response.body()?.status == "success") {
                SessionValidity.Valid
            } else {
                sessionProvider.update(null)
                SessionValidity.Invalid
            }
        } catch (error: IOException) {
            SessionValidity.Unverified
        }
    }

    private fun isExpired(token: String): Boolean = runCatching {
        val payload = token.split('.')[1]
        val decoded = String(Base64.getUrlDecoder().decode(payload))
        val expiry = Json.parseToJsonElement(decoded)
            .jsonObject["exp"]
            ?.jsonPrimitive
            ?.content
            ?.toLong()
            ?: return true
        expiry <= System.currentTimeMillis() / 1_000
    }.getOrDefault(true)
}

// Sloflix reports rejected credentials as HTTP 500 with a `status: "failed"` envelope rather than a
// 401, so the whole failing-status range is surfaced as a credentials problem.
private fun signInFailureMessage(code: Int): String = when (code) {
    400, 401, 403, 422, 500 -> "Incorrect username or password."
    else -> "Sloflix couldn’t sign you in (HTTP $code). Try again later."
}
