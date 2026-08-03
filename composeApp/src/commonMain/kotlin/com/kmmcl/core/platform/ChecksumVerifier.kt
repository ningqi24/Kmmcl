package com.kmmcl.core.platform

/**
 * Platform-specific SHA-256 verifier.
 *
 * Android: java.security.MessageDigest
 * Desktop: java.security.MessageDigest (same API — JVM standard library)
 */
interface ChecksumVerifier {
    /**
     * Compute SHA-256 hex digest of the file at [path].
     *
     * @return lowercase hex string
     */
    fun sha256Hex(path: String): String
}
