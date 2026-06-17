package com.example.contesttracker.update

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows a Material 3 dialog informing the user that an update is available.
 *
 * Strips the most common Markdown tokens from the release notes before display
 * so raw GitHub Markdown doesn't appear verbatim.
 */
object UpdateDialog {

    fun show(
        activity: Activity,
        currentVersion: String,
        latestVersion: String,
        releaseNotes: String?,
        apkDownloadUrl: String,
        apkFileName: String,
        expectedSha256: String,
        onDismiss: (userChoseLater: Boolean) -> Unit
    ) {
        val cleanNotes = releaseNotes
            ?.replace(Regex("#{1,6}\\s+"), "")           // ## headings
            ?.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")  // **bold**
            ?.replace(Regex("\\*(.+?)\\*"), "$1")         // *italic*
            ?.replace(Regex("`(.+?)`"), "$1")             // `code`
            ?.replace(Regex("\\[(.+?)]\\(.+?\\)"), "$1") // [link](url)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Bug fixes and performance improvements."

        val message = buildString {
            append("A new version is available!\n\n")
            append("Current  →  v$currentVersion\n")
            append("New       →  $latestVersion\n\n")
            append("─────────────────────\n")
            append("What's new:\n\n")
            append(cleanNotes)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Update Available 🎉")
            .setMessage(message)
            .setPositiveButton("Update Now") { _, _ ->
                ApkDownloader.download(
                    context        = activity,
                    downloadUrl    = apkDownloadUrl,
                    fileName       = apkFileName,
                    expectedSha256 = expectedSha256
                )
                onDismiss(false)
            }
            .setNegativeButton("Later") { _, _ ->
                onDismiss(true)
            }
            .setCancelable(false)
            .show()
    }
}
