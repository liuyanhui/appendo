package com.yiyue31.android.appendo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderLogicTest {

    private val NOW = 1_000_000L

    @Test
    fun `findOrphans returns sidecar keys absent from existing entries`() {
        val orphans = ReminderLogic.findOrphans(
            sidecarKeys = setOf("t1", "t2", "t3"),
            existingEntryTimestamps = setOf("t2", "t4")
        )
        assertEquals(setOf("t1", "t3"), orphans)
    }

    @Test
    fun `findOrphans keeps duplicate-ts reminder while any entry with the ts exists`() { // TD-020②
        // 重复时间戳下删除第一条后第二条仍在：key 不判孤儿（一条提醒覆盖同 ts 全部条目的语义边界）
        assertTrue(ReminderLogic.findOrphans(sidecarKeys = setOf("dup"), existingEntryTimestamps = setOf("dup")).isEmpty())
    }

    @Test
    fun `computeSnoozeTrigger adds minutes in millis`() {
        assertEquals(NOW + 10 * 60_000L, ReminderLogic.computeSnoozeTrigger(NOW, 10))
        assertEquals(NOW + 60 * 60_000L, ReminderLogic.computeSnoozeTrigger(NOW, 60))
    }

    @Test
    fun `nextOccurrence advances date keeping time of day`() {
        val zone = java.time.ZoneId.systemDefault()
        val base = java.time.ZonedDateTime.of(2026, 8, 12, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
        val daily = java.time.Instant.ofEpochMilli(ReminderLogic.nextOccurrence(base, Recurrence.DAILY)).atZone(zone)
        assertEquals(java.time.LocalDate.of(2026, 8, 13), daily.toLocalDate())
        assertEquals("09:30", daily.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
        val weekly = java.time.Instant.ofEpochMilli(ReminderLogic.nextOccurrence(base, Recurrence.WEEKLY)).atZone(zone)
        assertEquals(java.time.LocalDate.of(2026, 8, 19), weekly.toLocalDate())
    }

    @Test
    fun `advanceToNextFuture skips past occurrences to next future`() {
        val zone = java.time.ZoneId.systemDefault()
        val past = java.time.ZonedDateTime.of(2026, 8, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = java.time.ZonedDateTime.of(2026, 8, 12, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = java.time.Instant.ofEpochMilli(ReminderLogic.advanceToNextFuture(past, Recurrence.DAILY, now)).atZone(zone)
        assertEquals(java.time.LocalDate.of(2026, 8, 12), next.toLocalDate())
        assertEquals("09:00", next.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
    }

    @Test
    fun `bootDecide handles fired future past-once and past-recurring`() {
        val now = 1_000_000L
        val fired = ReminderLogic.bootDecide(ReminderMeta(now - 10, fired = true, snoozedUntil = 0), now)
        assertFalse(fired.fireMissed); assertFalse(fired.rearm)

        val future = ReminderLogic.bootDecide(ReminderMeta(now + 10, fired = false, snoozedUntil = 0), now)
        assertFalse(future.fireMissed); assertTrue(future.rearm)

        val pastOnce = ReminderLogic.bootDecide(ReminderMeta(now - 10, fired = false, snoozedUntil = 0, recurrence = Recurrence.NONE), now)
        assertTrue(pastOnce.fireMissed); assertFalse(pastOnce.rearm); assertTrue(pastOnce.newMeta.fired)

        val pastDaily = ReminderLogic.bootDecide(ReminderMeta(now - 10, fired = false, snoozedUntil = 0, recurrence = Recurrence.DAILY), now)
        assertTrue(pastDaily.fireMissed); assertTrue(pastDaily.rearm)
        assertFalse(pastDaily.newMeta.fired)
        assertTrue(pastDaily.newMeta.triggerAt > now)
    }
}
