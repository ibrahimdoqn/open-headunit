package com.andrerinas.openheadunit.input

import android.view.KeyEvent

/**
 * Which of this unit's keys go to Android Auto, by group, for the keys no binding speaks for.
 *
 * A head unit's key handler often delivers presses that were never meant for the projection. On
 * cars whose factory system stays in place, the steering wheel drives that system, and its OK or
 * Back still reaches this app: Android Auto then selects or leaves something the driver never
 * looked at. Bindings cannot help there, since an unbound key goes on as itself; this lets a group
 * be held back instead.
 *
 * Media buttons are not a group here: [MediaKeyRoutingPolicy] owns them, with an Automatic mode
 * this one has no use for. Volume is the volume setting's. Neither is affected by anything here.
 *
 * A binding always wins. A key bound in the keymap was chosen for Android Auto by name, so the
 * caller asks this only for unbound keys; gestures bound to an Android Auto key never come here
 * either.
 *
 * Pure and unit-tested; the send lives in `CommManager`.
 */
object AaKeyGroupPolicy {

    /**
     * A group of keys, by the id it is stored under. The ids are stored: never reuse or rename one.
     * What is stored is the set of groups held back, so a group added later starts out sent.
     */
    enum class Group(val id: String) {
        SELECT("select"),
        DIRECTION("direction"),
        BACK("back"),
        PHONE("phone"),
        ASSISTANT("assistant"),
        OTHER("other");

        companion object {
            fun fromId(id: String): Group? = values().firstOrNull { it.id == id }
        }
    }

    private val select = setOf(
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
    )

    /** The D-pad, and the rotary controller's turns, which reach Android Auto as SOFT_LEFT/RIGHT. */
    private val direction = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_SOFT_RIGHT,
    )

    private val back = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)

    private val phone = setOf(KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENDCALL, KeyEvent.KEYCODE_HEADSETHOOK)

    private val assistant = setOf(
        KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST,
    )

    /** The keys other settings own, so this one never touches them. */
    private val ownedElsewhere = setOf(
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    )

    /** The group [keyCode] belongs to, or null for a key another setting owns. */
    fun groupOf(keyCode: Int): Group? = when (keyCode) {
        in ownedElsewhere -> null
        in select -> Group.SELECT
        in direction -> Group.DIRECTION
        in back -> Group.BACK
        in phone -> Group.PHONE
        in assistant -> Group.ASSISTANT
        else -> Group.OTHER
    }

    /** Whether an unbound key goes to Android Auto, given the groups the user holds back. */
    fun shouldForward(keyCode: Int, heldBack: Set<Group>): Boolean {
        val group = groupOf(keyCode) ?: return true
        return group !in heldBack
    }

    /** The stored form: the ids of the groups held back. Unknown ids are dropped, never guessed at. */
    fun decode(stored: Set<String>?): Set<Group> = stored.orEmpty().mapNotNull { Group.fromId(it) }.toSet()

    fun encode(heldBack: Set<Group>): Set<String> = heldBack.map { it.id }.toSet()
}
