package com.example.contesttracker.update

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Verifies the SHA-256 integrity of a downloaded APK.
 *
 * Usage:
 *   val ok = SecurityVerifier.verify(apkFile, expectedHashFromGitHub)
 *   if (!ok) SecurityVerifier.deleteCorruptFile(apkFile)
 */
object SecurityVerifier {

    private const val TAG = "SecurityVerifier"

    /**
     * Computes the SHA-256 of [apkFile] and compares it to [expectedHash]
     * (a 64-character lowercase hex string).
     *
     * - If [expectedHash] is blank, verification **fails** (returns false).
     *   A missing hash is treated as a security failure, not a skip.
     * - Returns true only when the computed hash matches the expected hash exactly.
     */
    fun verify(apkFile: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) {
            Log.e(TAG, "SHA-256 verification failed: no expected hash supplied. Rejecting APK.")
            return false
        }

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().buffered().use { stream ->
                val buf = ByteArray(8_192)
                var n: Int
                while (stream.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            val actualHex   = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedHex = expectedHash.trim().lowercase()

            if (actualHex == expectedHex) {
                Log.i(TAG, "SHA-256 OK ✓")
                true
            } else {
                Log.e(TAG, "SHA-256 MISMATCH!\n  expected=$expectedHex\n  actual  =$actualHex")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verification exception: ${e.message}", e)
            false
        }
    }

    /** Deletes a file that failed verification. Logs the path. */
    fun deleteCorruptFile(file: File) {
        if (file.exists() && file.delete()) {
            Log.w(TAG, "Deleted corrupt APK: ${file.absolutePath}")
        }
    }
}
