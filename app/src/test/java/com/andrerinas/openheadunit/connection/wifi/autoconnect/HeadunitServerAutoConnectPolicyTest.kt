package com.andrerinas.openheadunit.connection.wifi.autoconnect

import com.andrerinas.openheadunit.connection.wifi.autoconnect.HeadunitServerAutoConnectPolicy as P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadunitServerAutoConnectPolicyTest {

    @Test
    fun `the start delay is held to the slider's range`() {
        assertEquals(0, P.clampStartDelaySec(-5))
        assertEquals(60, P.clampStartDelaySec(500))
        assertEquals(15, P.clampStartDelaySec(P.DEFAULT_START_DELAY_SEC))
    }

    @Test
    fun `the countdown runs only for Headunit Server Auto with automatic connection and a delay`() {
        assertTrue(P.countsDown(isHeadunitServerAuto = true, autoConnect = true, delaySec = 15, sessionLive = false))
        assertFalse(P.countsDown(isHeadunitServerAuto = false, autoConnect = true, delaySec = 15, sessionLive = false))
        assertFalse(P.countsDown(isHeadunitServerAuto = true, autoConnect = false, delaySec = 15, sessionLive = false))
        assertFalse(P.countsDown(isHeadunitServerAuto = true, autoConnect = true, delaySec = 0, sessionLive = false))
        assertFalse(P.countsDown(isHeadunitServerAuto = true, autoConnect = true, delaySec = 15, sessionLive = true))
    }

    @Test
    fun `automatic connection off leaves only the button, and only for Headunit Server`() {
        assertFalse(P.armsByItself(isHeadunitServerAuto = true, autoConnect = false))
        assertTrue(P.armsByItself(isHeadunitServerAuto = true, autoConnect = true))
        assertTrue(P.armsByItself(isHeadunitServerAuto = false, autoConnect = false))
    }

    @Test
    fun `leaving settings restarts the countdown unless the driver said not now`() {
        fun restarts(cancelled: Boolean = false, exited: Boolean = false, live: Boolean = false, auto: Boolean = true) =
            P.restartsAfterSettings(true, auto, cancelled, exited, live)
        assertTrue(restarts())
        assertFalse(restarts(cancelled = true))
        assertFalse(restarts(exited = true))
        assertFalse(restarts(live = true))
        assertFalse(restarts(auto = false))
        assertFalse(P.restartsAfterSettings(false, true, false, false, false))
    }

    @Test
    fun `after a join the retries come every 5 s for a minute, then the loop's own pace`() {
        (0 until P.fastRescanCount).forEach { assertEquals(5_000L, P.fastRescanDelayMs(it)) }
        assertEquals(60_000L, (0 until P.fastRescanCount).sumOf { P.fastRescanDelayMs(it)!! })
        assertNull(P.fastRescanDelayMs(P.fastRescanCount))
        assertNull(P.fastRescanDelayMs(-1))
    }

    @Test
    fun `an SSID loses its quotes and Android's placeholder means unknown`() {
        assertEquals("Galaxy S24", P.cleanSsid("\"Galaxy S24\""))
        assertNull(P.cleanSsid("<unknown ssid>"))
        assertNull(P.cleanSsid(""))
        assertNull(P.cleanSsid(null))
    }

    @Test
    fun `another network is only called out when both names are known`() {
        assertTrue(P.isOtherNetwork("Galaxy S24", "\"HomeWiFi\""))
        assertFalse(P.isOtherNetwork("Galaxy S24", "\"Galaxy S24\""))
        assertFalse(P.isOtherNetwork("", "HomeWiFi"))
        assertFalse(P.isOtherNetwork("Galaxy S24", "<unknown ssid>"))
    }

    @Test
    fun `the hotspot is missing only from a scan that saw something`() {
        assertEquals(true, P.phoneWifiVisible("Galaxy S24", listOf("HomeWiFi", "\"Galaxy S24\"")))
        assertEquals(false, P.phoneWifiVisible("Galaxy S24", listOf("HomeWiFi", "Cafe")))
        assertNull("no location access returns nothing", P.phoneWifiVisible("Galaxy S24", emptyList()))
        assertNull(P.phoneWifiVisible("Galaxy S24", null))
        assertNull("nothing learned yet", P.phoneWifiVisible("", listOf("HomeWiFi")))
    }

    @Test
    fun `refused is told apart from unanswered`() {
        assertTrue(P.isRefused("ConnectException", "failed to connect to /192.168.43.1 (port 5277): connect failed: ECONNREFUSED (Connection refused)"))
        assertFalse(P.isRefused("SocketTimeoutException", "failed to connect after 300ms"))
        assertFalse(P.isRefused("ConnectException", "ENETUNREACH (Network is unreachable)"))
        assertFalse(P.isRefused(null, null))
    }

    @Test
    fun `the subnet sweep is skipped only when the refusing gateway is the known phone`() {
        assertTrue(P.skipsSubnetSweep(true, "192.168.43.1", "192.168.43.1"))
        assertFalse(P.skipsSubnetSweep(true, "192.168.1.1", "192.168.43.1"))
        assertFalse("nothing learned yet", P.skipsSubnetSweep(true, "192.168.43.1", ""))
        assertFalse(P.skipsSubnetSweep(true, null, "192.168.43.1"))
        assertFalse("Wireless Helper sweeps too, and never skips", P.skipsSubnetSweep(false, "192.168.43.1", "192.168.43.1"))
    }

    @Test
    fun `the timeline is one line with skipped steps marked`() {
        val line = P.formatTimeline(
            listOf(P.Step("start->search", 15_000L), P.Step("WiFi", 8_200L), P.Step("phone", null)),
            "network=Galaxy S24, 5180 MHz"
        )
        assertEquals("Timeline: start->search 15.0s -> WiFi 8.2s -> phone - (network=Galaxy S24, 5180 MHz)", line)
        assertEquals("Timeline: WiFi 1.0s", P.formatTimeline(listOf(P.Step("WiFi", 1_000L)), null))
    }
}
