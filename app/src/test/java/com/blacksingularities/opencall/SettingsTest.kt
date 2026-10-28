package com.blacksingularities.opencall

import com.blacksingularities.opencall.data.AppSettings
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.data.CallEntry
import com.blacksingularities.opencall.data.CallLogRepo
import com.blacksingularities.opencall.data.NameOrder
import com.blacksingularities.opencall.data.RecentTap
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.data.StartTab
import com.blacksingularities.opencall.i18n.AppLanguage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val custom = AppSettings(
        language = AppLanguage.EN,
        haptics = false,
        autoUpdate = false,
        startTab = StartTab.KEYPAD,
        lastTab = 3,
        dtmfTones = false,
        t9Search = false,
        confirmCall = true,
        groupCalls = false,
        recentTap = RecentTap.CALL,
        nameOrder = NameOrder.LAST_FIRST,
        onlyWithNumbers = true,
        blockHidden = true,
        blockRules = listOf("0700", "+48 22 30*"),
        notifyBlocked = true,
        speedDial = mapOf(2 to "+48600100200", 9 to "112"),
        quickReplies = listOf("Oddzwonię"),
        defaultSim = "sim-key-1",
        onboardingDone = true,
    )

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals(custom, SettingsStore.decode(SettingsStore.encode(custom)))
    }

    @Test
    fun defaultsRoundTrip() {
        val d = AppSettings()
        assertEquals(d, SettingsStore.decode(SettingsStore.encode(d)))
        assertEquals(4, d.quickReplies.size)
    }

    @Test
    fun emptyJsonGivesDefaults() {
        assertEquals(AppSettings(), SettingsStore.decode(JSONObject()))
    }

    @Test
    fun unknownEnumValueFallsBackToDefault() {
        val o = SettingsStore.encode(custom)
        o.put("startTab", "NIE_MA_TAKIEJ")
        o.put("recentTap", "")
        val s = SettingsStore.decode(o)
        assertEquals(AppSettings().startTab, s.startTab)
        assertEquals(AppSettings().recentTap, s.recentTap)
    }

    @Test
    fun missingFieldKeepsItsDefaultNotFalse() {
        // Plik ustawień ze starszej wersji nie ma nowych pól – nie mogą wtedy „wyzerować się”.
        val o = SettingsStore.encode(AppSettings())
        o.remove("haptics")
        o.remove("proximity")
        val s = SettingsStore.decode(o)
        assertTrue(s.haptics)
        assertTrue(s.proximity)
    }

    @Test
    fun emptySimMeansNoChoice() {
        val o = SettingsStore.encode(AppSettings(defaultSim = null))
        assertEquals(null, SettingsStore.decode(o).defaultSim)
    }

    @Test
    fun csvQuotesSeparatorsAndQuotes() {
        val entry = CallEntry(
            1, "600100200", "Nowak, Jan \"Janek\"", null, CallKind.OUTGOING,
            1_700_000_000_000L, 65, null, null, false, false, false,
        )
        val csv = CallLogRepo.toCsv(listOf(entry), { it.name }, { "2023-11-14 22:13" })
        val row = csv.lineSequence().drop(1).first()
        assertTrue(csv.lineSequence().first().endsWith("Data;Numer;Nazwa;Rodzaj;Czas (s);Lokalizacja"))
        assertTrue(row.contains("\"Nowak, Jan \"\"Janek\"\"\""))
        assertTrue(row.contains("\"OUTGOING\";65;"))
    }
}
