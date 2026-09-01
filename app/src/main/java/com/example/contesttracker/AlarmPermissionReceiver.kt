package com.example.contesttracker

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Listens for [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED]
 * (Android 12 / API 31+).
 *
 * Android fires this broadcast whenever the user grants or revokes the
 * SCHEDULE_EXACT_ALARM permission from system Settings, even while the app
 * is in the background. Without this receiver, the app would only react to
 * permission changes via the onResume() check — which only fires when the
 * user actively returns to the app (BUG-N5 fix).
 *
 * Behaviour:
 *  - Permission granted  → reschedule all alarms from cache with exact timing.
 *  - Permission revoked  → cancel all scheduled alarms (they would fire
 *                          inexactly or not at all; better to cancel and let
 *                          the user re-enable from the app).
 */
class AlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduler    = NotificationScheduler(context)

        if (alarmManager.canScheduleExactAlarms()) {
            // Permission was just granted — reschedule with exact alarms.
            val cached = scheduler.getCachedContests()
            if (cached.isNotEmpty()) {
                scheduler.scheduleAll(cached)
                Log.d(TAG, "Exact alarm permission granted — rescheduled ${cached.size} contest(s).")
            } else {
                Log.d(TAG, "Exact alarm permission granted — cache empty, nothing to reschedule.")
            }
        } else {
            // Permission was revoked — cancel all pending alarms so they
            // don't fire inexactly and confuse the user.
            scheduler.cancelAll()
            Log.d(TAG, "Exact alarm permission revoked — cancelled all scheduled alarms.")
        }
    }

    companion object {
        private const val TAG = "AlarmPermissionReceiver"
    }
}
