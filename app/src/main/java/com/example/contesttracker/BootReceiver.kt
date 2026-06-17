package com.example.contesttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            Log.d("BootReceiver", "Device booted. Rescheduling notifications.")
            val repository = ContestRepository()
            val scheduler = NotificationScheduler(context)
            
            // 1. Immediately reschedule from cache to be network-independent
            val cachedContests = scheduler.getCachedContests()
            if (cachedContests.isNotEmpty()) {
                Log.d("BootReceiver", "Found ${cachedContests.size} cached contests. Scheduling them immediately.")
                scheduler.scheduleAll(cachedContests)
            } else {
                Log.d("BootReceiver", "No cached contests found to schedule.")
            }
            
            // 2. Refresh from network in background to get any new updates
            CoroutineScope(Dispatchers.IO).launch {
                // Try up to 3 times with delay to handle transient network issues on boot
                var attempts = 0
                var success = false
                while (attempts < 3 && !success) {
                    attempts++
                    repository.fetchUpcomingContests(SettingsActivity.HARDCODED_USER, SettingsActivity.HARDCODED_KEY)
                        .onSuccess { contests ->
                            Log.d("BootReceiver", "Successfully fetched contests from network. Updating schedule.")
                            scheduler.scheduleAll(contests)
                            success = true
                        }
                        .onFailure { exception ->
                            Log.w("BootReceiver", "Attempt $attempts to fetch contests failed.", exception)
                            if (attempts < 3) {
                                try {
                                    kotlinx.coroutines.delay(10000) // wait 10 seconds before retry
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }
                }
            }
        }
    }
}
