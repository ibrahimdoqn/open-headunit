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
import android.os.SystemClock
import android.provider.Settings as SystemSettings
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Waits for this unit to join a WiFi network, for the Headunit Server's searches.
 *
 * While it waits it asks the platform for scans, which is what makes it join a saved network
 * sooner; it does not pick a network itself. Pacing comes from [WifiJoinWaitPolicy]. It never
 * gives up on its own: the background search loop waits as long as it takes, and the WiFi
 * button bounds its own wait.
 *
 * One wait at a time, on the main thread. [start] while waiting keeps the scans going.
 */
class WifiJoinWaiter(context: Context) {

    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val wifi = this.context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val cm = this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var onJoined: (() -> Unit)? = null
    private var scanIndex = 0
    private var startedAtMs = 0L

    val isWaiting: Boolean get() = onJoined != null

    private val scanTick = object : Runnable {
        override fun run() {
            if (!isWaiting) return
            requestScan()
            handler.postDelayed(this, WifiJoinWaitPolicy.delayAfterScanMs(scanIndex++))
        }
    }

    fun isWifiConnected(): Boolean = isWifiConnected(context)

    /**
     * Starts waiting, or keeps the wait already running; [onJoined] runs once the join has
     * settled. Already waiting, the scan schedule carries on: restarting it would spend the
     * platform's scan budget again for nothing.
     */
    fun start(reason: String, onJoined: () -> Unit) {
        if (isWaiting) {
            this.onJoined = onJoined
            AppLog.d("WifiJoinWaiter: already waiting ($reason)")
            return
        }
        this.onJoined = onJoined
        scanIndex = 0
        startedAtMs = SystemClock.elapsedRealtime()
        AppLog.i("WifiJoinWaiter: not on WiFi; scanning until a WiFi network is joined ($reason)")
        registerListener()
        handler.post(scanTick)
    }

    /** Ends the wait without calling anything back. Safe when nothing is waiting. */
    fun stop() {
        if (!isWaiting) return
        AppLog.i("WifiJoinWaiter: stopped after ${elapsedSec()}s")
        onJoined = null
        handler.removeCallbacks(scanTick)
        callback?.let { try { cm?.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        callback = null
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    private fun joined(source: String) {
        val then = onJoined ?: return
        AppLog.i("WifiJoinWaiter: joined WiFi after ${elapsedSec()}s ($source)")
        stop()
        handler.postDelayed(then, WifiJoinWaitPolicy.SETTLE_DELAY_MS)
    }

    private fun elapsedSec() = (SystemClock.elapsedRealtime() - startedAtMs) / 1000

    /**
     * Whether WiFi is on, switching it on first when the user asked for a connection. Below
     * Android 10 the app may switch it on itself; above it only the WiFi panel can ask, which is
     * opened only for a user's press, never for the background loop.
     */
    @SuppressLint("MissingPermission")
    fun ensureWifiEnabled(userAsked: Boolean): Boolean {
        val w = wifi ?: return true
        if (w.isWifiEnabled) return true
        if (!userAsked) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val on = try { w.setWifiEnabled(true) } catch (_: Exception) { false }
            if (on) {
                AppLog.i("WifiJoinWaiter: switched WiFi on")
                return true
            }
        } else {
            try {
                context.startActivity(Intent(SystemSettings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                AppLog.w("WifiJoinWaiter: no WiFi panel on this unit: ${e.message}")
            }
        }
        AppLog.i("WifiJoinWaiter: WiFi is off and the app cannot switch it on")
        return false
    }

    @SuppressLint("MissingPermission")
    private fun requestScan() {
        val w = wifi ?: return
        if (!w.isWifiEnabled) return
        try {
            @Suppress("DEPRECATION")
            val accepted = w.startScan()
            AppLog.d("WifiJoinWaiter: scan ${scanIndex + 1} ${if (accepted) "requested" else "refused (throttled)"}")
        } catch (e: Exception) {
            AppLog.d("WifiJoinWaiter: scan refused: ${e.message}")
        }
        // Below Android 10 the app may also ask the supplicant to retry its saved networks.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            try { w.reconnect() } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerListener() {
        val cm = cm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && cm != null) {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post { joined("network callback") }
                }
            }
            try {
                cm.registerNetworkCallback(
                    NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb
                )
                callback = cb
                return
            } catch (e: Exception) {
                AppLog.w("WifiJoinWaiter: network callback refused, using the broadcast: ${e.message}")
            }
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (isWifiConnected()) joined("broadcast")
            }
        }
        // EXPORTED, as for the service's other system WiFi broadcasts: NOT_EXPORTED would not
        // hear the platform.
        @Suppress("DEPRECATION")
        ContextCompat.registerReceiver(
            context, r, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), ContextCompat.RECEIVER_EXPORTED
        )
        receiver = r
    }

    companion object {
        /** True when any WiFi network is connected, the default network or not. */
        @SuppressLint("MissingPermission")
        fun isWifiConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                } else {
                    @Suppress("DEPRECATION")
                    cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected == true
                }
            } catch (e: Exception) {
                AppLog.w("WifiJoinWaiter: could not read the network state: ${e.message}")
                false
            }
        }
    }
}
