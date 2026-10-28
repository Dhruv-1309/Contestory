package com.example.contesttracker

import android.content.SharedPreferences
import com.example.contesttracker.update.VersionComparator
import org.junit.Assert.*
import org.junit.Test

class ContestTrackerTest {

    @Test
    fun testStartTimeMillisParsing() {
        val isoZ = "2026-10-29T10:00:00Z"
        val millisZ = ContestTimeUtils.startTimeMillis(isoZ)
        assertNotNull(millisZ)

        val isoOffset = "2026-10-29T10:00:00+00:00"
        val millisOffset = ContestTimeUtils.startTimeMillis(isoOffset)
        assertEquals(millisZ, millisOffset)

        val invalid = "not-a-date"
        assertNull(ContestTimeUtils.startTimeMillis(invalid))
    }

    @Test
    fun testFormatDuration() {
        val duration2h15m = 2 * 3600L + 15 * 60L
        assertEquals("2h 15m", ContestTimeUtils.formatDuration(duration2h15m))

        val duration1d2h = 24 * 3600L + 2 * 3600L
        assertEquals("1d 2h 0m", ContestTimeUtils.formatDuration(duration1d2h))
    }

    @Test
    fun testPlatformResolution() {
        assertEquals(Platform.CODEFORCES, Platform.fromResourceId(1))
        assertEquals(Platform.CODECHEF, Platform.fromResourceId(2))
        assertEquals(Platform.LEETCODE, Platform.fromResourceId(102))
        assertEquals(Platform.ATCODER, Platform.fromResourceId(93))
        assertNull(Platform.fromResourceId(99999))
    }

    @Test
    fun testContestModelCachedMillis() {
        val contest = ContestModel(
            id = 12345L,
            name = "Test Contest",
            resourceId = 1,
            start = "2026-10-29T10:00:00Z",
            end = "2026-10-29T12:00:00Z",
            durationSeconds = 7200L,
            url = "https://codeforces.com/contest/12345"
        )

        assertEquals(Platform.CODEFORCES, contest.platform)
        assertTrue(contest.startMillis > 0)
        assertEquals(contest.startMillis + 7200_000L, contest.endMillis)
        // Verify caching returns same value
        assertEquals(contest.startMillis, contest.startMillis)
    }

    @Test
    fun testVersionComparator() {
        assertTrue(VersionComparator.isNewerVersion("1.2.11", "1.2.12"))
        assertTrue(VersionComparator.isNewerVersion("1.9.9", "2.0.0"))
        assertFalse(VersionComparator.isNewerVersion("1.2.11", "1.2.11"))
        assertFalse(VersionComparator.isNewerVersion("1.2.11", "1.2.10"))
    }

    @Test
    fun testPlatformPreferencesKeys() {
        assertEquals("platform_CODEFORCES", PlatformPreferences.key(Platform.CODEFORCES))
        assertEquals("platform_LEETCODE", PlatformPreferences.key(Platform.LEETCODE))
        assertEquals("platform_CODECHEF", PlatformPreferences.key(Platform.CODECHEF))
        assertEquals("platform_ATCODER", PlatformPreferences.key(Platform.ATCODER))
    }

    @Test
    fun testPlatformPreferencesAreAllPlatformsSelected() {
        assertTrue(PlatformPreferences.areAllPlatformsSelected(Platform.entries.toSet()))
        assertFalse(PlatformPreferences.areAllPlatformsSelected(setOf(Platform.CODEFORCES, Platform.LEETCODE)))
        assertFalse(PlatformPreferences.areAllPlatformsSelected(emptySet()))
    }

    @Test
    fun testPlatformPreferencesGetAndSet() {
        val fakePrefs = FakeSharedPreferences()

        // By default, all platforms are enabled
        Platform.entries.forEach { platform ->
            assertTrue(PlatformPreferences.isPlatformEnabled(fakePrefs, platform))
        }
        assertEquals(Platform.entries.toSet(), PlatformPreferences.getSelectedPlatforms(fakePrefs))
        assertTrue(PlatformPreferences.areAllPlatformsSelected(PlatformPreferences.getSelectedPlatforms(fakePrefs)))

        // Disable LEETCODE
        PlatformPreferences.setPlatformEnabled(fakePrefs, Platform.LEETCODE, false)
        assertFalse(PlatformPreferences.isPlatformEnabled(fakePrefs, Platform.LEETCODE))
        assertTrue(PlatformPreferences.isPlatformEnabled(fakePrefs, Platform.CODEFORCES))

        val currentSelected = PlatformPreferences.getSelectedPlatforms(fakePrefs)
        assertEquals(setOf(Platform.CODEFORCES, Platform.CODECHEF, Platform.ATCODER), currentSelected)
        assertFalse(PlatformPreferences.areAllPlatformsSelected(currentSelected))

        // Re-enable LEETCODE
        PlatformPreferences.setPlatformEnabled(fakePrefs, Platform.LEETCODE, true)
        assertTrue(PlatformPreferences.isPlatformEnabled(fakePrefs, Platform.LEETCODE))
        assertEquals(Platform.entries.toSet(), PlatformPreferences.getSelectedPlatforms(fakePrefs))
        assertTrue(PlatformPreferences.areAllPlatformsSelected(PlatformPreferences.getSelectedPlatforms(fakePrefs)))
    }

    @Test
    fun testPlatformPreferencesSetAllPlatforms() {
        val fakePrefs = FakeSharedPreferences()

        // Disable all
        PlatformPreferences.setAllPlatforms(fakePrefs, false)
        assertTrue(PlatformPreferences.getSelectedPlatforms(fakePrefs).isEmpty())
        assertFalse(PlatformPreferences.areAllPlatformsSelected(PlatformPreferences.getSelectedPlatforms(fakePrefs)))
        Platform.entries.forEach { platform ->
            assertFalse(PlatformPreferences.isPlatformEnabled(fakePrefs, platform))
        }

        // Enable all
        PlatformPreferences.setAllPlatforms(fakePrefs, true)
        assertEquals(Platform.entries.toSet(), PlatformPreferences.getSelectedPlatforms(fakePrefs))
        assertTrue(PlatformPreferences.areAllPlatformsSelected(PlatformPreferences.getSelectedPlatforms(fakePrefs)))
        Platform.entries.forEach { platform ->
            assertTrue(PlatformPreferences.isPlatformEnabled(fakePrefs, platform))
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class Editor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { if (key != null) temp[key] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { if (key != null) removed.add(key); return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) prefs.map.clear()
                removed.forEach { prefs.map.remove(it) }
                prefs.map.putAll(temp)
            }
        }
    }
}
