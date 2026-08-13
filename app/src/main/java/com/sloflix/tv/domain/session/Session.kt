package com.sloflix.tv.domain.session

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Session(
    val accessToken: String,
    val cookieHeader: String? = null,
    val username: String? = null,
) {
    fun withResolvedUsername(): Session {
        if (!username.isNullOrBlank()) return this
        return copy(username = usernameFromAccessToken(accessToken))
    }

    companion object {
        fun usernameFromAccessToken(token: String): String? = runCatching {
            val payload = Json.parseToJsonElement(
                String(Base64.getUrlDecoder().decode(token.split('.')[1])),
            ).jsonObject
            sequenceOf("username", "preferred_username", "user_name", "name", "email", "sub")
                .mapNotNull { key ->
                    payload[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
                ?.takeUnless { candidate -> candidate.all(Char::isDigit) }
        }.getOrNull()
    }
}
