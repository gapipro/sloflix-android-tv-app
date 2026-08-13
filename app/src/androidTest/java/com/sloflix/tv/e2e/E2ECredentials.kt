package com.sloflix.tv.e2e

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

object E2ECredentials {
    private val args get() = InstrumentationRegistry.getArguments()

    val username: String?
        get() = args.getString("sloflix.username")?.takeIf { it.isNotBlank() }
            ?: System.getenv("SLOFLIX_USERNAME")?.takeIf { it.isNotBlank() }

    val password: String?
        get() = args.getString("sloflix.password")?.takeIf { it.isNotBlank() }
            ?: System.getenv("SLOFLIX_PASSWORD")?.takeIf { it.isNotBlank() }

    fun assumePresent() {
        assumeTrue(
            "Pass -Psloflix.username / -Psloflix.password or set SLOFLIX_USERNAME / SLOFLIX_PASSWORD",
            !username.isNullOrBlank() && !password.isNullOrBlank(),
        )
    }
}
