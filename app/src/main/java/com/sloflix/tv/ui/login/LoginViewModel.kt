package com.sloflix.tv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sloflix.tv.domain.repo.AuthRepository
import com.sloflix.tv.domain.session.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionDestination {
    Checking,
    Login,
    Home,
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val destination: SessionDestination = SessionDestination.Checking,
)

sealed interface LoginEvent {
    data object NavigateHome : LoginEvent
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<LoginEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var hasRestoredSession = false

    fun onUsernameChanged(username: String) {
        mutableUiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        mutableUiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun restoreSession() {
        if (hasRestoredSession) return
        hasRestoredSession = true

        viewModelScope.launch(dispatcher) {
            val session = sessionStore.get()
            if (session == null) {
                mutableUiState.update { it.copy(destination = SessionDestination.Login) }
                return@launch
            }

            if (authRepository.validateSession(session)) {
                mutableUiState.update { it.copy(destination = SessionDestination.Home) }
            } else {
                sessionStore.clear()
                mutableUiState.update {
                    it.copy(
                        destination = SessionDestination.Login,
                        errorMessage = "Session expired",
                    )
                }
            }
        }
    }

    fun submit() {
        val credentials = mutableUiState.value
        if (credentials.isLoading) return

        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(dispatcher) {
            authRepository.login(credentials.username, credentials.password)
                .onSuccess { session ->
                    sessionStore.set(session)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            destination = SessionDestination.Home,
                        )
                    }
                    eventChannel.send(LoginEvent.NavigateHome)
                }
                .onFailure { error ->
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to sign in",
                        )
                    }
                }
        }
    }
}
