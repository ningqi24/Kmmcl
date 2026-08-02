
package com.kmmcl.core.game

object MojangMirror {
    private const val BASE = "https://bmclapi2.bangbang93.com"

    private val mappings = mapOf(
        "https://launchermeta.mojang.com" to BASE,
        "https://piston-data.mojang.com" to BASE,
        "https://piston-meta.mojang.com" to BASE,
        "https://libraries.minecraft.net" to "$BASE/maven",
        "https://resources.download.minecraft.net" to "$BASE/assets",
    )

    fun mirror(url: String): String {
        for ((origin, mirror) in mappings) {
            if (url.startsWith(origin)) {
                return mirror + url.removePrefix(origin)
            }
        }
        return url
    }
}
