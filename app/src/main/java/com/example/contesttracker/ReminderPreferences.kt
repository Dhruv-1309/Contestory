package com.example.contesttracker

import android.content.Context

/**
 * Persists per-contest reminder opt-in state.
 *
 * Key format: "reminder_<contestId>"
 * Default: true (all contests are reminded by default; matches original behaviour).
 *
 * Storage is in the shared [SettingsActivity.PREFS_NAME] preferences file so no
 * additional file handle is needed alongside [NotificationScheduler]'s prefs.
 */
object ReminderPreferences {

    private fun key(contestId: Long) = "reminder_$contestId"

    /**
     * Returns true if the user has a reminder enabled for [contestId].
     * Defaults to true — opt-out model, preserving existing notification behaviour.
     */
    fun isEnabled(context: Context, contestId: Long): Boolean =
        prefs(context).getBoolean(key(contestId), true)

    /** Persists the user's reminder preference for [contestId]. */
    fun setEnabled(context: Context, contestId: Long, enabled: Boolean) {
        prefs(context).edit().putBoolean(key(contestId), enabled).apply()
    }

    /**
     * Removes all per-contest reminder keys from preferences.
     * Called when the user globally disables notifications so that re-enabling
     * restores the default opt-in state for all contests.
     */
    fun clearAll(context: Context) {
        val p = prefs(context)
        val keysToRemove = p.all.keys.filter { it.startsWith("reminder_") }
        if (keysToRemove.isNotEmpty()) {
            p.edit().apply { keysToRemove.forEach { remove(it) } }.apply()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
}
