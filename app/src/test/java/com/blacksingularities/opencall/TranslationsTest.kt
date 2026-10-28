package com.blacksingularities.opencall

import com.blacksingularities.opencall.i18n.AppLanguage
import com.blacksingularities.opencall.i18n.En
import com.blacksingularities.opencall.i18n.I18n
import com.blacksingularities.opencall.i18n.plural
import com.blacksingularities.opencall.i18n.tr
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pilnuje, żeby każdy polski tekst z interfejsu miał angielski odpowiednik: skanuje źródła w
 * poszukiwaniu wywołań `tr("…")` i porównuje z mapą `En`. Dzięki temu nowy napis bez tłumaczenia
 * psuje build, a nie dopiero wygląd aplikacji.
 */
class TranslationsTest {
    private val sources = File("src/main/java/com/blacksingularities/opencall")

    /** Pierwszy argument `tr(…)`, jeśli jest literałem; wywołania z wyrażeniem są pomijane. */
    private val trCall = Regex("""\btr\s*\(\s*"((?:[^"\\]|\\.)*)"""")

    private fun keysInSources(): Set<String> {
        assertTrue("Nie znaleziono katalogu źródeł: ${sources.absolutePath}", sources.isDirectory)
        return sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "En.kt" }
            .flatMap { file -> trCall.findAll(file.readText()).map { it.groupValues[1] } }
            .map { it.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\") }
            .toSet()
    }

    @After
    fun resetLanguage() {
        I18n.apply(AppLanguage.PL)
    }

    @Test
    fun everyPolishStringHasEnglish() {
        val missing = keysInSources().filterNot { En.containsKey(it) }.sorted()
        assertTrue("Brak tłumaczeń EN (${missing.size}):\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun noStaleTranslations() {
        val used = keysInSources()
        val stale = En.keys.filterNot { it in used }.sorted()
        assertTrue("Nieużywane klucze w En.kt (${stale.size}):\n" + stale.joinToString("\n"), stale.isEmpty())
    }

    @Test
    fun placeholdersMatch() {
        val holder = Regex("""\{\d}""")
        val bad = En.entries.filter { (pl, en) ->
            holder.findAll(pl).map { it.value }.toSet() != holder.findAll(en).map { it.value }.toSet()
        }
        assertTrue(
            "Niezgodne znaczniki {0}, {1}…:\n" + bad.joinToString("\n") { "${it.key} → ${it.value}" },
            bad.isEmpty(),
        )
    }

    @Test
    fun noEmptyTranslations() {
        val empty = En.entries.filter { it.value.isBlank() }.map { it.key }
        assertTrue("Puste tłumaczenia: $empty", empty.isEmpty())
    }

    @Test
    fun trSwitchesLanguageAndFillsArguments() {
        I18n.apply(AppLanguage.PL)
        assertEquals("Ustawienia", tr("Ustawienia"))
        assertEquals("Zadzwoń do Jan", tr("Zadzwoń do {0}", "Jan"))

        I18n.apply(AppLanguage.EN)
        assertEquals("Settings", tr("Ustawienia"))
        assertEquals("Call Jan", tr("Zadzwoń do {0}", "Jan"))
        // Tekst bez tłumaczenia przechodzi bez zmian, a nie jako pusty napis.
        assertEquals("Napis bez tłumaczenia", tr("Napis bez tłumaczenia"))
    }

    @Test
    fun systemLanguageDecidesEnglish() {
        I18n.apply(AppLanguage.SYSTEM, systemLanguage = "pl")
        assertEquals("Ustawienia", tr("Ustawienia"))
        I18n.apply(AppLanguage.SYSTEM, systemLanguage = "de")
        assertEquals("Settings", tr("Ustawienia"))
    }

    @Test
    fun polishPluralForms() {
        I18n.apply(AppLanguage.PL)
        val f = { n: Int -> plural(n, "połączenie", "połączenia", "połączeń") }
        assertEquals("połączenie", f(1))
        assertEquals("połączenia", f(2))
        assertEquals("połączenia", f(23))
        assertEquals("połączeń", f(5))
        assertEquals("połączeń", f(12))
        assertEquals("połączeń", f(13))
        assertEquals("połączeń", f(0))
    }
}
