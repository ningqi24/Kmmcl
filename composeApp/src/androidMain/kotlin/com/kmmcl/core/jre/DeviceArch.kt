
package com.kmmcl.core.jre

import android.os.Build

object DeviceArch {
    /**
     * PojavLauncher native classifier key.
     * Maps Android ABI to Minecraft/PojavLauncher native naming.
     */
    val nativeKey: String
        get() = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "natives-arm64"
            Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "natives-arm32"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "natives-linux"
            Build.SUPPORTED_ABIS.contains("x86") -> "natives-linux"
            else -> "natives-linux"
        }
}
