package com.andrerinas.openheadunit.main

import com.andrerinas.openheadunit.main.HomeWifiStatusPolicy.Band
import com.andrerinas.openheadunit.main.HomeWifiStatusPolicy.Facts
import com.andrerinas.openheadunit.main.HomeWifiStatusPolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeWifiStatusPolicyTest {

    private val onPhone = Facts(wifiEnabled = true, connected = true, rawSsid = "\"Galaxy S24\"", frequencyMhz = 5180, rssiDbm = -65)

    private fun line(facts: Facts, headunitServer: Boolean = true, cardIsWifi: Boolean = true) =
        HomeWifiStatusPolicy.line(headunitServer, cardIsWifi, facts)

    @Test
    fun `shown only for Headunit Server while the card is the WiFi one`() {
        assertEquals(Kind.HIDDEN, line(onPhone, headunitServer = false).kind)
        assertEquals(Kind.HIDDEN, line(onPhone, cardIsWifi = false).kind)
        assertEquals(Kind.CONNECTED, line(onPhone).kind)
    }

    @Test
    fun `a connected unit shows the network, its band and its signal`() {
        val l = line(onPhone)
        assertEquals("Galaxy S24", l.ssid)
        assertEquals(Band.GHZ_5, l.band)
        assertEquals(70, l.signalPercent)
    }

    @Test
    fun `without location access the name is left out, not made up`() {
        val l = line(onPhone.copy(rawSsid = "<unknown ssid>"))
        assertEquals(Kind.CONNECTED, l.kind)
        assertNull(l.ssid)
    }

    @Test
    fun `off and not connected are told apart`() {
        assertEquals(Kind.OFF, line(Facts(wifiEnabled = false, connected = false)).kind)
        assertEquals(Kind.NOT_CONNECTED, line(Facts(wifiEnabled = true, connected = false)).kind)
    }

    @Test
    fun `scanning wins over everything but the mode`() {
        assertEquals(Kind.SCANNING, line(onPhone.copy(scanning = true)).kind)
        assertEquals(Kind.SCANNING, line(Facts(wifiEnabled = true, connected = false, scanning = true)).kind)
        assertEquals(Kind.HIDDEN, line(onPhone.copy(scanning = true), headunitServer = false).kind)
    }

    @Test
    fun `bands by frequency`() {
        assertEquals(Band.GHZ_2_4, HomeWifiStatusPolicy.band(2437))
        assertEquals(Band.GHZ_5, HomeWifiStatusPolicy.band(5200))
        assertEquals(Band.GHZ_6, HomeWifiStatusPolicy.band(5955))
        assertNull(HomeWifiStatusPolicy.band(null))
        assertNull(HomeWifiStatusPolicy.band(-1))
    }

    @Test
    fun `signal runs from 0 at -100 dBm to 100 at -50 dBm`() {
        assertEquals(0, HomeWifiStatusPolicy.signalPercent(-100))
        assertEquals(0, HomeWifiStatusPolicy.signalPercent(-110))
        assertEquals(50, HomeWifiStatusPolicy.signalPercent(-75))
        assertEquals(100, HomeWifiStatusPolicy.signalPercent(-50))
        assertEquals(100, HomeWifiStatusPolicy.signalPercent(-30))
        assertNull("Android's no-reading value", HomeWifiStatusPolicy.signalPercent(-127))
        assertNull(HomeWifiStatusPolicy.signalPercent(null))
    }
}
