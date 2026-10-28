package com.example.contesttracker

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistence and state synchronization for Platform Subscriptions.
 * Key format: "platform_<platform.name>"
 * Default: true (all platforms subscribed by default).
 */
object PlatformPreferences {

    fun key(platform: Platform): String = "platform_${platform.name}"

    fun isPlatformEnabled(prefs: SharedPreferences, platform: Platform): Boolean =
        prefs.getBoolean(key(platform), true)

    fun isPlatformEnabled(context: Context, platform: Platform): Boolean =
        isPlatformEnabled(prefs(context), platform)

    fun setPlatformEnabled(context: Context, platform: Platform, enabled: Boolean) {
        setPlatformEnabled(prefs(context), platform, enabled)
    }

    fun setPlatformEnabled(prefs: SharedPreferences, platform: Platform, enabled: Boolean) {
        prefs.edit().putBoolean(key(platform), enabled).apply()
    }

    fun getSelectedPlatforms(prefs: SharedPreferences): Set<Platform> {
        return Platform.entries.filter { isPlatformEnabled(prefs, it) }.toSet()
    }

    fun getSelectedPlatforms(context: Context): Set<Platform> =
        getSelectedPlatforms(prefs(context))

    fun setAllPlatforms(context: Context, enabled: Boolean) {
        setAllPlatforms(prefs(context), enabled)
    }

    fun setAllPlatforms(prefs: SharedPreferences, enabled: Boolean) {
        val editor = prefs.edit()
        Platform.entries.forEach { platform ->
            editor.putBoolean(key(platform), enabled)
        }
        editor.apply()
    }

    fun areAllPlatformsSelected(selectedPlatforms: Collection<Platform>): Boolean =
        selectedPlatforms.size == Platform.entries.size

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
}
