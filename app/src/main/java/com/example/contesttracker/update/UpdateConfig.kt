package com.example.contesttracker.update

/**
 * Single source of truth for all OTA update configuration.
 * Change GITHUB_OWNER and GITHUB_REPO to match your repository.
 */
object UpdateConfig {
    const val GITHUB_OWNER = "Dhruv-1309"
    const val GITHUB_REPO = "CodeCountdown"

    /** Prefix of the APK asset file name on GitHub Releases, e.g. "Contestory" */
    const val APK_ASSET_PREFIX = "Contestory"
    const val APK_ASSET_EXTENSION = ".apk"

    /** Suffix for the SHA-256 sidecar file, e.g. "CodeCountdown.sha256" or "sha256.txt" */
    const val SHA256_ASSET_SUFFIX = ".sha256"

    /** How often to auto-check for updates (24 hours). */
    const val UPDATE_CHECK_INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

    const val GITHUB_API_BASE = "https://api.github.com/"

    /** Notification IDs */
    const val NOTIFICATION_ID_UPDATE_READY = 9001
    const val NOTIFICATION_CHANNEL_ID = "codecountdown_updates"
    const val NOTIFICATION_CHANNEL_NAME = "App Updates"
}
