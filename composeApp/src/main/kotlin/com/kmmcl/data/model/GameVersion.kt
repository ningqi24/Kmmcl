package com.kmmcl.data.model

data class GameVersion(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String
)

data class GameSettings(
    val gameDirectory: String = "",
    val maxMemory: Int = 2048,
    val javaArgs: List<String> = emptyList(),
    val enableSnapshots: Boolean = false
)
