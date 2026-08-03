package com.kmmcl.core.platform

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Android / JVM [ChecksumVerifier] — uses [java.security.MessageDigest].
 *
 * Same implementation works on all JVM targets (Android, Desktop, Server).
 */
object AndroidChecksumVerifier : ChecksumVerifier {
    override fun sha256Hex(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(File(path)).use { fis ->
            val buf = ByteArray(8192)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
