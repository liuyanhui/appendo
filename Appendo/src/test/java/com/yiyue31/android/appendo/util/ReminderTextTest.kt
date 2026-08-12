package com.yiyue31.android.appendo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTextTest {

    @Test
    fun `title uses first non-blank line`() {
        assertEquals("买牛奶", ReminderText.title("买牛奶"))
        assertEquals("第一行", ReminderText.title("第一行\n第二行\n第三行"))
        assertEquals("实际首行", ReminderText.title("\n\n实际首行\n第二行"))
    }

    @Test
    fun `title strips ZWSP isolation markers`() {
        val marked = "${EntryParser.ISOLATION_MARKER}## 2026-01-01 12:00:00"
        assertEquals("## 2026-01-01 12:00:00", ReminderText.title(marked))
    }

    @Test
    fun `title truncates overlong first line with ellipsis`() {
        val long = "A".repeat(200)
        val t = ReminderText.title(long)
        assertTrue("title <= 80 chars", t.length <= 80)
        assertTrue("title ends with ellipsis", t.endsWith("…"))
    }

    @Test
    fun `title of pure url is the url`() {
        val url = "https://example.com/x?y=1"
        assertEquals(url, ReminderText.title(url))
    }

    @Test
    fun `timeLabel shows today tomorrow or date`() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.of(2026, 8, 11)
        val now = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val sameDay = today.atTime(14, 30).atZone(zone).toInstant().toEpochMilli()
        val nextDay = today.plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val other = today.plusDays(5).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("今天 14:30", ReminderText.timeLabel(sameDay, now))
        assertEquals("明天 09:00", ReminderText.timeLabel(nextDay, now))
        assertEquals("08-16 09:00", ReminderText.timeLabel(other, now))
    }

    @Test
    fun `fullLabel prefixes recurrence for daily and weekly`() {
        val zone = java.time.ZoneId.systemDefault()
        val t = java.time.ZonedDateTime.of(2026, 8, 12, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("每天 09:30", ReminderText.fullLabel(ReminderMeta(t, false, 0, Recurrence.DAILY)))
        assertEquals("每周 09:30", ReminderText.fullLabel(ReminderMeta(t, false, 0, Recurrence.WEEKLY)))
        assertTrue(ReminderText.fullLabel(ReminderMeta(t, false, 0, Recurrence.NONE)).contains("09:30"))
    }
}
