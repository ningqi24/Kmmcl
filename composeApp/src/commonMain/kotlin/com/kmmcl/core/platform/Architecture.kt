package com.kmmcl.core.platform

/**
 * Cross-platform CPU architecture enumeration.
 *
 * Used by JRE download to select the correct binary and
 * by native library resolution to pick the right .so/.dll/.dylib.
 */
enum class Architecture(val abiName: String) {
    ARM64("arm64-v8a"),
    ARM32("armeabi-v7a"),
    X86_64("x86_64"),
    X86("x86"),
    UNKNOWN("unknown"),
}

/**
 * Platform-specific architecture detector.
 *
 * Each platform (Android / JVM desktop / native) provides its own
 * implementation via actual or DI wiring.
 */
interface ArchitectureDetector {
    fun detect(): Architecture
}
