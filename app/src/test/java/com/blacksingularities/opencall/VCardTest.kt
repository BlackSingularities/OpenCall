package com.blacksingularities.opencall

import com.blacksingularities.opencall.data.ContactDraft
import com.blacksingularities.opencall.data.DraftField
import com.blacksingularities.opencall.data.VCard
import com.blacksingularities.opencall.update.isNewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {
    @Test
    fun roundTrip() {
        val d = ContactDraft(
            given = "Łucja",
            family = "Wiśniewska; Nowak",
            organization = "Firma, sp. z o.o.",
            phones = listOf(DraftField("+48 600 100 200", 2), DraftField("22 123 45 67", 3)),
            emails = listOf(DraftField("lucja@example.com", 1)),
            birthday = "1990-05-12",
            note = "Pierwsza linia\nDruga linia",
        )
        val parsed = VCard.parse(VCard.write(d)).single()
        assertEquals("Łucja", parsed.given)
        assertEquals("Wiśniewska; Nowak", parsed.family)
        assertEquals("Firma, sp. z o.o.", parsed.organization)
        assertEquals(listOf("+48 600 100 200", "22 123 45 67"), parsed.phones.map { it.value })
        assertEquals(2, parsed.phones[0].type)
        assertEquals(3, parsed.phones[1].type)
        assertEquals("lucja@example.com", parsed.emails.single().value)
        assertEquals("1990-05-12", parsed.birthday)
        assertEquals("Pierwsza linia\nDruga linia", parsed.note)
    }

    @Test
    fun parsesVersion21QuotedPrintable() {
        val text = """
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Kowalski;=C5=81ukasz;;;
            TEL;CELL:600100200
            TEL;HOME;VOICE:221234567
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Tylko Nazwa
            TEL;TYPE=WORK:+48 500
            END:VCARD
        """.trimIndent()
        val list = VCard.parse(text)
        assertEquals(2, list.size)
        assertEquals("Łukasz", list[0].given)
        assertEquals("Kowalski", list[0].family)
        assertEquals(2, list[0].phones[0].type)
        assertEquals(1, list[0].phones[1].type)
        assertEquals("Tylko Nazwa", list[1].displayName)
    }

    @Test
    fun foldedLinesAreJoined() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jan\r\nNOTE:abc\r\n def\r\nEND:VCARD\r\n"
        assertEquals("abcdef", VCard.parse(text).single().note)
    }

    @Test
    fun skipsEmptyCards() {
        assertTrue(VCard.parse("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD").isEmpty())
    }

    @Test
    fun versionCompare() {
        assertTrue(isNewer("0.1.10", "0.1.9"))
        assertTrue(isNewer("0.2.0", "0.1.0-dev"))
        assertTrue(isNewer("0.1.0.57", "0.1.0"))
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.0.9", "0.1.0"))
    }
}
