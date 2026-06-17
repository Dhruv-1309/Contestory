package com.example.contesttracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish() // Redirect to MainActivity or just close
    }

    companion object {
        const val PREFS_NAME = "clist_settings"
        const val KEY_USERNAME = "clist_username"
        const val KEY_API_KEY = "clist_api_key"
        const val HARDCODED_USER = "DhruvB"
        const val HARDCODED_KEY = "52a9f4ac2f61ac852316fa2cd68fa4fa6a67105b"
    }
}
