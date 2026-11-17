package com.andrerinas.openheadunit.main

/**
 * What the WiFi line next to the main button's icon says: the network this unit is on, its band
 * and signal, or why there is none. Only for Headunit Server, where this unit joins the phone's
 * hotspot; Native and WiFi Direct bring their own network, so "not connected" would mislead there.
 *
 * Pure, so every case is a unit test rather than a head unit.
 */
object HomeWifiStatusPolicy {

    /** What WifiManager and ConnectivityManager say, read by [HomeWifiStatus]. */
    data class Facts(
        val wifiEnabled: Boolean,
        val connected: Boolean,
        /** As WifiInfo gives it, quotes and `<unknown ssid>` included. */
        val rawSsid: String? = null,
        val frequencyMhz: Int? = null,
        val rssiDbm: Int? = null,
        val scanning: Boolean = false,
    )

    enum class Kind { HIDDEN, SCANNING, OFF, NOT_CONNECTED, CONNECTED }

    data class Line(
        val kind: Kind,
        /** Null when Android withholds the name: it does without location access. */
        val ssid: String? = null,
        val band: Band? = null,
        val signalPercent: Int? = null,
    )

    enum class Band { GHZ_2_4, GHZ_5, GHZ_6 }

    /** Below this Android reports no signal at all (WifiInfo.INVALID_RSSI is -127). */
    private const val RSSI_FLOOR = -100
    private const val RSSI_FULL = -50

    fun line(isHeadunitServer: Boolean, cardIsWifi: Boolean, facts: Facts): Line = when {
        !isHeadunitServer || !cardIsWifi -> Line(Kind.HIDDEN)
        facts.scanning -> Line(Kind.SCANNING)
        !facts.wifiEnabled -> Line(Kind.OFF)
        !facts.connected -> Line(Kind.NOT_CONNECTED)
        else -> Line(Kind.CONNECTED, cleanSsid(facts.rawSsid), band(facts.frequencyMhz), signalPercent(facts.rssiDbm))
    }

    /** The name without its quotes, or null for Android's placeholder. */
    fun cleanSsid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (s.isEmpty() || s.equals("<unknown ssid>", ignoreCase = true) || s == "0x") return null
        return s
    }

    fun band(frequencyMhz: Int?): Band? = when (frequencyMhz) {
        null -> null
        in 2400..2500 -> Band.GHZ_2_4
        in 4900..5900 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> null
    }

    /** -100 dBm and below is 0 %, -50 dBm and above is 100 %; null when there is no reading. */
    fun signalPercent(rssiDbm: Int?): Int? {
        if (rssiDbm == null || rssiDbm < -126 || rssiDbm >= 0) return null
        val clamped = rssiDbm.coerceIn(RSSI_FLOOR, RSSI_FULL)
        return (clamped - RSSI_FLOOR) * 100 / (RSSI_FULL - RSSI_FLOOR)
    }
}
