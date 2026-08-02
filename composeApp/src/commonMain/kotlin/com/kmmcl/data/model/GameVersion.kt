
package com.kmmcl.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GameVersion(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String
)
