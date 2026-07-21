package com.yiyue31.android.appendo.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DuplicateHintThrottleTest {

    private var fakeNow = 0L

    @Before
    fun setup() {
        fakeNow = 0L
        DuplicateHintThrottle.clock = { fakeNow }
        DuplicateHintThrottle.resetForTest()
    }

    @After
    fun teardown() {
        DuplicateHintThrottle.clock = { System.currentTimeMillis() }
        DuplicateHintThrottle.resetForTest()
    }

    @Test
    fun firstShow_returnsTrue() {
        assertTrue(DuplicateHintThrottle.shouldShow("x"))
    }

    @Test
    fun withinWindow_returnsFalse() {
        assertTrue(DuplicateHintThrottle.shouldShow("x"))
        fakeNow = 4000 // < 5000
        assertFalse(DuplicateHintThrottle.shouldShow("x"))
    }

    @Test
    fun atAndAfterWindow_returnsTrue() {
        assertTrue(DuplicateHintThrottle.shouldShow("x"))
        fakeNow = 5000 // >= 5000
        assertTrue(DuplicateHintThrottle.shouldShow("x"))
    }

    @Test
    fun differentContent_independent() {
        assertTrue(DuplicateHintThrottle.shouldShow("a"))
        assertTrue(DuplicateHintThrottle.shouldShow("b")) // 不同内容不互相影响
    }

    @Test
    fun threeTimesWithinWindow_showsOnceThenAgainAfterWindow() {
        assertTrue(DuplicateHintThrottle.shouldShow("x"))
        fakeNow = 1000
        assertFalse(DuplicateHintThrottle.shouldShow("x"))
        fakeNow = 3000
        assertFalse(DuplicateHintThrottle.shouldShow("x"))
        fakeNow = 5001 // 距上次（0）>= 5000
        assertTrue(DuplicateHintThrottle.shouldShow("x"))
    }
}
