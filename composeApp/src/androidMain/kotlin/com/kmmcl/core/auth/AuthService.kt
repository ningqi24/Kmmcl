package com.kmmcl.core.auth

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

enum class AuthType { OFFLINE, MICROSOFT }

data class AuthState(
    val isLoggedIn: Boolean = false,
    val authType: AuthType = AuthType.OFFLINE,
    val username: String = "",
    val uuid: String = "",
    val accessToken: String = ""
)

@Serializable
private data class AccountStore(val currentIndex: Int = 0, val accounts: List<SavedAccount> = emptyList())

@Serializable
private data class SavedAccount(
    val authType: String,
    val username: String,
    val uuid: String,
    val accessToken: String
)

class AuthService(private val context: Context) {
    private val prefs = context.getSharedPreferences("kmmcl_accounts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private var _currentIndex: Int = 0
    private var _accounts: List<AuthState> = emptyList()

    val currentAuth: AuthState get() = _accounts.getOrElse(_currentIndex) { AuthState() }
    val accounts: List<AuthState> get() = _accounts
    val currentIndex: Int get() = _currentIndex

    init {
        loadAccounts()
    }

    fun loginOffline(playerName: String): AuthState {
        val name = playerName.ifBlank { "Player" }
        val id = UUID.nameUUIDFromBytes(("OfflinePlayer:$name").toByteArray()).toString()
            .replace("-", "")
        val auth = AuthState(
            isLoggedIn = true,
            authType = AuthType.OFFLINE,
            username = name,
            uuid = id,
            accessToken = ""
        )

        val existing = _accounts.indexOfFirst { it.username == name && it.authType == AuthType.OFFLINE }
        if (existing >= 0) {
            _currentIndex = existing
        } else {
            _accounts = _accounts + auth
            _currentIndex = _accounts.lastIndex
        }
        saveAccounts()
        return auth
    }

    fun switchAccount(index: Int) {
        if (index in _accounts.indices) {
            _currentIndex = index
            saveAccounts()
        }
    }

    fun deleteAccount(index: Int) {
        if (index in _accounts.indices) {
            _accounts = _accounts.toMutableList().apply { removeAt(index) }
            _currentIndex = _currentIndex.coerceIn(0, (_accounts.size - 1).coerceAtLeast(0))
            saveAccounts()
        }
    }

    fun logout() {
        if (_accounts.isNotEmpty()) {
            _accounts = _accounts.toMutableList().apply { removeAt(_currentIndex) }
            _currentIndex = (_currentIndex - 1).coerceAtLeast(0)
            saveAccounts()
        }
    }

    suspend fun loginMicrosoft(): AuthState = currentAuth

    private fun loadAccounts() {
        val raw = prefs.getString("accounts", null) ?: return
        try {
            val store = json.decodeFromString<AccountStore>(raw)
            _currentIndex = store.currentIndex
            _accounts = store.accounts.map {
                AuthState(
                    isLoggedIn = true,
                    authType = AuthType.valueOf(it.authType),
                    username = it.username,
                    uuid = it.uuid,
                    accessToken = it.accessToken
                )
            }
        } catch (_: Exception) {}
    }

    private fun saveAccounts() {
        val store = AccountStore(
            currentIndex = _currentIndex,
            accounts = _accounts.map {
                SavedAccount(it.authType.name, it.username, it.uuid, it.accessToken)
            }
        )
        prefs.edit().putString("accounts", json.encodeToString(store)).apply()
    }
}
