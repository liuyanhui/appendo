package com.yiyue31.android.appendo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderMetaCodecTest {

    @Test
    fun `round-trips a typical one-time reminder`() {
        val meta = ReminderMeta(triggerAt = 1754800000000L, fired = false, snoozedUntil = 0, recurrence = Recurrence.NONE)
        assertEquals(meta, ReminderMetaCodec.decode(ReminderMetaCodec.encode(meta)))
    }

    @Test
    fun `round-trips snoozed and recurring variants`() {
        val cases = listOf(
            ReminderMeta(1L, fired = true, snoozedUntil = 2L, recurrence = Recurrence.DAILY),
            ReminderMeta(99999L, fired = false, snoozedUntil = 100000L, recurrence = Recurrence.WEEKLY),
            ReminderMeta(0L, fired = false, snoozedUntil = 0L, recurrence = Recurrence.NONE)
        )
        cases.forEach { m ->
            assertEquals(m, ReminderMetaCodec.decode(ReminderMetaCodec.encode(m)))
        }
    }

    @Test
    fun `decode rejects malformed strings`() {
        assertNull(ReminderMetaCodec.decode(""))
        assertNull(ReminderMetaCodec.decode("abc|false|0|NONE"))   // triggerAt not a Long
        assertNull(ReminderMetaCodec.decode("1|maybe|0|NONE"))     // fired not a Boolean
        assertNull(ReminderMetaCodec.decode("1|false|0|HOURLY"))   // unknown recurrence
        assertNull(ReminderMetaCodec.decode("1|false|0"))          // too few segments
        assertNull(ReminderMetaCodec.decode("1|false|0|NONE|x"))   // too many segments
    }
}
