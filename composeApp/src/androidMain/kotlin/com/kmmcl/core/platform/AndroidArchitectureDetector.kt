package com.kmmcl.core.platform

import android.os.Build

/**
 * Android [ArchitectureDetector] — reads from [Build.SUPPORTED_ABIS].
 *
 * Prefers 64-bit targets when available (ARM64 → X86_64); falls back to
 * 32-bit (ARM32 → X86) only when no 64-bit ABI is present.
 */
object AndroidArchitectureDetector : ArchitectureDetector {
    override fun detect(): Architecture {
        val abis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray()
        for (abi in abis) {
            when {
                abi.startsWith("arm64") -> return Architecture.ARM64
                abi.startsWith("x86_64") -> return Architecture.X86_64
            }
        }
        for (abi in abis) {
            when {
                abi.startsWith("armeabi") -> return Architecture.ARM32
                abi.startsWith("x86") -> return Architecture.X86
            }
        }
        return Architecture.UNKNOWN
    }
}
