package com.blacksingularities.opencall.wear.app

import java.util.Locale

/**
 * Teksty zegarka. Tak jak w aplikacji telefonu kluczem jest polski napis, a [En] trzyma angielskie
 * odpowiedniki – zegarek wybiera język po ustawieniach systemu na nadgarstku.
 */
private val En = mapOf(
    "Odbierz" to "Answer",
    "Odrzuć" to "Decline",
    "Rozłącz" to "Hang up",
    "Wycisz dzwonek" to "Silence ringer",
    "Wyciszono" to "Muted",
    "Mikrofon" to "Microphone",
    "Głośnik" to "Speaker",
    "Zawieś" to "Hold",
    "Wznów" to "Resume",
    "Zamień" to "Swap",
    "Scal" to "Merge",
    "Odpowiedz SMS-em" to "Reply by text",
    "Połączenie przychodzące" to "Incoming call",
    "Łączenie…" to "Calling…",
    "Zawieszone" to "On hold",
    "Trwa rozmowa" to "In call",
    "Konferencja" to "Conference",
    "Uczestnicy: {0}" to "{0} participants",
    "Numer prywatny" to "Private number",
    "Oczekuje: {0}" to "Waiting: {0}",
    "Ulubione" to "Favorites",
    "Ostatnie" to "Recent",
    "Brak numerów do pokazania" to "Nothing to show yet",
    "Otwórz OpenDial na telefonie, aby zobaczyć ulubione i historię." to
        "Open OpenDial on your phone to see favorites and history.",
    "Brak połączenia z telefonem" to "Phone not connected",
    "Sprawdź, czy zegarek jest sparowany i czy OpenDial działa na telefonie." to
        "Check that the watch is paired and OpenDial is running on the phone.",
    "Odśwież" to "Refresh",
    "Zadzwoń" to "Call",
    "Nieodebrane" to "Missed",
    "Sterowanie rozmową jest wyłączone na telefonie" to "Call control is turned off on the phone",
    "Klawiatura" to "Keypad",
    "Wróć" to "Back",
)

private val english: Boolean get() = Locale.getDefault().language != "pl"

fun tr(pl: String, vararg args: Any?): String {
    val text = if (english) En[pl] ?: pl else pl
    if (args.isEmpty()) return text
    var out = text
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
    return out
}
