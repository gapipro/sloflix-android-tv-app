package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.LoginRequest
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.domain.repo.AuthRepository
import com.sloflix.tv.domain.session.Session
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthRepositoryImpl(
    private val api: SloflixApi,
    private val sessionProvider: MutableSessionProvider,
) : AuthRepository {
    override suspend fun login(username: String, password: String): Result<Session> = runCatching {
        val response = api.login(LoginRequest(username, password))
        val body = response.body()
        check(response.isSuccessful && body?.status == "success") {
            "Login failed with HTTP ${response.code()}"
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

    override suspend fun validateSession(session: Session): Boolean {
        if (isExpired(session.accessToken)) return false
        sessionProvider.update(session)
        return runCatching {
            val response = api.preferences()
            response.isSuccessful && response.body()?.status == "success"
        }.getOrDefault(false)
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
