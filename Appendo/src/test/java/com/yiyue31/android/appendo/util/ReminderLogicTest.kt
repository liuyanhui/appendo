package com.yiyue31.android.appendo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderLogicTest {

    private val NOW = 1_000_000L

    @Test
    fun `findMissed returns unfired entries whose effective trigger is at or before now`() {
        val reminders = mapOf(
            "a" to ReminderMeta(triggerAt = NOW - 10, fired = false, snoozedUntil = 0),
            "b" to ReminderMeta(triggerAt = NOW + 10, fired = false, snoozedUntil = 0),
            "c" to ReminderMeta(triggerAt = NOW - 100, fired = true, snoozedUntil = 0),
            "d" to ReminderMeta(triggerAt = NOW + 1000, fired = false, snoozedUntil = NOW - 5)
        )
        val missed = ReminderLogic.findMissed(reminders, NOW)
        assertEquals(setOf("a", "d"), missed.toSet())
    }

    @Test
    fun `findOrphans returns sidecar keys absent from existing entries`() {
        val orphans = ReminderLogic.findOrphans(
            sidecarKeys = setOf("t1", "t2", "t3"),
            existingEntryTimestamps = setOf("t2", "t4")
        )
        assertEquals(setOf("t1", "t3"), orphans)
    }

    @Test
    fun `computeSnoozeTrigger adds minutes in millis`() {
        assertEquals(NOW + 10 * 60_000L, ReminderLogic.computeSnoozeTrigger(NOW, 10))
        assertEquals(NOW + 60 * 60_000L, ReminderLogic.computeSnoozeTrigger(NOW, 60))
    }

    @Test
    fun `advanceToBooted marks missed as fired and lists them without touching future ones`() {
        val reminders = mapOf(
            "missed" to ReminderMeta(triggerAt = NOW - 50, fired = false, snoozedUntil = 0),
            "future" to ReminderMeta(triggerAt = NOW + 50, fired = false, snoozedUntil = 0),
            "done" to ReminderMeta(triggerAt = NOW - 50, fired = true, snoozedUntil = 0)
        )
        val (updated, toFire) = ReminderLogic.advanceToBooted(reminders, NOW)
        assertEquals(listOf("missed"), toFire)
        assertTrue(updated["missed"]!!.fired)
        assertFalse(updated["future"]!!.fired)
        assertTrue(updated["done"]!!.fired)
    }

    @Test
    fun `advanceToBooted is idempotent`() {
        val reminders = mapOf(
            "missed" to ReminderMeta(triggerAt = NOW - 50, fired = false, snoozedUntil = 0)
        )
        val (once, toFire1) = ReminderLogic.advanceToBooted(reminders, NOW)
        val (_, toFire2) = ReminderLogic.advanceToBooted(once, NOW)
        assertEquals(listOf("missed"), toFire1)
        assertTrue("second pass must not re-list", toFire2.isEmpty())
    }
}
