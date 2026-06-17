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
    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

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
            cancelAll(contests)
            return
        }

        val now = System.currentTimeMillis()

        contests.forEach { contest ->
            val startMillis = ContestTimeUtils.startTimeMillis(contest.start) ?: return@forEach
            
            if (startMillis <= now) return@forEach

            if (!prefs.getBoolean("platform_${contest.platform.name}", true)) {
                cancelForContest(contest)
                return@forEach
            }

            // 1 Hour Before
            val oneHourBefore = startMillis - (60 * 60 * 1000)
            if (oneHourBefore > now) {
                schedule(contest, oneHourBefore, 1, "${contest.name} starts in 1 hour. Get ready!")
            }

            // 15 Mins Before
            val fifteenMinsBefore = startMillis - (15 * 60 * 1000)
            if (fifteenMinsBefore > now) {
                schedule(contest, fifteenMinsBefore, 2, "${contest.name} starts in 15 minutes. Join now!")
            }
        }
    }

    private fun schedule(contest: ContestModel, timeMillis: Long, typeOffset: Int, body: String) {
        val notificationId = (contest.id.toInt() * 10) + typeOffset
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
        listOf(1, 2).forEach { offset ->
            val notificationId = (contest.id.toInt() * 10) + offset
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    fun cancelAll(contests: List<ContestModel>) {
        contests.forEach { cancelForContest(it) }
    }
}
