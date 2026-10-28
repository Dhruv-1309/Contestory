package com.example.contesttracker

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
}
