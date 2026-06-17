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
     * - If [expectedHash] is blank, verification is skipped with a warning and true is returned.
     * - Returns true if the file is valid, false otherwise.
     */
    fun verify(apkFile: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) {
            Log.w(TAG, "No expected hash supplied – skipping SHA-256 verification")
            return true
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
