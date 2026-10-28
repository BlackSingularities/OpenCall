package com.blacksingularities.opencall

import com.blacksingularities.opencall.data.CallEntry
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.data.CallLogRepo
import com.blacksingularities.opencall.data.PhoneText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTextTest {
    @Test
    fun foldRemovesPolishDiacritics() {
        assertEquals("lukasz zolw", PhoneText.fold("Łukasz Żółw"))
        assertEquals("zazolc gesla jazn", PhoneText.fold("Zażółć gęślą jaźń"))
    }

    @Test
    fun t9OfName() {
        assertEquals("526 56925754", PhoneText.toT9("Jan Kowalski"))
        assertEquals("585279", PhoneText.toT9("Łukasz"))
        assertEquals("23 5", PhoneText.toT9("Ąę - Ł"))
    }

    @Test
    fun t9Scores() {
        val t9 = PhoneText.toT9("Jan Kowalski")
        assertEquals(3, PhoneText.t9Score(t9, "526"))
        assertEquals(2, PhoneText.t9Score(t9, "5692"))
        assertEquals(1, PhoneText.t9Score(t9, "55"))
        assertEquals(0, PhoneText.t9Score(t9, "999"))
    }

    @Test
    fun numberKeysMatchAcrossFormats() {
        assertTrue(PhoneText.sameNumber("+48 600 100 200", "600-100-200"))
        assertTrue(PhoneText.sameNumber("0048600100200", "600100200"))
        assertFalse(PhoneText.sameNumber("600100200", "600100201"))
        assertFalse(PhoneText.sameNumber("", ""))
    }

    @Test
    fun numberSubstringMatch() {
        assertTrue(PhoneText.numberMatches("+48 600 100 200", "0010"))
        assertFalse(PhoneText.numberMatches("+48 600 100 200", ""))
    }

    @Test
    fun prettyPolishNumbers() {
        assertEquals("600 100 200", PhoneText.pretty("600100200"))
        assertEquals("+48 600 100 200", PhoneText.pretty("+48600100200"))
        assertEquals("*100#", PhoneText.pretty("*100#"))
    }

    @Test
    fun blockRules() {
        assertTrue(PhoneText.matchesRule("0700123456", "0700"))
        assertTrue(PhoneText.matchesRule("+48700123456", "0700"))
        assertTrue(PhoneText.matchesRule("+48 22 300 11 22", "+48 22 30*"))
        assertTrue(PhoneText.matchesRule("223001122", "+48 22"))
        assertFalse(PhoneText.matchesRule("+48600100200", "0700"))
        assertFalse(PhoneText.matchesRule("600100200", ""))
    }

    @Test
    fun durations() {
        assertEquals("0:42", PhoneText.duration(42))
        assertEquals("12:05", PhoneText.duration(725))
        assertEquals("1:02:33", PhoneText.duration(3753))
        assertEquals("3 min", PhoneText.durationShort(200))
    }

    @Test
    fun initials() {
        assertEquals("JK", PhoneText.initials("Jan Kowalski"))
        assertEquals("A", PhoneText.initials("anna"))
        assertEquals("#", PhoneText.initials("+48 600"))
    }

    private fun entry(id: Long, number: String, kind: CallKind, date: Long, dur: Long = 0) =
        CallEntry(id, number, null, null, kind, date, dur, null, null, number.isBlank(), false, false)

    @Test
    fun groupingJoinsSameNumberSameDay() {
        val day = 1_700_000_000_000L
        val list = listOf(
            entry(1, "600100200", CallKind.OUTGOING, day),
            entry(2, "+48 600 100 200", CallKind.INCOMING, day - 60_000),
            entry(3, "600100200", CallKind.MISSED, day - 120_000),
            entry(4, "500", CallKind.OUTGOING, day - 180_000),
        )
        val g = PhoneText.group(list)
        assertEquals(3, g.size)
        assertEquals(2, g[0].count)
        assertTrue(g[1].missed)
        assertEquals(4, PhoneText.group(list, enabled = false).size)
    }

    @Test
    fun statsCountKinds() {
        val now = System.currentTimeMillis()
        val list = listOf(
            entry(1, "600100200", CallKind.OUTGOING, now, 60),
            entry(2, "600100200", CallKind.INCOMING, now, 120),
            entry(3, "500100200", CallKind.MISSED, now),
        )
        val s = CallLogRepo.stats(list, 0) { it.number }
        assertEquals(3, s.total)
        assertEquals(1, s.missed)
        assertEquals(180, s.talkSec)
        assertEquals("600100200" to 2, s.top.first())
        assertEquals(120L, s.longest?.durationSec)
    }
}
