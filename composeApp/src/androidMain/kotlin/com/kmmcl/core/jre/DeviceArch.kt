
package com.kmmcl.core.jre

import android.os.Build
import com.kmmcl.data.model.JreInfo

object DeviceArch {
    val ABI: String get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    /** PojavLauncher native classifier for library downloads */
    val nativeKey: String get() = when {
        ABI.contains("arm64") -> "natives-arm64"
        ABI.contains("armeabi") -> "natives-arm32"
        ABI.contains("x86_64") -> "natives-linux"
        ABI.contains("x86") -> "natives-linux"
        else -> "natives-linux"
    }

    /** PojavLauncher JRE download URL */
    val jreUrl: String get() = when {
        ABI.contains("arm64") -> JreInfo.JRE_17.arm64Url
        ABI.contains("armeabi") -> JreInfo.JRE_17.armUrl
        ABI.contains("x86_64") -> JreInfo.JRE_17.x8664Url
        ABI.contains("x86") -> JreInfo.JRE_17.x86Url
        else -> JreInfo.JRE_17.arm64Url
    }

    /** All JRE download URLs ordered by priority: direct → mirrors. */
    val jreUrls: List<String> get() {
        val primary = jreUrl
        return JreInfo.MIRROR_PREFIXES.map { prefix ->
            if (prefix.isEmpty()) primary else "$prefix$primary"
        }
    }
}
