package com.example.contesttracker.update

import android.app.Activity
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the full OTA update flow.
 *
 * ## Startup check (called from MainActivity.onCreate)
 *   - Skipped automatically if the last check was less than 24 h ago.
 *   - Silent on any error so startup is never disrupted.
 *
 * ## Manual check (called from Settings → "Check for Updates" button)
 *   - Always runs regardless of the 24 h cooldown.
 *   - Reports the result string back to the UI via [onResult].
 */
object UpdateManager {

    private const val TAG = "UpdateManager"

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Checks for updates in the background.
     * Pass [forceCheck] = true to bypass the 24 h cooldown (used for the manual button).
     */
    fun checkForUpdates(activity: Activity, forceCheck: Boolean = false) {
        val prefs = UpdatePreferences(activity)

        if (!forceCheck && !prefs.shouldCheckForUpdate()) {
            Log.d(TAG, "Skipping update check (last checked: ${prefs.getFormattedLastCheckTime()})")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolved = resolveUpdate(prefs.updateChannel) ?: run {
                    prefs.markChecked()
                    Log.d(TAG, "No release found on GitHub")
                    return@launch
                }
                prefs.markChecked()

                val (release, apkAsset, sha256) = resolved
                val currentVersion = getCurrentVersion(activity)

                Log.d(TAG, "Current=$currentVersion  Latest=${release.tagName}")

                if (!VersionComparator.isNewerVersion(currentVersion, release.tagName)) {
                    Log.d(TAG, "App is up-to-date")
                    return@launch
                }

                // Don't re-show a dialog for a version the user already dismissed
                if (prefs.dismissedVersion == release.tagName) {
                    Log.d(TAG, "User dismissed ${release.tagName} previously")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        UpdateDialog.show(
                            activity       = activity,
                            currentVersion = currentVersion,
                            latestVersion  = release.tagName,
                            releaseNotes   = release.releaseNotes,
                            apkDownloadUrl = apkAsset.downloadUrl,
                            apkFileName    = apkAsset.name,
                            expectedSha256 = sha256
                        ) { dismissed ->
                            if (dismissed) prefs.dismissedVersion = release.tagName
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent fail on automatic startup check — never disrupt the user
                Log.e(TAG, "Update check failed: ${e.message}", e)
            }
        }
    }

    /**
     * Force-checks for an update (ignores the 24 h cooldown).
     * Calls [onResult] on the main thread with a human-readable status message.
     * Used by the "Check for Updates" button in Settings.
     */
    fun checkForUpdatesManually(activity: Activity, onResult: (String) -> Unit) {
        val prefs = UpdatePreferences(activity)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolved = resolveUpdate(prefs.updateChannel)
                prefs.markChecked()

                if (resolved == null) {
                    withContext(Dispatchers.Main) {
                        onResult("No releases found on GitHub.")
                    }
                    return@launch
                }

                val (release, apkAsset, sha256) = resolved
                val currentVersion = getCurrentVersion(activity)
                val isNewer = VersionComparator.isNewerVersion(currentVersion, release.tagName)

                withContext(Dispatchers.Main) {
                    if (isNewer) {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            UpdateDialog.show(
                                activity       = activity,
                                currentVersion = currentVersion,
                                latestVersion  = release.tagName,
                                releaseNotes   = release.releaseNotes,
                                apkDownloadUrl = apkAsset.downloadUrl,
                                apkFileName    = apkAsset.name,
                                expectedSha256 = sha256
                            ) { dismissed ->
                                if (dismissed) prefs.dismissedVersion = release.tagName
                            }
                        }
                        onResult("Update available: ${release.tagName}")
                    } else {
                        onResult("You're on the latest version (v$currentVersion)! ✓")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Check failed: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Fetches the appropriate release from GitHub, finds the APK asset and
     * the required SHA-256 sidecar, then returns all three.
     *
     * Returns null if:
     * - No release is found.
     * - The release has no APK asset.
     * - The SHA-256 sidecar asset is missing from the release.
     * - The fetched hash is not a valid 64-character hex string.
     *
     * A release without a verifiable hash is treated as uninstallable.
     */
    private suspend fun resolveUpdate(
        channel: String
    ): Triple<GitHubRelease, GitHubAsset, String>? {
        val release: GitHubRelease? = when (channel) {
            UpdatePreferences.CHANNEL_BETA -> {
                // All releases list includes pre-releases; take the newest one
                runCatching {
                    GitHubUpdateService.api
                        .getAllReleases(UpdateConfig.GITHUB_OWNER, UpdateConfig.GITHUB_REPO)
                        .firstOrNull()
                }.getOrNull()
            }
            else -> {
                // /releases/latest skips pre-releases automatically
                runCatching {
                    GitHubUpdateService.api
                        .getLatestRelease(UpdateConfig.GITHUB_OWNER, UpdateConfig.GITHUB_REPO)
                }.getOrNull()
            }
        }

        // Explicit null check so the compiler knows release is non-null below
        val nonNullRelease: GitHubRelease = release ?: return null

        val apkAsset = nonNullRelease.assets.firstOrNull { asset ->
            asset.name.startsWith(UpdateConfig.APK_ASSET_PREFIX, ignoreCase = true) &&
            asset.name.endsWith(UpdateConfig.APK_ASSET_EXTENSION, ignoreCase = true)
        } ?: return null   // Release exists but has no APK — skip silently

        // SHA-256 sidecar is mandatory. A release without one is treated as
        // uninstallable to prevent bypassing integrity verification.
        val sha256Asset = nonNullRelease.assets.firstOrNull { asset ->
            asset.name.endsWith(UpdateConfig.SHA256_ASSET_SUFFIX, ignoreCase = true) ||
            asset.name.equals("sha256.txt", ignoreCase = true)
        }
        if (sha256Asset == null) {
            Log.w(TAG, "Release ${nonNullRelease.tagName} has no SHA-256 sidecar. Skipping.")
            return null
        }

        val sha256 = fetchTextFromUrl(sha256Asset.downloadUrl)
        if (!isValidSha256(sha256)) {
            Log.w(TAG, "SHA-256 sidecar for ${nonNullRelease.tagName} is malformed: '$sha256'. Skipping.")
            return null
        }

        return Triple(nonNullRelease, apkAsset, sha256)
    }

    /**
     * Returns true if [hash] is a valid lowercase 64-character hex string
     * (the expected format for a SHA-256 digest).
     */
    private fun isValidSha256(hash: String): Boolean =
        hash.length == 64 && hash.all { it.isDigit() || it in 'a'..'f' }

    /**
     * Reads the installed app's versionName, falling back to "1.0" on any error.
     */
    private fun getCurrentVersion(activity: Activity): String = try {
        activity.packageManager
            .getPackageInfo(activity.packageName, 0)
            .versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }

    /**
     * Downloads a small text file (SHA-256 sidecar) over HTTPS.
     * Returns the first whitespace-separated token (the hash) or an empty string on failure.
     */
    private fun fetchTextFromUrl(url: String): String = runCatching {
        java.net.URL(url).readText().trim().split("\\s".toRegex()).firstOrNull() ?: ""
    }.getOrElse { "" }
}
