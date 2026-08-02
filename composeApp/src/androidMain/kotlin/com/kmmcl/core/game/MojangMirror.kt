package com.kmmcl.core.game

object MojangMirror {
    private val mirrors = mapOf(
        "launcher.mojang.com"          to "bmclapi2.bangbang93.com",
        "launchermeta.mojang.com"      to "bmclapi2.bangbang93.com",
        "resources.download.minecraft.net" to "bmclapi2.bangbang93.com",
        "libraries.minecraft.net"      to "bmclapi2.bangbang93.com",
        "piston-meta.mojang.com"       to "bmclapi2.bangbang93.com",
    )

    fun mirror(url: String): String {
        if (url.isBlank()) return url
        for ((mojang, mirror) in mirrors) {
            if (mojang in url) {
                return url.replace(mojang, mirror)
            }
        }
        return url
    }
}
