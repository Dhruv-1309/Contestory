package com.example.contesttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Runs on device boot (and equivalent OEM events) to restore contest alarms
 * that were wiped when the system powered off.
 *
 * BUG-Q1 fix: removed the goAsync() + coroutine network fetch that was here
 * before. goAsync() grants a ~10 s process-budget extension, but if the
 * process is OOM-killed before pendingResult.finish() executes the budget
 * leaks. Since v1.1.6 (DailyRefreshReceiver), a repeating AlarmManager alarm
 * handles periodic network refreshes automatically, so the boot receiver only
 * needs to do two guaranteed-synchronous things:
 *
 *  1. Reschedule alarms from the on-disk contest cache so reminders fire even
 *     if the phone never gets a network connection after reboot.
 *  2. Re-register the daily background refresh alarm — repeating alarms are
 *     cleared on every reboot and must be explicitly restored.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON") {
            return
        }

        Log.d(TAG, "Device booted. Restoring notification schedule.")

        val scheduler = NotificationScheduler(context)

        // ── Step 1: Reschedule from cache (guaranteed to run) ─────────────────
        val cachedContests = scheduler.getCachedContests()
        if (cachedContests.isNotEmpty()) {
            Log.d(TAG, "Scheduling ${cachedContests.size} cached contest(s).")
            scheduler.scheduleAll(cachedContests)
        } else {
            Log.d(TAG, "No cached contests — nothing to reschedule at boot.")
        }

        // ── Step 2: Re-register the daily refresh alarm ───────────────────────
        // Repeating AlarmManager alarms are wiped on reboot. DailyRefreshReceiver
        // will perform a network fetch on its first tick and keep the schedule
        // fresh going forward (BUG-N1 fix).
        DailyRefreshScheduler.schedule(context)

        Log.d(TAG, "Boot restore complete.")
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
