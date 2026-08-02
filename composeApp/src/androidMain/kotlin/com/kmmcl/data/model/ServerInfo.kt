package com.kmmcl.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val name: String = "",
    val host: String = "",
    val port: Int = 25565
) {
    val isValid: Boolean get() = host.isNotBlank() && name.isNotBlank()
}
