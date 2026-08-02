package com.kmmcl.core.auth

import java.util.UUID

enum class AuthType { OFFLINE, MICROSOFT }

data class AuthState(
    val isLoggedIn: Boolean = false,
    val authType: AuthType = AuthType.OFFLINE,
    val username: String = "",
    val uuid: String = "",
    val accessToken: String = ""
)

class AuthService {

    var currentAuth: AuthState = AuthState()
        private set

    fun loginOffline(playerName: String): AuthState {
        val name = playerName.ifBlank { "Player" }
        val id = UUID.nameUUIDFromBytes(("OfflinePlayer:$name").toByteArray()).toString()
            .replace("-", "")
        currentAuth = AuthState(
            isLoggedIn = true,
            authType = AuthType.OFFLINE,
            username = name,
            uuid = id,
            accessToken = ""
        )
        return currentAuth
    }

    /** 预留：微软正版登录（后续接入 OAuth） */
    suspend fun loginMicrosoft(): AuthState {
        // TODO: implement Microsoft OAuth via Ktor
        return currentAuth
    }

    fun logout() {
        currentAuth = AuthState()
    }
}
