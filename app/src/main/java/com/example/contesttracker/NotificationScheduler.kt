package com.example.contesttracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class NotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

    fun getCachedContests(): List<ContestModel> {
        val json = prefs.getString("cached_contests", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ContestModel>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveToCache(contests: List<ContestModel>) {
        try {
            val json = Gson().toJson(contests)
            prefs.edit().putString("cached_contests", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scheduleAll(contests: List<ContestModel>) {
        saveToCache(contests)

        if (!prefs.getBoolean("enable_notifications", true)) {
            cancelAll()
            return
        }

        val now = System.currentTimeMillis()

        // ── Stale alarm reconciliation ────────────────────────────────────────
        // Load every notification ID registered in a previous scheduleAll() call.
        // After computing what will be scheduled this run, cancel anything that
        // is no longer needed (dropped contest, platform disabled, etc.).
        val previousIds = loadScheduledIds()
        val newIds = mutableSetOf<Int>()
        // ─────────────────────────────────────────────────────────────────────

        contests.forEach { contest ->
            val startMillis = ContestTimeUtils.startTimeMillis(contest.start) ?: return@forEach

            if (startMillis <= now) return@forEach

            if (!prefs.getBoolean("platform_${contest.platform.name}", true)) {
                cancelForContest(contest)
                return@forEach
            }

            // Per-contest opt-out: user tapped the bell icon to silence this contest.
            if (!ReminderPreferences.isEnabled(context, contest.id)) {
                cancelForContest(contest)
                return@forEach
            }

            // 1 Hour Before
            val oneHourBefore = startMillis - (60 * 60 * 1000)
            if (oneHourBefore > now) {
                val id = notificationId(contest, 1)
                schedule(contest, oneHourBefore, 1, "${contest.name} starts in 1 hour. Get ready!")
                newIds.add(id)
            }

            // 15 Mins Before
            val fifteenMinsBefore = startMillis - (15 * 60 * 1000)
            if (fifteenMinsBefore > now) {
                val id = notificationId(contest, 2)
                schedule(contest, fifteenMinsBefore, 2, "${contest.name} starts in 15 minutes. Join now!")
                newIds.add(id)
            }
        }

        // Cancel any alarm that was registered before but is not in this run's set.
        val staleIds = previousIds - newIds
        staleIds.forEach { cancelById(it) }
        if (staleIds.isNotEmpty()) {
            Log.d(TAG, "Cancelled ${staleIds.size} stale alarm(s): $staleIds")
        }

        // Persist the current set so the next call can diff against it.
        saveScheduledIds(newIds)
    }

    private fun schedule(contest: ContestModel, timeMillis: Long, typeOffset: Int, body: String) {
        // contest.id is a Long from the Clist API and can be very large (e.g. 17xxxxxx).
        // Directly casting to Int and multiplying overflows into negative / duplicate values,
        // which causes PendingIntents (and therefore alarms) to silently overwrite each other.
        // We take the positive modulo first to guarantee a safe, unique-enough Int bucket.
        val notificationId = notificationId(contest, typeOffset)
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("contest_name", contest.name)
            putExtra("platform", contest.platform.displayName)
            putExtra("url", contest.url)
            putExtra("body", body)
            putExtra("notification_id", notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                } else {
                    // Fallback to non-exact
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Last resort fallback
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }

    private fun cancelForContest(contest: ContestModel) {
        listOf(1, 2).forEach { offset -> cancelById(notificationId(contest, offset)) }
    }

    /**
     * Cancels every alarm that has ever been registered by this scheduler,
     * using the persisted ID set as the source of truth.
     * Clears the persisted set on completion.
     */
    fun cancelAll() {
        val ids = loadScheduledIds()
        ids.forEach { cancelById(it) }
        saveScheduledIds(emptySet())
        Log.d(TAG, "Cancelled all ${ids.size} scheduled alarm(s).")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Derives a stable, Int-safe notification ID from a contest and alarm type. */
    private fun notificationId(contest: ContestModel, typeOffset: Int): Int {
        val safeId = (contest.id % Int.MAX_VALUE).toInt().and(0x7FFF_FFFF)
        return (safeId * 10) + typeOffset
    }

    /** Cancels a single alarm by its notification ID. */
    private fun cancelById(notificationId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /** Persists the set of currently scheduled notification IDs to SharedPreferences. */
    private fun saveScheduledIds(ids: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    /** Loads the previously persisted set of scheduled notification IDs. */
    private fun loadScheduledIds(): Set<Int> {
        return prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    companion object {
        private const val TAG = "NotificationScheduler"
        private const val KEY_SCHEDULED_IDS = "scheduled_notification_ids"
    }
}
