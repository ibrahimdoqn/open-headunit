package com.andrerinas.openheadunit.connection.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Waits for the head unit to join a WiFi network before the Headunit Server scan starts.
 *
 * Many aftermarket units (Android 10 especially) take a long time to auto-join the phone's
 * hotspot on their own. A one-shot discovery sweep started before the join simply finds nothing.
 * This helper:
 *  1. returns at once if a WiFi network is already up,
 *  2. otherwise turns WiFi on (only possible below Android 10) or opens the WiFi panel,
 *  3. asks the WiFi framework for scans, which is what makes it auto-join saved networks sooner,
 *  4. calls [onReady] as soon as a WiFi network becomes available, or [onTimeout] after [timeoutMs].
 *
 * Only one wait runs at a time; starting a new one cancels the previous.
 */
object WifiJoinWaiter {

    private const val TAG = "WifiJoinWaiter"
    private const val SCAN_INTERVAL_MS = 8_000L
    /** Short pause after the join so DHCP and routes settle before the probe of port 5277. */
    private const val SETTLE_DELAY_MS = 1_500L

    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null

    val isWaiting: Boolean get() = session != null

    /** True when any WiFi network is connected (not only the default network). */
    @SuppressLint("MissingPermission")
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.allNetworks.any { network ->
                    cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
            } else {
                @Suppress("DEPRECATION")
                cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected == true
            }
        } catch (e: Exception) {
            AppLog.w("$TAG: could not read network state: ${e.message}")
            false
        }
    }

    fun cancel() {
        session?.stop("cancelled")
        session = null
    }

    /**
     * Starts waiting. [onReady] / [onTimeout] / [onWifiOff] run on the main thread.
     * [onWifiOff] is called when WiFi is disabled and the app could not turn it on itself; the
     * wait keeps running so the join is still caught once the user switches WiFi on.
     */
    fun waitForWifi(
        context: Context,
        timeoutMs: Long,
        onReady: () -> Unit,
        onTimeout: () -> Unit,
        onWifiOff: () -> Unit = {}
    ) {
        cancel()
        val app = context.applicationContext
        if (isWifiConnected(app)) {
            AppLog.i("$TAG: WiFi already connected")
            onReady()
            return
        }
        val s = Session(app, timeoutMs, onReady, onTimeout, onWifiOff)
        session = s
        s.start()
    }

    private class Session(
        val context: Context,
        val timeoutMs: Long,
        val onReady: () -> Unit,
        val onTimeout: () -> Unit,
        val onWifiOff: () -> Unit
    ) {
        private val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        private var callback: ConnectivityManager.NetworkCallback? = null
        private var receiver: BroadcastReceiver? = null
        private var finished = false

        private val scanTick = object : Runnable {
            override fun run() {
                if (finished) return
                requestScan()
                handler.postDelayed(this, SCAN_INTERVAL_MS)
            }
        }

        private val timeoutTick = Runnable {
            if (finished) return@Runnable
            AppLog.w("$TAG: no WiFi network after ${timeoutMs / 1000}s")
            stop("timeout")
            if (session === this) session = null
            onTimeout()
        }

        fun start() {
            AppLog.i("$TAG: waiting up to ${timeoutMs / 1000}s for a WiFi network")
            ensureWifiEnabled()
            registerListeners()
            handler.post(scanTick)
            handler.postDelayed(timeoutTick, timeoutMs)
        }

        fun stop(reason: String) {
            if (finished) return
            finished = true
            AppLog.i("$TAG: stopped ($reason)")
            handler.removeCallbacks(scanTick)
            handler.removeCallbacks(timeoutTick)
            callback?.let { cb -> try { cm?.unregisterNetworkCallback(cb) } catch (_: Exception) {} }
            callback = null
            receiver?.let { r -> try { context.unregisterReceiver(r) } catch (_: Exception) {} }
            receiver = null
        }

        private fun joined(source: String) {
            if (finished) return
            AppLog.i("$TAG: WiFi joined ($source)")
            stop("joined")
            if (session === this) session = null
            handler.postDelayed({ onReady() }, SETTLE_DELAY_MS)
        }

        @SuppressLint("MissingPermission")
        private fun ensureWifiEnabled() {
            val w = wifi ?: return
            if (w.isWifiEnabled) return
            // setWifiEnabled() still works for apps below Android 10; above it always returns false.
            @Suppress("DEPRECATION")
            val turnedOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                try { w.setWifiEnabled(true) } catch (_: Exception) { false }
            if (turnedOn) {
                AppLog.i("$TAG: WiFi switched on")
                return
            }
            AppLog.i("$TAG: WiFi is off and cannot be switched on by the app; opening the WiFi panel")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.startActivity(
                        Intent(SystemSettings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    AppLog.w("$TAG: WiFi panel unavailable: ${e.message}")
                }
            }
            onWifiOff()
        }

        @SuppressLint("MissingPermission")
        private fun requestScan() {
            val w = wifi ?: return
            if (!w.isWifiEnabled) return
            // Deprecated and throttled (4 per 2 min in the foreground), but still honoured, and the
            // scan results it produces are what the framework's auto-join acts on.
            try {
                @Suppress("DEPRECATION")
                val ok = w.startScan()
                AppLog.d("$TAG: startScan -> $ok")
            } catch (e: Exception) {
                AppLog.d("$TAG: startScan refused: ${e.message}")
            }
            // Below Android 10 the app may also nudge the supplicant directly.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try {
                    @Suppress("DEPRECATION")
                    w.reconnect()
                } catch (_: Exception) {}
            }
        }

        @SuppressLint("MissingPermission")
        private fun registerListeners() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && cm != null) {
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        handler.post { joined("NetworkCallback") }
                    }
                }
                try {
                    cm.registerNetworkCallback(
                        NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .build(),
                        cb
                    )
                    callback = cb
                    return
                } catch (e: Exception) {
                    AppLog.w("$TAG: NetworkCallback failed, falling back to broadcast: ${e.message}")
                }
            }
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (isWifiConnected(context)) joined("broadcast")
                }
            }
            @Suppress("DEPRECATION")
            context.registerReceiver(r, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
            receiver = r
        }
    }
}
