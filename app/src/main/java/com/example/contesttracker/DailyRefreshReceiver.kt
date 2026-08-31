package com.example.contesttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Fires once every 24 hours via [DailyRefreshScheduler].
 *
 * Purpose: ensure contest alarms are always up-to-date even when the user
 * has not opened the app recently. Without this, alarms set on the last app
 * open fire and disappear — new contests that appeared in the feed after
 * that point never get scheduled.
 *
 * Flow:
 *  1. Try to fetch fresh contest data from the network.
 *  2. On success: call scheduleAll() with fresh data (also updates cache).
 *  3. On failure: reschedule from the on-disk cache so existing alarms
 *     are kept alive even during a network outage.
 */
class DailyRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Daily refresh alarm fired — rescheduling contest notifications.")

        val scheduler = NotificationScheduler(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    ContestRepository().fetchUpcomingContests()
                        .onSuccess { contests ->
                            Log.d(TAG, "Network refresh OK — scheduling ${contests.size} contest(s).")
                            scheduler.scheduleAll(contests)
                        }
                        .onFailure { e ->
                            Log.w(TAG, "Network refresh failed: ${e.message}. Falling back to cache.")
                            val cached = scheduler.getCachedContests()
                            if (cached.isNotEmpty()) {
                                scheduler.scheduleAll(cached)
                                Log.d(TAG, "Cache fallback: rescheduled ${cached.size} contest(s).")
                            } else {
                                Log.w(TAG, "Cache is empty — no alarms rescheduled.")
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Daily refresh timed out or crashed: ${e.message}", e)
                // Still try the cache as a last resort
                runCatching {
                    val cached = scheduler.getCachedContests()
                    if (cached.isNotEmpty()) scheduler.scheduleAll(cached)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DailyRefreshReceiver"
        private const val NETWORK_TIMEOUT_MS = 12_000L
    }
}
