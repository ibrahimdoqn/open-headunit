package com.andrerinas.openheadunit.connection.wifi

/**
 * Whether the Headunit Server's WiFi button joins WiFi before it searches, and how it paces that.
 *
 * The one-shot search probes this unit's own subnet. A unit that has not joined the phone's
 * hotspot yet has no subnet to probe, so the search ended at once and the user had to press again.
 * Android 10 units are the slow case: the platform's own reconnect scans back off while the
 * screen is on and nothing is connected, so a saved hotspot can sit in range for a minute.
 *
 * Pure, so every case is a unit test rather than a head unit.
 */
object WifiJoinWaitPolicy {

    const val MIN_TIMEOUT_SEC = 10
    const val MAX_TIMEOUT_SEC = 120
    const val DEFAULT_TIMEOUT_SEC = 30

    /** After the join, before the probe: the hotspot's DHCP lease and routes take a moment. */
    const val SETTLE_DELAY_MS = 1_500L

    /**
     * From Android 9 a foreground app gets four scans in any two minutes, and the platform refuses
     * the rest. The four come early, while a hotspot that is in range is found fastest; each later
     * scan waits until the one four before it has left the window, so none is planned to fail.
     */
    private val OPENING_SCANS_MS = longArrayOf(0L, 10_000L, 25_000L, 45_000L)
    const val SCAN_BUDGET_WINDOW_MS = 120_000L

    fun clampTimeoutSec(seconds: Int): Int = seconds.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC)

    /** Only a unit that is not on WiFi yet waits; one that is searches at once, as before. */
    fun shouldWait(enabled: Boolean, wifiConnected: Boolean): Boolean = enabled && !wifiConnected

    /**
     * Whether the Headunit Server's background search loop parks instead of sweeping.
     *
     * Off WiFi the sweep has no subnet of its own: it either gives up or walks whatever other
     * interface is up, every 10 s, and does nothing to bring the join any closer. Parked, the loop
     * asks for WiFi scans instead and comes back the moment a WiFi network arrives. Only Auto:
     * the other modes bring their own network up, or never search at all.
     */
    fun shouldParkDiscovery(isHeadunitServerAuto: Boolean, enabled: Boolean, wifiConnected: Boolean): Boolean =
        isHeadunitServerAuto && shouldWait(enabled, wifiConnected)

    /** Time from the start of the wait to scan number [index], counting from zero. */
    fun scanAtMs(index: Int): Long {
        require(index >= 0) { "index must not be negative" }
        if (index < OPENING_SCANS_MS.size) return OPENING_SCANS_MS[index]
        return scanAtMs(index - OPENING_SCANS_MS.size) + SCAN_BUDGET_WINDOW_MS
    }

    /** Wait between scan [index] and the scan after it. */
    fun delayAfterScanMs(index: Int): Long = scanAtMs(index + 1) - scanAtMs(index)
}
