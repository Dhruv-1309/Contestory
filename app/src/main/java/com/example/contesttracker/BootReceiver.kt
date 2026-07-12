package com.example.contesttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON") {
            return
        }

        Log.d(TAG, "Device booted. Rescheduling notifications.")

        val scheduler = NotificationScheduler(context)

        // ── Step 1: Synchronous cache reschedule ──────────────────────────────
        // This completes within onReceive() and is guaranteed to run.
        // If the network fetch below is killed, users still get reminders
        // for contests that were already known before the reboot.
        val cachedContests = scheduler.getCachedContests()
        if (cachedContests.isNotEmpty()) {
            Log.d(TAG, "Found ${cachedContests.size} cached contest(s). Scheduling immediately.")
            scheduler.scheduleAll(cachedContests)
        } else {
            Log.d(TAG, "No cached contests found.")
        }

        // ── Step 2: Best-effort network refresh using goAsync() ───────────────
        // goAsync() extends the receiver's active window so the coroutine is
        // lifecycle-bound. We make a single attempt with a conservative timeout.
        // If it fails, the user's next app-open triggers a full refresh anyway.
        // The retry loop is intentionally removed: retries with multi-second
        // delays are incompatible with receiver lifecycle guarantees and were
        // the original cause of this issue.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    ContestRepository().fetchUpcomingContests()
                        .onSuccess { contests ->
                            Log.d(TAG, "Network refresh succeeded. Updating schedule with ${contests.size} contest(s).")
                            scheduler.scheduleAll(contests)
                        }
                        .onFailure { exception ->
                            Log.w(TAG, "Network refresh failed at boot (non-critical). Cache schedule remains active.", exception)
                        }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network refresh timed out or was interrupted at boot.", e)
            } finally {
                // Always call finish() to release the receiver's process lifecycle.
                // Failing to do so leaks the process budget granted by goAsync().
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        // Conservative timeout: must be well under Android's goAsync() hard limit.
        // Gives Retrofit enough time for a real network response on a slow connection.
        private const val NETWORK_TIMEOUT_MS = 8_000L
    }
}
