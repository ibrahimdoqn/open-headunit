package com.andrerinas.openheadunit.connection.wifi.autoconnect

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.widget.Toast
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.connection.ConnectionStage
import com.andrerinas.openheadunit.connection.ConnectionStageTracker
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherMode
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Headunit Server's automatic connection, the part the driver sees: a countdown before the first
 * search, what the card says while the unit waits for the phone's WiFi, quick retries once it has
 * joined, a closed head unit server told apart from an absent phone, the phone's network learned
 * from the last session, and one timeline line per connection in the log.
 *
 * The search itself stays where it was (the launcher, the discovery loop and its WiFi parking in
 * AapService); this decides when it starts and says what it is doing. Main thread only.
 *
 * @param arm brings the wireless search up (AapService.initWifiModeWithOptionalWait).
 * @param disarm takes it down for the countdown (the launcher's stop).
 * @param userExited the driver left Android Auto, so nothing starts again by itself.
 * @param cancelledByUser the status pill's X stopped the wireless bring-up.
 */
class HeadunitServerAutoConnect(
    context: Context,
    private val scope: CoroutineScope,
    private val arm: () -> Unit,
    private val disarm: () -> Unit,
    private val userExited: () -> Boolean,
    private val cancelledByUser: () -> Boolean,
) {

    companion object {
        private val _countdown = MutableStateFlow<Int?>(null)

        /** Seconds left before the first search, or null when no countdown runs. For the home card. */
        val countdown: StateFlow<Int?> = _countdown.asStateFlow()
    }

    private val context = context.applicationContext
    private val settings: Settings get() = App.provide(context).settings
    private val commManager: CommManager get() = App.provide(context).commManager
    private val wifi = this.context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var countdownJob: Job? = null
    private var visibilityJob: Job? = null
    private var stateJob: Job? = null
    /** The card's Cancel during the countdown: nothing starts by itself until the WiFi button. */
    private var cancelledCountdown = false
    private var fastRescanIndex = -1
    private var serverClosedToldThisRun = false
    private var lastFoundIp: String? = null

    // Timeline marks, elapsed realtime; 0 means not reached.
    private var tStart = SystemClock.elapsedRealtime()
    private var tSearch = 0L
    private var tWifi = 0L
    private var wifiWasUp = false
    private var tPhone = 0L
    private var tHandshake = 0L
    private var timelineLogged = false

    private val isAuto: Boolean get() = settings.wifiConnectionMode == WifiLauncherMode.AUTO
    private val sessionLive: Boolean get() = commManager.isConnected

    /** Whether the service brings the search up by itself; see [HeadunitServerAutoConnectPolicy.armsByItself]. */
    fun armsByItself(): Boolean = HeadunitServerAutoConnectPolicy.armsByItself(isAuto, settings.headunitServerAutoConnect)

    /**
     * The service started. Watches the connection for the timeline and the phone's network, and
     * returns true when it takes the first search over: a countdown, or automatic connection off.
     */
    fun onServiceStart(): Boolean {
        watchConnection()
        resetTimeline()
        if (!isAuto) return false
        if (!settings.headunitServerAutoConnect) {
            AppLog.i("HuAutoConnect: automatic connection is off; the WiFi button connects")
            return true
        }
        if (beginCountdownIfWanted("startup")) return true
        tSearch = SystemClock.elapsedRealtime()
        return false
    }

    /** Counts down if the settings ask for it, and returns whether it did. */
    private fun beginCountdownIfWanted(reason: String): Boolean {
        val delaySec = settings.headunitServerStartDelaySec
        if (!HeadunitServerAutoConnectPolicy.countsDown(isAuto, settings.headunitServerAutoConnect, delaySec, sessionLive)) {
            return false
        }
        if (countdownJob?.isActive == true) {
            AppLog.d("HuAutoConnect: countdown already running ($reason)")
            return true
        }
        AppLog.i("HuAutoConnect: searching for the phone in ${delaySec}s ($reason)")
        cancelledCountdown = false
        disarm()
        ConnectionStageTracker.endAttempt()
        countdownJob = scope.launch {
            for (left in delaySec downTo 1) {
                _countdown.value = left
                delay(1_000L)
            }
            _countdown.value = null
            countdownJob = null
            startSearch("countdown over")
        }
        return true
    }

    private fun startSearch(reason: String) {
        if (sessionLive) return
        AppLog.i("HuAutoConnect: starting the Headunit Server search ($reason)")
        tSearch = SystemClock.elapsedRealtime()
        serverClosedToldThisRun = false
        arm()
    }

    val isCountingDown: Boolean get() = countdownJob?.isActive == true

    /** The card's "Connect now": skip what is left of the countdown. */
    fun connectNow() {
        if (countdownJob?.isActive != true) return
        stopCountdown()
        cancelledCountdown = false
        startSearch("connect now")
    }

    /** The card's Cancel during the countdown. Nothing starts by itself until the WiFi button. */
    fun cancelByUser() {
        if (countdownJob?.isActive != true) return
        AppLog.i("HuAutoConnect: countdown cancelled; the WiFi button connects")
        stopCountdown()
        cancelledCountdown = true
    }

    /** The WiFi button was pressed: it starts the search itself, so the countdown has no more to do. */
    fun onWifiButton() {
        cancelledCountdown = false
        if (countdownJob?.isActive == true) {
            AppLog.i("HuAutoConnect: the WiFi button ends the countdown")
            stopCountdown()
        }
        if (tSearch == 0L) tSearch = SystemClock.elapsedRealtime()
        serverClosedToldThisRun = false
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdown.value = null
    }

    /** The settings screen opened: the countdown waits for it to close. The stack itself stops in AapService. */
    fun onSettingsOpened() {
        if (countdownJob?.isActive == true) {
            AppLog.i("HuAutoConnect: the settings screen is open; the countdown starts again when it closes")
            stopCountdown()
        }
        stopVisibilityChecks()
    }

    /**
     * The settings screen closed. Returns true when this took the re-arm over: the countdown
     * starts again from the top, or the search starts at once when the delay is zero.
     */
    fun onSettingsClosed(): Boolean {
        val restart = HeadunitServerAutoConnectPolicy.restartsAfterSettings(
            isHeadunitServerAuto = isAuto,
            autoConnect = settings.headunitServerAutoConnect,
            cancelledByUser = cancelledCountdown || cancelledByUser(),
            userExited = userExited(),
            sessionLive = sessionLive,
        )
        if (!restart) return false
        if (!beginCountdownIfWanted("settings closed")) startSearch("settings closed")
        return true
    }

    // --- While the search waits for WiFi -------------------------------------------------------

    /** The search loop parked because this unit is not on WiFi. */
    fun onParkedForWifi() {
        if (!isAuto) return
        ConnectionStageTracker.showDiagnostic(ConnectionStage.WAITING_FOR_WIFI)
        if (visibilityJob?.isActive == true) return
        val phoneSsid = settings.headunitServerPhoneSsid
        if (phoneSsid.isBlank()) return
        visibilityJob = scope.launch {
            delay(HeadunitServerAutoConnectPolicy.NOT_VISIBLE_AFTER_MS)
            while (true) {
                when (HeadunitServerAutoConnectPolicy.phoneWifiVisible(phoneSsid, visibleSsids())) {
                    false -> {
                        if (ConnectionStageTracker.stage.value != ConnectionStage.PHONE_WIFI_NOT_VISIBLE) {
                            AppLog.w("HuAutoConnect: the phone's hotspot \"$phoneSsid\" is not in the scan results")
                        }
                        ConnectionStageTracker.showDiagnostic(ConnectionStage.PHONE_WIFI_NOT_VISIBLE)
                    }
                    true -> ConnectionStageTracker.showDiagnostic(ConnectionStage.WAITING_FOR_WIFI)
                    null -> {}
                }
                delay(HeadunitServerAutoConnectPolicy.VISIBILITY_CHECK_INTERVAL_MS)
            }
        }
    }

    /** The search is no longer waiting for WiFi: it found it, or it stopped. */
    fun stopVisibilityChecks() {
        visibilityJob?.cancel()
        visibilityJob = null
    }

    /**
     * This unit joined WiFi while the search waited. The card says so, and the next sweeps come
     * quickly instead of every 10 s.
     */
    fun onWifiJoined() {
        stopVisibilityChecks()
        if (tWifi == 0L) tWifi = SystemClock.elapsedRealtime()
        if (!isAuto) return
        val phoneSsid = settings.headunitServerPhoneSsid
        val current = currentSsid()
        if (HeadunitServerAutoConnectPolicy.isOtherNetwork(phoneSsid, current)) {
            AppLog.w("HuAutoConnect: joined \"$current\", not the phone's \"$phoneSsid\"; searching it anyway")
            ConnectionStageTracker.showDiagnostic(ConnectionStage.OTHER_WIFI)
        } else {
            ConnectionStageTracker.showDiagnostic(ConnectionStage.WIFI_JOINED)
        }
        startFastRescans("WiFi joined")
    }

    private fun startFastRescans(reason: String) {
        fastRescanIndex = 0
        AppLog.i("HuAutoConnect: quick retries for the phone ($reason)")
    }

    /**
     * Asked by the discovery loop for the gap before its next sweep: 5 s while the quick retries
     * last, else null for the loop's own. When they run out the card goes back to searching.
     */
    fun nextFastRescanDelayMs(): Long? {
        if (fastRescanIndex < 0) return null
        // Wireless Helper's sweeps share this loop; a mode changed mid-retries keeps its own pace.
        if (!isAuto) {
            fastRescanIndex = -1
            return null
        }
        val delayMs = HeadunitServerAutoConnectPolicy.fastRescanDelayMs(fastRescanIndex)
        if (delayMs == null) {
            fastRescanIndex = -1
            AppLog.i("HuAutoConnect: quick retries spent; back to the loop's own pace")
            ConnectionStageTracker.retreat(ConnectionStage.WIFI_JOINED, ConnectionStage.SEARCHING)
            return null
        }
        fastRescanIndex++
        return delayMs
    }

    /**
     * The gateway refused port 5277. From any thread. Returns whether the sweep may skip the rest
     * of the subnet, which only holds when the gateway is the phone learned from the last session.
     */
    fun onGatewayRefused(ip: String): Boolean {
        // Wireless Helper's hotspot strategies sweep with the same discovery; only Auto may skip.
        val skip = HeadunitServerAutoConnectPolicy.skipsSubnetSweep(isAuto, ip, settings.headunitServerPhoneIp)
        scope.launch {
            if (!isAuto || sessionLive) return@launch
            ConnectionStageTracker.showDiagnostic(ConnectionStage.SERVER_CLOSED)
            if (!serverClosedToldThisRun) {
                serverClosedToldThisRun = true
                AppLog.w("HuAutoConnect: $ip refuses port 5277: Android Auto's head unit server is not running on the phone")
                ToastUtils.showToast(context, context.getString(R.string.hu_server_closed_toast), Toast.LENGTH_LONG, force = true)
            }
        }
        return skip
    }

    /** The discovery found the head unit server. From the main thread. */
    fun onPhoneFound(ip: String) {
        lastFoundIp = ip
        if (tPhone == 0L) tPhone = SystemClock.elapsedRealtime()
        fastRescanIndex = -1
    }

    // --- The session: timeline and the phone's network -----------------------------------------

    private fun watchConnection() {
        if (stateJob?.isActive == true) return
        stateJob = scope.launch {
            commManager.connectionState.collect { state ->
                when (state) {
                    is CommManager.ConnectionState.HandshakeComplete -> onHandshake()
                    is CommManager.ConnectionState.TransportStarted -> onProjection()
                    is CommManager.ConnectionState.Disconnected -> onDisconnected()
                    else -> {}
                }
            }
        }
    }

    private fun onHandshake() {
        if (tHandshake == 0L) tHandshake = SystemClock.elapsedRealtime()
        stopCountdown()
        stopVisibilityChecks()
        fastRescanIndex = -1
        // Only a session this search found: Self Mode and a typed address are sockets too.
        val ip = lastFoundIp ?: return
        lastFoundIp = null
        if (!isAuto || !commManager.isWirelessSession) return
        val ssid = HeadunitServerAutoConnectPolicy.cleanSsid(currentSsid())
        if (ssid != null && ssid != settings.headunitServerPhoneSsid) {
            AppLog.i("HuAutoConnect: learned the phone's network \"$ssid\"")
            settings.headunitServerPhoneSsid = ssid
        }
        if (ip != settings.headunitServerPhoneIp) settings.headunitServerPhoneIp = ip
    }

    private fun onProjection() {
        if (timelineLogged || !isAuto) return
        timelineLogged = true
        val now = SystemClock.elapsedRealtime()
        val search = tSearch.takeIf { it > 0 }
        val wifiAt = tWifi.takeIf { it > 0 }
        val phoneAt = tPhone.takeIf { it > 0 }
        val handshakeAt = tHandshake.takeIf { it > 0 }
        val steps = listOf(
            HeadunitServerAutoConnectPolicy.Step("start->search", search?.let { it - tStart }),
            HeadunitServerAutoConnectPolicy.Step(if (wifiWasUp) "WiFi (already up)" else "WiFi",
                if (search != null && wifiAt != null) (wifiAt - search).coerceAtLeast(0) else null),
            HeadunitServerAutoConnectPolicy.Step("phone",
                phoneAt?.let { it - (wifiAt ?: search ?: tStart) }?.coerceAtLeast(0)),
            HeadunitServerAutoConnectPolicy.Step("handshake", handshakeAt?.let { it - (phoneAt ?: tStart) }?.coerceAtLeast(0)),
            HeadunitServerAutoConnectPolicy.Step("projection", now - (handshakeAt ?: tStart)),
        )
        AppLog.i("HuAutoConnect: " + HeadunitServerAutoConnectPolicy.formatTimeline(steps, networkDetail()))
    }

    private fun onDisconnected() {
        if (tHandshake == 0L) return
        if (!isAuto || userExited()) {
            // Leaving Android Auto means "not now": no quick retries behind the driver's back.
            resetTimeline()
            return
        }
        // A session that dropped: the next timeline starts now, and the retries come quickly
        // while the phone's network is still up.
        resetTimeline()
        tSearch = SystemClock.elapsedRealtime()
        startFastRescans("session ended")
    }

    private fun resetTimeline() {
        tStart = SystemClock.elapsedRealtime()
        tSearch = 0L
        tPhone = 0L
        tHandshake = 0L
        timelineLogged = false
        wifiWasUp = false
        tWifi = 0L
    }

    /** The search armed with WiFi already up, so the timeline has no join to wait for. */
    fun onArmedOnWifi() {
        if (tWifi == 0L) {
            tWifi = SystemClock.elapsedRealtime()
            wifiWasUp = true
        }
    }

    fun destroy() {
        stopCountdown()
        stopVisibilityChecks()
        stateJob?.cancel()
        stateJob = null
    }

    // --- WiFi facts ------------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun currentSsid(): String? = try {
        HeadunitServerAutoConnectPolicy.cleanSsid(wifi?.connectionInfo?.ssid)
    } catch (e: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun visibleSsids(): List<String?>? = try {
        @Suppress("DEPRECATION")
        wifi?.scanResults?.map { it.SSID }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun networkDetail(): String? = try {
        val info = wifi?.connectionInfo
        val ssid = HeadunitServerAutoConnectPolicy.cleanSsid(info?.ssid)
        val freq = info?.frequency?.takeIf { it > 0 }
        listOfNotNull(ssid?.let { "network=$it" }, freq?.let { "$it MHz" }).joinToString(", ").ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
