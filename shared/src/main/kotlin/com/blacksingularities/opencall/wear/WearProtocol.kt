package com.blacksingularities.opencall.wear

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wspólny język telefonu i zegarka (katalog `shared/` jest w źródłach obu modułów).
 *
 * Telefon publikuje stan rozmowy i katalog numerów przez DataClient (ostatnia wartość zostaje
 * w warstwie danych, więc zegarek po przebudzeniu od razu wie, co się dzieje), a zegarek odsyła
 * polecenia przez MessageClient. Wszystko jedzie jako JSON – nowe pole nie psuje starszej strony,
 * bo każda czyta tylko to, co zna.
 */
object WearLink {
    /** Ścieżki DataClient (telefon → zegarek). */
    const val PATH_CALL = "/opendial/call"
    const val PATH_DIRECTORY = "/opendial/directory"

    /** Ścieżki MessageClient (zegarek → telefon). */
    const val PATH_ACTION = "/opendial/action"
    const val PATH_SYNC = "/opendial/sync"

    /** Deklarowane w `res/values/wear.xml` obu aplikacji – służą do rozpoznania drugiej strony. */
    const val CAPABILITY_PHONE = "opendial_phone"
    const val CAPABILITY_WATCH = "opendial_watch"

    const val ANSWER = "answer"
    const val REJECT = "reject"
    const val REJECT_SMS = "reject_sms"
    const val HANGUP = "hangup"
    const val MUTE = "mute"
    const val SPEAKER = "speaker"
    const val HOLD = "hold"
    const val SWAP = "swap"
    const val MERGE = "merge"
    const val SILENCE = "silence"
    const val DIAL = "dial"
    const val DTMF = "dtmf"

    /** Polecenie z zegarka: nazwa akcji + opcjonalny argument (numer, treść SMS, cyfra, przełącznik). */
    fun action(name: String, text: String? = null, on: Boolean? = null): ByteArray =
        JSONObject().apply {
            put("a", name)
            if (text != null) put("t", text)
            if (on != null) put("on", on)
        }.toString().toByteArray()

    fun parseAction(bytes: ByteArray): WearAction? = runCatching {
        val o = JSONObject(String(bytes))
        val name = o.optString("a").takeIf { it.isNotBlank() } ?: return null
        WearAction(
            name = name,
            text = o.optString("t").takeIf { it.isNotBlank() },
            on = if (o.has("on")) o.optBoolean("on") else null,
        )
    }.getOrNull()
}

data class WearAction(val name: String, val text: String? = null, val on: Boolean? = null)

/** Stan jednego połączenia widziany przez zegarek. */
data class WearCall(
    val state: String,
    val name: String,
    val number: String,
    val label: String? = null,
    val connectTime: Long = 0,
    val conference: Boolean = false,
    val children: Int = 0,
    val account: String? = null,
    val hidden: Boolean = false,
) {
    val ringing: Boolean get() = state == RINGING
    val dialing: Boolean get() = state == DIALING
    val active: Boolean get() = state == ACTIVE
    val held: Boolean get() = state == HELD

    companion object {
        const val RINGING = "ringing"
        const val DIALING = "dialing"
        const val ACTIVE = "active"
        const val HELD = "held"
    }
}

/**
 * Migawka rozmów z telefonu. [stamp] to czas telefonu w chwili publikacji – zegarek liczy z niego
 * przesunięcie zegarów, żeby licznik rozmowy zgadzał się co do sekundy.
 */
data class WearCallState(
    val calls: List<WearCall> = emptyList(),
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val canMute: Boolean = false,
    val canHold: Boolean = false,
    val canSwap: Boolean = false,
    val canMerge: Boolean = false,
    val quickReplies: List<String> = emptyList(),
    val stamp: Long = 0,
) {
    val primary: WearCall?
        get() = calls.firstOrNull { it.ringing }
            ?: calls.firstOrNull { it.dialing }
            ?: calls.firstOrNull { it.active }
            ?: calls.firstOrNull { it.held }

    /** Drugie połączenie – zegarek pokazuje je jako podpis („Oczekuje: Jan”). */
    val secondary: WearCall? get() = primary?.let { p -> calls.firstOrNull { it !== p } }

    val idle: Boolean get() = calls.isEmpty()

    fun encode(): String = JSONObject().apply {
        put("calls", JSONArray().apply {
            calls.forEach { c ->
                put(JSONObject().apply {
                    put("state", c.state)
                    put("name", c.name)
                    put("number", c.number)
                    put("label", c.label ?: "")
                    put("connectTime", c.connectTime)
                    put("conference", c.conference)
                    put("children", c.children)
                    put("account", c.account ?: "")
                    put("hidden", c.hidden)
                })
            }
        })
        put("muted", muted)
        put("speaker", speaker)
        put("canMute", canMute)
        put("canHold", canHold)
        put("canSwap", canSwap)
        put("canMerge", canMerge)
        put("quickReplies", JSONArray(quickReplies))
        put("stamp", stamp)
    }.toString()

    companion object {
        fun decode(raw: String): WearCallState = runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("calls") ?: JSONArray()
            WearCallState(
                calls = (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    WearCall(
                        state = c.optString("state", WearCall.ACTIVE),
                        name = c.optString("name"),
                        number = c.optString("number"),
                        label = c.optString("label").takeIf { it.isNotBlank() },
                        connectTime = c.optLong("connectTime"),
                        conference = c.optBoolean("conference"),
                        children = c.optInt("children"),
                        account = c.optString("account").takeIf { it.isNotBlank() },
                        hidden = c.optBoolean("hidden"),
                    )
                },
                muted = o.optBoolean("muted"),
                speaker = o.optBoolean("speaker"),
                canMute = o.optBoolean("canMute"),
                canHold = o.optBoolean("canHold"),
                canSwap = o.optBoolean("canSwap"),
                canMerge = o.optBoolean("canMerge"),
                quickReplies = o.optJSONArray("quickReplies").let { a ->
                    if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                },
                stamp = o.optLong("stamp"),
            )
        }.getOrElse { WearCallState() }
    }
}

/** Pozycja listy na zegarku: ulubiony kontakt albo wpis z historii. */
data class WearEntry(
    val name: String,
    val number: String,
    val favorite: Boolean = false,
    val missed: Boolean = false,
    val date: Long = 0,
)

/** Ulubione i ostatnie połączenia wysyłane na zegarek, żeby dało się zadzwonić bez telefonu w ręce. */
data class WearDirectory(
    val favorites: List<WearEntry> = emptyList(),
    val recents: List<WearEntry> = emptyList(),
    val stamp: Long = 0,
) {
    val empty: Boolean get() = favorites.isEmpty() && recents.isEmpty()

    fun encode(): String = JSONObject().apply {
        put("favorites", entries(favorites))
        put("recents", entries(recents))
        put("stamp", stamp)
    }.toString()

    private fun entries(list: List<WearEntry>) = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().apply {
                put("name", e.name)
                put("number", e.number)
                put("favorite", e.favorite)
                put("missed", e.missed)
                put("date", e.date)
            })
        }
    }

    companion object {
        fun decode(raw: String): WearDirectory = runCatching {
            val o = JSONObject(raw)
            WearDirectory(
                favorites = list(o.optJSONArray("favorites")),
                recents = list(o.optJSONArray("recents")),
                stamp = o.optLong("stamp"),
            )
        }.getOrElse { WearDirectory() }

        private fun list(a: JSONArray?): List<WearEntry> {
            if (a == null) return emptyList()
            return (0 until a.length()).map { i ->
                val e = a.getJSONObject(i)
                WearEntry(
                    name = e.optString("name"),
                    number = e.optString("number"),
                    favorite = e.optBoolean("favorite"),
                    missed = e.optBoolean("missed"),
                    date = e.optLong("date"),
                )
            }
        }
    }
}
