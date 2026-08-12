package com.sloflix.tv.ui.login

import com.sloflix.tv.domain.repo.AuthRepository
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @Test
    fun `successful login stores session and navigates home`() = runTest {
        val session = Session(accessToken = "fake-token")
        val authRepository = FakeAuthRepository(loginResult = Result.success(session))
        val sessionStore = FakeSessionStore()
        val viewModel = LoginViewModel(
            authRepository = authRepository,
            sessionStore = sessionStore,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.onUsernameChanged("fake-user")
        viewModel.onPasswordChanged("fake-password")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(session, sessionStore.storedSession)
        assertEquals(LoginEvent.NavigateHome, viewModel.events.first())
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `failed login displays repository error`() = runTest {
        val authRepository = FakeAuthRepository(
            loginResult = Result.failure(IllegalArgumentException("Invalid username or password")),
        )
        val viewModel = LoginViewModel(
            authRepository = authRepository,
            sessionStore = FakeSessionStore(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.onUsernameChanged("fake-user")
        viewModel.onPasswordChanged("wrong-fake-password")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Invalid username or password", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `login network failure shows offline message`() = runTest {
        val viewModel = LoginViewModel(
            authRepository = FakeAuthRepository(
                loginResult = Result.failure(UnknownHostException("api.sloflix.com")),
            ),
            sessionStore = FakeSessionStore(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            "You’re offline. Check your connection and try again.",
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `valid stored session selects home`() = runTest {
        val session = Session(accessToken = "stored-fake-token")
        val authRepository = FakeAuthRepository(isSessionValid = true)
        val viewModel = LoginViewModel(
            authRepository = authRepository,
            sessionStore = FakeSessionStore(storedSession = session),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.restoreSession()
        advanceUntilIdle()

        assertEquals(SessionDestination.Home, viewModel.uiState.value.destination)
        assertEquals(session, authRepository.validatedSession)
    }

    @Test
    fun `expired stored session is cleared and displays session expired`() = runTest {
        val sessionStore = FakeSessionStore(
            storedSession = Session(accessToken = "expired-fake-token"),
        )
        val viewModel = LoginViewModel(
            authRepository = FakeAuthRepository(isSessionValid = false),
            sessionStore = sessionStore,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.restoreSession()
        advanceUntilIdle()

        assertEquals(SessionDestination.Login, viewModel.uiState.value.destination)
        assertEquals("Session expired", viewModel.uiState.value.errorMessage)
        assertEquals(1, sessionStore.clearCount)
        assertNull(sessionStore.storedSession)
    }
}

private class FakeAuthRepository(
    private val loginResult: Result<Session> = Result.failure(
        IllegalStateException("Login was not configured for this test"),
    ),
    private val isSessionValid: Boolean = false,
) : AuthRepository {
    var validatedSession: Session? = null

    override suspend fun login(username: String, password: String): Result<Session> = loginResult

    override suspend fun validateSession(session: Session): Boolean {
        validatedSession = session
        return isSessionValid
    }
}

private class FakeSessionStore(
    var storedSession: Session? = null,
) : SessionStore {
    var clearCount = 0

    override suspend fun get(): Session? = storedSession

    override suspend fun set(session: Session) {
        storedSession = session
    }

    override suspend fun clear() {
        clearCount += 1
        storedSession = null
    }
}
