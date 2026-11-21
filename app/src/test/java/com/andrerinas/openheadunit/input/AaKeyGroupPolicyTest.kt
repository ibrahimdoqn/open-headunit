package com.andrerinas.openheadunit.input

import android.view.KeyEvent
import com.andrerinas.openheadunit.input.AaKeyGroupPolicy.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AaKeyGroupPolicyTest {

    @Test
    fun `nothing held back sends every key, as before the setting existed`() {
        for (code in KeyCode.supported + listOf(KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENDCALL)) {
            assertTrue("$code", AaKeyGroupPolicy.shouldForward(code, emptySet()))
        }
    }

    @Test
    fun `a steering wheel's OK is held back with the select group, in every spelling`() {
        val heldBack = setOf(Group.SELECT)
        for (code in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            assertEquals(Group.SELECT, AaKeyGroupPolicy.groupOf(code))
            assertFalse("$code", AaKeyGroupPolicy.shouldForward(code, heldBack))
        }
        // and nothing else goes with it
        assertTrue(AaKeyGroupPolicy.shouldForward(KeyEvent.KEYCODE_DPAD_UP, heldBack))
        assertTrue(AaKeyGroupPolicy.shouldForward(KeyEvent.KEYCODE_BACK, heldBack))
    }

    @Test
    fun `each key lands in its group`() {
        val expected = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to Group.DIRECTION, KeyEvent.KEYCODE_DPAD_DOWN to Group.DIRECTION,
            KeyEvent.KEYCODE_DPAD_LEFT to Group.DIRECTION, KeyEvent.KEYCODE_DPAD_RIGHT to Group.DIRECTION,
            // the rotary controller's turns
            KeyEvent.KEYCODE_SOFT_LEFT to Group.DIRECTION, KeyEvent.KEYCODE_SOFT_RIGHT to Group.DIRECTION,
            KeyEvent.KEYCODE_BACK to Group.BACK, KeyEvent.KEYCODE_ESCAPE to Group.BACK,
            KeyEvent.KEYCODE_CALL to Group.PHONE, KeyEvent.KEYCODE_ENDCALL to Group.PHONE,
            KeyEvent.KEYCODE_HEADSETHOOK to Group.PHONE,
            KeyEvent.KEYCODE_SEARCH to Group.ASSISTANT, KeyEvent.KEYCODE_VOICE_ASSIST to Group.ASSISTANT,
            KeyEvent.KEYCODE_ASSIST to Group.ASSISTANT,
            KeyEvent.KEYCODE_A to Group.OTHER, KeyEvent.KEYCODE_HOME to Group.OTHER,
            KeyEvent.KEYCODE_MUSIC to Group.OTHER, KeyEvent.KEYCODE_TAB to Group.OTHER,
        )
        for ((code, group) in expected) assertEquals("$code", group, AaKeyGroupPolicy.groupOf(code))
    }

    @Test
    fun `media and volume keys belong to their own settings and are never held back here`() {
        val owned = listOf(
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        )
        val everything = Group.values().toSet()
        for (code in owned) {
            assertNull("$code", AaKeyGroupPolicy.groupOf(code))
            assertTrue("$code", AaKeyGroupPolicy.shouldForward(code, everything))
        }
    }

    @Test
    fun `holding every group back still leaves media and volume alone, and stops the rest`() {
        val everything = Group.values().toSet()
        assertFalse(AaKeyGroupPolicy.shouldForward(KeyEvent.KEYCODE_DPAD_CENTER, everything))
        assertFalse(AaKeyGroupPolicy.shouldForward(KeyEvent.KEYCODE_Z, everything))
        assertTrue(AaKeyGroupPolicy.shouldForward(KeyEvent.KEYCODE_MEDIA_NEXT, everything))
    }

    @Test
    fun `stored ids never move, and unknown ones are dropped rather than guessed at`() {
        assertEquals(listOf("select", "direction", "back", "phone", "assistant", "other"), Group.values().map { it.id })
        assertEquals(emptySet<Group>(), AaKeyGroupPolicy.decode(null))
        assertEquals(setOf(Group.SELECT), AaKeyGroupPolicy.decode(setOf("select", "steering-wheel-v2")))
        for (g in Group.values()) {
            assertEquals(setOf(g), AaKeyGroupPolicy.decode(AaKeyGroupPolicy.encode(setOf(g))))
        }
    }
}
