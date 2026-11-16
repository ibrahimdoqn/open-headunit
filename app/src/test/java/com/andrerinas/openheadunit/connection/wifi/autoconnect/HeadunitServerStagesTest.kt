package com.andrerinas.openheadunit.connection.wifi.autoconnect

import com.andrerinas.openheadunit.connection.ConnectionStage
import com.andrerinas.openheadunit.connection.ConnectionStagePolicy
import com.andrerinas.openheadunit.connection.ConnectionStageTracker
import com.andrerinas.openheadunit.main.HomePrimaryPolicy
import com.andrerinas.openheadunit.main.StatusPillPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The steps Headunit Server's automatic connection shows, and what may replace what. */
class HeadunitServerStagesTest {

    private val waits = listOf(
        ConnectionStage.WAITING_FOR_WIFI,
        ConnectionStage.PHONE_WIFI_NOT_VISIBLE,
        ConnectionStage.OTHER_WIFI,
        ConnectionStage.SERVER_CLOSED,
    )

    @After
    fun clear() = ConnectionStageTracker.clear()

    @Test
    fun `waits are listening steps on the pill`() {
        waits.forEach { assertTrue(it.name, StatusPillPolicy.isWaiting(it)) }
        assertFalse(StatusPillPolicy.isWaiting(ConnectionStage.WIFI_JOINED))
    }

    @Test
    fun `a wait shows its step on the card without becoming a progress screen`() {
        waits.forEach { stage ->
            val d = decide(stage)
            assertEquals(stage.name, HomePrimaryPolicy.Status.WAITING, d.status)
            assertEquals(stage, d.stage)
        }
    }

    @Test
    fun `the join is progress on the card`() {
        val d = decide(ConnectionStage.WIFI_JOINED)
        assertEquals(HomePrimaryPolicy.Status.CONNECTING, d.status)
        assertEquals(ConnectionStage.WIFI_JOINED, d.stage)
    }

    @Test
    fun `a diagnostic can go back to waiting for WiFi after the join`() {
        ConnectionStageTracker.showDiagnostic(ConnectionStage.WIFI_JOINED)
        ConnectionStageTracker.showDiagnostic(ConnectionStage.WAITING_FOR_WIFI)
        assertEquals(ConnectionStage.WAITING_FOR_WIFI, ConnectionStageTracker.stage.value)
    }

    @Test
    fun `a diagnostic never covers the phone answering or a USB step`() {
        for (later in listOf(ConnectionStage.PHONE_ANSWERED, ConnectionStage.CONNECTING, ConnectionStage.SECURING,
            ConnectionStage.STARTING_PROJECTION, ConnectionStage.USB_ATTACHED, ConnectionStage.USB_SWITCHING)) {
            ConnectionStageTracker.beginAttempt(later)
            ConnectionStageTracker.showDiagnostic(ConnectionStage.SERVER_CLOSED)
            assertEquals(later.name, later, ConnectionStageTracker.stage.value)
            assertFalse(ConnectionStagePolicy.mayShowDiagnostic(later))
        }
    }

    @Test
    fun `the phone answering replaces every Headunit Server step`() {
        (waits + ConnectionStage.WIFI_JOINED).forEach { stage ->
            assertTrue(stage.name, ConnectionStagePolicy.shouldApply(stage, ConnectionStage.PHONE_ANSWERED))
        }
    }

    @Test
    fun `spent quick retries drop the join back to searching, and nothing else`() {
        ConnectionStageTracker.showDiagnostic(ConnectionStage.WIFI_JOINED)
        ConnectionStageTracker.retreat(ConnectionStage.WIFI_JOINED, ConnectionStage.SEARCHING)
        assertEquals(ConnectionStage.SEARCHING, ConnectionStageTracker.stage.value)
        ConnectionStageTracker.beginAttempt(ConnectionStage.CONNECTING)
        ConnectionStageTracker.retreat(ConnectionStage.WIFI_JOINED, ConnectionStage.SEARCHING)
        assertEquals(ConnectionStage.CONNECTING, ConnectionStageTracker.stage.value)
    }

    private fun decide(stage: ConnectionStage) = HomePrimaryPolicy.decide(HomePrimaryPolicy.Input(
        chosen = setOf(HomePrimaryPolicy.Connection.WIFI),
        lastConnection = null, usbPhoneDetected = false, sessionActive = false,
        reconnectTarget = null, wifiScanning = false, stage = stage,
    ))
}
