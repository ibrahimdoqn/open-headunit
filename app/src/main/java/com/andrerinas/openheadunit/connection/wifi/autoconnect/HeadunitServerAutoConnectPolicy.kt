package com.andrerinas.openheadunit.connection.wifi.autoconnect

/**
 * The decisions behind Headunit Server's automatic connection: the startup countdown, the quick
 * retries after a WiFi join, and the diagnostics the home card shows while it waits.
 *
 * Pure, so every rule is a unit test rather than a car.
 */
object HeadunitServerAutoConnectPolicy {

    const val MIN_START_DELAY_SEC = 0
    const val MAX_START_DELAY_SEC = 60
    const val DEFAULT_START_DELAY_SEC = 15
    const val START_DELAY_STEP_SEC = 5

    /** How long the phone's hotspot may stay out of the scan results before the card says so. */
    const val NOT_VISIBLE_AFTER_MS = 20_000L

    /** How often that is checked while the search waits for WiFi. */
    const val VISIBILITY_CHECK_INTERVAL_MS = 10_000L

    /**
     * After a WiFi join or a dropped session the first sweep runs at once; if it does not connect,
     * the next ones come every 5 s instead of the loop's usual 10 s, for a minute, and then the
     * loop's own pace (with its back-off for a phone that never answers) takes over. Each sweep
     * still runs one at a time, so the head unit server is never probed twice at once.
     */
    const val FAST_RESCAN_INTERVAL_MS = 5_000L
    const val FAST_RESCAN_COUNT = 12

    fun clampStartDelaySec(seconds: Int): Int = seconds.coerceIn(MIN_START_DELAY_SEC, MAX_START_DELAY_SEC)

    /**
     * Whether the service counts down before it starts searching. Only Headunit Server's Auto
     * mode, only with automatic connection on, and never over a session that is already up.
     */
    fun countsDown(isHeadunitServerAuto: Boolean, autoConnect: Boolean, delaySec: Int, sessionLive: Boolean): Boolean =
        isHeadunitServerAuto && autoConnect && delaySec > 0 && !sessionLive

    /**
     * Whether the service brings the search up by itself (at startup, after the countdown, after a
     * settings visit). With automatic connection off only the WiFi button does.
     */
    fun armsByItself(isHeadunitServerAuto: Boolean, autoConnect: Boolean): Boolean =
        !isHeadunitServerAuto || autoConnect

    /**
     * Whether closing the settings screen starts the countdown again. Not after the driver
     * cancelled or left Android Auto: both mean "not now", and the WiFi button is the way back.
     */
    fun restartsAfterSettings(
        isHeadunitServerAuto: Boolean,
        autoConnect: Boolean,
        cancelledByUser: Boolean,
        userExited: Boolean,
        sessionLive: Boolean,
    ): Boolean = isHeadunitServerAuto && autoConnect && !cancelledByUser && !userExited && !sessionLive

    /** The gap before quick retry [index] (from zero), or null once the quick retries are spent. */
    fun fastRescanDelayMs(index: Int): Long? =
        if (index in 0 until FAST_RESCAN_COUNT) FAST_RESCAN_INTERVAL_MS else null

    val fastRescanCount: Int get() = FAST_RESCAN_COUNT

    /**
     * An SSID as WifiInfo or a scan result gives it, without the quotes, or null when Android
     * withheld it: it does without location access, and says `<unknown ssid>`.
     */
    fun cleanSsid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (s.isEmpty() || s.equals("<unknown ssid>", ignoreCase = true) || s == "0x") return null
        return s
    }

    /**
     * Whether this unit joined a network that is not the phone's. Unknown on either side means
     * no verdict, so a unit without location access never hears it.
     */
    fun isOtherNetwork(phoneSsid: String?, currentSsid: String?): Boolean {
        val phone = cleanSsid(phoneSsid) ?: return false
        val current = cleanSsid(currentSsid) ?: return false
        return phone != current
    }

    /**
     * Whether the phone's hotspot is in the latest scan results: true, false, or null when that
     * cannot be told. An empty list is no answer either: without location access Android returns
     * nothing at all rather than a list without the phone.
     */
    fun phoneWifiVisible(phoneSsid: String?, visibleSsids: List<String?>?): Boolean? {
        val phone = cleanSsid(phoneSsid) ?: return null
        val seen = visibleSsids?.mapNotNull { cleanSsid(it) }
        if (seen.isNullOrEmpty()) return null
        return phone in seen
    }

    /**
     * Whether a failed dial to port 5277 was refused, as opposed to unanswered. Refused means
     * something is at that address and nothing listens on the port: on the phone's own hotspot,
     * the phone is there and Android Auto's head unit server is not running.
     */
    fun isRefused(exceptionClass: String?, message: String?): Boolean {
        if (exceptionClass == "ConnectException" || exceptionClass == "java.net.ConnectException") {
            val m = message.orEmpty()
            return m.contains("ECONNREFUSED", ignoreCase = true) || m.contains("refused", ignoreCase = true)
        }
        return false
    }

    /**
     * Whether a sweep may skip the 254 addresses after the gateway refused. Only when the gateway
     * is the phone itself, which is learned from the last session: on a shared network the
     * gateway is a router, and refusing says nothing about where the phone is.
     */
    fun skipsSubnetSweep(isHeadunitServerAuto: Boolean, refusedGatewayIp: String?, knownPhoneIp: String?): Boolean =
        isHeadunitServerAuto && !refusedGatewayIp.isNullOrBlank() && refusedGatewayIp == knownPhoneIp

    /** One timeline step: a label and the seconds it took, or null for a step that was skipped. */
    data class Step(val label: String, val ms: Long?)

    /** The single line a connection's timeline is logged as. */
    fun formatTimeline(steps: List<Step>, detail: String?): String {
        val body = steps.joinToString(" -> ") { step ->
            if (step.ms == null) "${step.label} -" else "${step.label} %.1fs".format(java.util.Locale.ROOT, step.ms / 1000.0)
        }
        return if (detail.isNullOrBlank()) "Timeline: $body" else "Timeline: $body ($detail)"
    }
}
