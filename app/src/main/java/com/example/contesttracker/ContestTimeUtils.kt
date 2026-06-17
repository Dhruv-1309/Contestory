package com.example.contesttracker

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object ContestTimeUtils {
    private val utcPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    fun startTimeMillis(value: String): Long? {
        return utcPatterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val position = ParsePosition(0)
                val date = parser.parse(value, position)
                if (position.index == value.length) date?.time else null
            }.getOrNull()
        }
    }

    fun formatLocalDateTime(value: String): String {
        val millis = startTimeMillis(value) ?: return value
        return SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(millis))
    }

    fun formatDuration(seconds: Long): String {
        val days = TimeUnit.SECONDS.toDays(seconds)
        val hours = TimeUnit.SECONDS.toHours(seconds) % 24
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${minutes}m")
        }
    }

    fun formatCountdown(targetMillis: Long): String {
        val remaining = (targetMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        return "%02dd %02dh %02dm %02ds".format(days, hours, minutes, seconds)
    }
}
