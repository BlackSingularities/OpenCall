package com.blacksingularities.opencall

import com.blacksingularities.opencall.wear.WearCall
import com.blacksingularities.opencall.wear.WearCallState
import com.blacksingularities.opencall.wear.WearDirectory
import com.blacksingularities.opencall.wear.WearEntry
import com.blacksingularities.opencall.wear.WearLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telefon i zegarek rozmawiają JSON-em – ten test pilnuje, żeby obie strony mówiły to samo. */
class WearProtocolTest {
    @Test
    fun callStateSurvivesRoundTrip() {
        val state = WearCallState(
            calls = listOf(
                WearCall(state = WearCall.RINGING, name = "Jan Łukasz", number = "+48123456789", label = "Komórka"),
                WearCall(state = WearCall.HELD, name = "", number = "501502503", connectTime = 1_700_000_000_000),
            ),
            muted = true,
            speaker = false,
            canMute = true,
            canHold = true,
            canSwap = true,
            canMerge = false,
            quickReplies = listOf("Oddzwonię", "Jestem w drodze"),
            stamp = 1_700_000_001_000,
        )
        val back = WearCallState.decode(state.encode())
        assertEquals(state, back)
        assertEquals("Jan Łukasz", back.primary?.name)
        assertEquals("501502503", back.secondary?.number)
        assertTrue(back.primary?.ringing == true)
        assertFalse(back.idle)
    }

    @Test
    fun hiddenNumberKeepsNoLabel() {
        val state = WearCallState(calls = listOf(WearCall(state = WearCall.ACTIVE, name = "", number = "", hidden = true)))
        val back = WearCallState.decode(state.encode())
        assertNull(back.primary?.label)
        assertTrue(back.primary?.hidden == true)
    }

    @Test
    fun brokenPayloadGivesEmptyState() {
        assertTrue(WearCallState.decode("to nie jest JSON").idle)
        assertTrue(WearDirectory.decode("{").empty)
    }

    @Test
    fun directorySurvivesRoundTrip() {
        val dir = WearDirectory(
            favorites = listOf(WearEntry(name = "Mama", number = "600700800", favorite = true)),
            recents = listOf(WearEntry(name = "", number = "+48999", missed = true, date = 1_700_000_000_000)),
            stamp = 5,
        )
        assertEquals(dir, WearDirectory.decode(dir.encode()))
    }

    @Test
    fun actionsCarryArguments() {
        val sms = WearLink.parseAction(WearLink.action(WearLink.REJECT_SMS, text = "Oddzwonię"))
        assertEquals(WearLink.REJECT_SMS, sms?.name)
        assertEquals("Oddzwonię", sms?.text)
        assertNull(sms?.on)

        val mute = WearLink.parseAction(WearLink.action(WearLink.MUTE, on = true))
        assertEquals(true, mute?.on)

        assertNull(WearLink.parseAction("śmieci".toByteArray()))
        assertNull(WearLink.parseAction("{}".toByteArray()))
    }
}
