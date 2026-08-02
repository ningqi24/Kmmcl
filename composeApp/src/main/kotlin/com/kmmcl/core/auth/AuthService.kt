package com.kmmcl.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthType { MICROSOFT, OFFLINE }

data class AuthState(
    val isLoggedIn: Boolean = false,
    val authType: AuthType = AuthType.OFFLINE,
    val username: String = "",
    val uuid: String = "",
    val accessToken: String = ""
)

data class AccountInfo(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val authType: AuthType
)

class AuthService {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun loginMicrosoft(): Result<AuthState> {
        return try {
            // Mokt MS auth flow: launch browser / WebView OAuth
            // val authResult = MoktAuth.microsoft { deviceCode ->
            //     // prompt user with device code
            // }
            val state = AuthState(
                isLoggedIn = true,
                authType = AuthType.MICROSOFT,
                username = "Player",
                uuid = "microsoft-uuid-placeholder",
                accessToken = "ms-access-token-placeholder"
            )
            _authState.value = state
            Result.success(state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loginOffline(username: String): AuthState {
        val state = AuthState(
            isLoggedIn = true,
            authType = AuthType.OFFLINE,
            username = username.ifBlank { "Player" },
            uuid = "offline-uuid-${username.hashCode().toString(16)}",
            accessToken = ""
        )
        _authState.value = state
        return state
    }

    fun logout() {
        _authState.value = AuthState()
    }
}
