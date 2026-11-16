package com.andrerinas.openheadunit.connection.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiJoinWaitPolicyTest {

    @Test
    fun `a unit already on WiFi searches at once`() {
        assertFalse(WifiJoinWaitPolicy.shouldWait(enabled = true, wifiConnected = true))
    }

    @Test
    fun `a unit not on WiFi waits only when the setting is on`() {
        assertTrue(WifiJoinWaitPolicy.shouldWait(enabled = true, wifiConnected = false))
        assertFalse(WifiJoinWaitPolicy.shouldWait(enabled = false, wifiConnected = false))
    }

    @Test
    fun `the timeout is held to the slider's range`() {
        assertEquals(WifiJoinWaitPolicy.MIN_TIMEOUT_SEC, WifiJoinWaitPolicy.clampTimeoutSec(0))
        assertEquals(WifiJoinWaitPolicy.MAX_TIMEOUT_SEC, WifiJoinWaitPolicy.clampTimeoutSec(600))
        assertEquals(45, WifiJoinWaitPolicy.clampTimeoutSec(45))
        assertEquals(
            WifiJoinWaitPolicy.DEFAULT_TIMEOUT_SEC,
            WifiJoinWaitPolicy.clampTimeoutSec(WifiJoinWaitPolicy.DEFAULT_TIMEOUT_SEC)
        )
    }

    @Test
    fun `the first scan is immediate and the rest only move forward`() {
        assertEquals(0L, WifiJoinWaitPolicy.scanAtMs(0))
        (0 until 10).forEach { i ->
            assertTrue("scan ${i + 1} is not after scan $i", WifiJoinWaitPolicy.delayAfterScanMs(i) > 0L)
        }
    }

    @Test
    fun `the scans fit Android's foreground budget of four in two minutes`() {
        val times = (0 until 16).map { WifiJoinWaitPolicy.scanAtMs(it) }
        assertEquals("the opening burst", 4, times.count { it < 60_000L })
        times.forEach { start ->
            val inWindow = times.count { it >= start && it < start + WifiJoinWaitPolicy.SCAN_BUDGET_WINDOW_MS }
            assertTrue("$inWindow scans in the two minutes from ${start}ms", inWindow <= 4)
        }
    }

    @Test
    fun `the fifth scan waits for the first to leave the window`() {
        assertEquals(WifiJoinWaitPolicy.SCAN_BUDGET_WINDOW_MS, WifiJoinWaitPolicy.scanAtMs(4))
    }
}
