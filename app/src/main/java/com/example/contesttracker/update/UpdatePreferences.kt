package com.example.contesttracker.update

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thin wrapper around SharedPreferences that stores OTA update state.
 */
class UpdatePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME        = "ota_update_prefs"
        const val KEY_LAST_CHECK_MS         = "last_check_ms"
        const val KEY_UPDATE_CHANNEL        = "update_channel"
        const val KEY_DISMISSED_VERSION     = "dismissed_version"

        const val CHANNEL_STABLE = "stable"
        const val CHANNEL_BETA   = "beta"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Epoch-ms timestamp of the last successful update check. 0 = never checked. */
    var lastCheckMs: Long
        get()      = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_CHECK_MS, value).apply() }

    /** "stable" or "beta" */
    var updateChannel: String
        get()      = prefs.getString(KEY_UPDATE_CHANNEL, CHANNEL_STABLE) ?: CHANNEL_STABLE
        set(value) { prefs.edit().putString(KEY_UPDATE_CHANNEL, value).apply() }

    /**
     * Tag name of the last update the user dismissed with "Later".
     * Prevents repeatedly showing the dialog for the same release.
     */
    var dismissedVersion: String
        get()      = prefs.getString(KEY_DISMISSED_VERSION, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DISMISSED_VERSION, value).apply() }

    /** True if enough time has elapsed since the last check to warrant a new one. */
    fun shouldCheckForUpdate(): Boolean =
        System.currentTimeMillis() - lastCheckMs >= UpdateConfig.UPDATE_CHECK_INTERVAL_MS

    /** Call this immediately after a successful check to reset the cooldown timer. */
    fun markChecked() {
        lastCheckMs = System.currentTimeMillis()
    }

    /** Human-readable "MMM dd, HH:mm" string of the last check time, or "Never". */
    fun getFormattedLastCheckTime(): String {
        if (lastCheckMs == 0L) return "Never"
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(lastCheckMs))
    }
}
