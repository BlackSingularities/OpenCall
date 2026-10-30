package com.blacksingularities.opencall.wear

import android.content.Context
import android.telecom.CallAudioState
import com.blacksingularities.opencall.call.CallManager
import com.blacksingularities.opencall.call.CallView
import com.blacksingularities.opencall.call.Dialer
import com.blacksingularities.opencall.data.CallLogRepo
import com.blacksingularities.opencall.data.ContactsRepo
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.SettingsStore
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Strona telefonu: publikuje stan rozmowy i katalog numerów dla zegarka oraz wykonuje polecenia,
 * które z niego przychodzą.
 *
 * Publikacja jest leniwa – nic nie leci do warstwy danych, dopóki nie ma sparowanego zegarka
 * z OpenDial ani włączonego przełącznika w ustawieniach. Dzięki temu na telefonie bez zegarka
 * cała ta ścieżka jest martwa i nie kosztuje baterii.
 */
object WearBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Czy w pobliżu jest zegarek z zainstalowaną aplikacją OpenDial. */
    @Volatile
    var watchPresent: Boolean = false
        private set

    private var lastCallKey = ""
    private var checkedPresence = false

    private fun enabled(context: Context) = SettingsStore(context).load().wearControl

    /**
     * Czy powiadomienia o rozmowie mają zostać na telefonie. Gdy zegarek ma własny ekran rozmowy,
     * mostkowane powiadomienie systemowe tylko by się z nim dublowało.
     */
    fun handlesCallUi(context: Context): Boolean = watchPresent && enabled(context)

    /** Sprawdza obecność zegarka (raz na proces i po każdej zmianie zgłoszonej przez usługę). */
    fun refreshPresence(context: Context, force: Boolean = false) {
        if (checkedPresence && !force) return
        checkedPresence = true
        val app = context.applicationContext
        scope.launch {
            val nodes = runCatching {
                Wearable.getCapabilityClient(app)
                    .getCapability(WearLink.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                    .await().nodes
            }.getOrNull()
            watchPresent = !nodes.isNullOrEmpty()
            if (watchPresent) {
                publishCall(app, force = true)
                publishDirectory(app)
            }
        }
    }

    internal fun presenceChanged(context: Context, present: Boolean) {
        watchPresent = present
        checkedPresence = true
        if (present) {
            publishCall(context, force = true)
            publishDirectory(context)
        }
    }

    /** Wywoływane przy każdej zmianie stanu połączeń (z [CallManager]). */
    fun publishCall(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!watchPresent) {
            refreshPresence(app)
            return
        }
        if (!enabled(app)) return
        val calls = CallManager.snapshot()
        val audio = CallManager.audio.value
        val state = stateOf(app, calls, audio)
        val key = calls.joinToString("|") { "${it.state}/${it.number}/${it.connectTime}" } +
            "#${state.muted}${state.speaker}${state.canHold}${state.canSwap}${state.canMerge}"
        if (key == lastCallKey && !force) return
        val wasBusy = lastCallKey.isNotEmpty() && !lastCallKey.startsWith("#")
        lastCallKey = key
        put(app, WearLink.PATH_CALL, state.encode())
        // Po zakończonej rozmowie historia na zegarku jest już nieaktualna.
        if (calls.isEmpty() && wasBusy) publishDirectory(app)
    }

    private fun stateOf(context: Context, calls: List<CallView>, audio: CallAudioState?): WearCallState {
        val primary = CallManager.primary(calls)
        return WearCallState(
            calls = calls.map { c ->
                WearCall(
                    state = when {
                        c.ringing -> WearCall.RINGING
                        c.dialing -> WearCall.DIALING
                        c.held -> WearCall.HELD
                        else -> WearCall.ACTIVE
                    },
                    name = c.name ?: "",
                    number = if (c.hidden) "" else c.number,
                    label = c.label,
                    connectTime = c.connectTime,
                    conference = c.conference,
                    children = c.children,
                    account = c.accountLabel,
                    hidden = c.hidden,
                )
            },
            muted = audio?.isMuted == true,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            canMute = primary?.canMute == true || primary?.active == true,
            canHold = primary?.canHold == true,
            canSwap = calls.any { it.held } && calls.any { it.active } || primary?.canSwap == true,
            canMerge = primary?.canMerge == true,
            quickReplies = SettingsStore(context).load().quickReplies,
            stamp = System.currentTimeMillis(),
        )
    }

    /** Ulubione kontakty i ostatnie połączenia – lista, z której dzwoni się z zegarka. */
    fun publishDirectory(context: Context) {
        val app = context.applicationContext
        if (!watchPresent || !enabled(app)) return
        scope.launch {
            val settings = SettingsStore(app).load()
            val favorites = runCatching {
                ContactsRepo(app).loadAll(settings.nameOrder)
                    .filter { it.starred && it.primaryNumber != null }
                    .take(30)
                    .map { WearEntry(name = it.name, number = it.primaryNumber!!.number, favorite = true) }
            }.getOrDefault(emptyList())
            val recents = runCatching {
                val seen = HashSet<String>()
                CallLogRepo(app).load(limit = 200)
                    .filter { it.number.isNotBlank() && !it.hidden }
                    .filter { seen.add(PhoneText.digits(it.number)) }
                    .take(30)
                    .map {
                        WearEntry(
                            name = it.name ?: "",
                            number = it.number,
                            missed = it.kind == com.blacksingularities.opencall.data.CallKind.MISSED,
                            date = it.date,
                        )
                    }
            }.getOrDefault(emptyList())
            put(app, WearLink.PATH_DIRECTORY, WearDirectory(favorites, recents, System.currentTimeMillis()).encode())
        }
    }

    private fun put(context: Context, path: String, json: String) {
        scope.launch {
            runCatching {
                val req = PutDataRequest.create(path).apply {
                    data = json.toByteArray()
                    setUrgent()
                }
                Wearable.getDataClient(context).putDataItem(req).await()
            }
        }
    }

    /** Wykonuje polecenie z zegarka. Zwraca false, gdy nie ma czego zmieniać. */
    fun handle(context: Context, action: WearAction): Boolean {
        val app = context.applicationContext
        if (!enabled(app)) return false
        val calls = CallManager.snapshot()
        val primary = CallManager.primary(calls)
        val ringing = calls.firstOrNull { it.ringing }
        when (action.name) {
            WearLink.ANSWER -> {
                val call = ringing ?: return false
                // Drugie połączenie: zawieszamy bieżące, tak jak przyciski na ekranie telefonu.
                if (calls.any { it.active }) CallManager.holdAndAnswer(call.call) else CallManager.answer(call.call)
            }
            WearLink.REJECT -> CallManager.reject((ringing ?: return false).call)
            WearLink.REJECT_SMS -> {
                val call = ringing ?: return false
                val text = action.text ?: return false
                CallManager.reject(call.call, text)
            }
            WearLink.HANGUP -> CallManager.hangup((primary ?: return false).call)
            WearLink.SILENCE -> CallManager.silenceRinger()
            WearLink.MUTE -> CallManager.mute(action.on ?: !(CallManager.audio.value?.isMuted ?: false))
            WearLink.SPEAKER -> {
                val on = action.on
                if (on == null) {
                    CallManager.toggleSpeaker()
                } else {
                    @Suppress("DEPRECATION")
                    CallManager.route(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
                }
            }
            WearLink.HOLD -> CallManager.toggleHold((primary ?: return false).call)
            WearLink.SWAP -> CallManager.swap()
            WearLink.MERGE -> CallManager.merge()
            WearLink.DTMF -> {
                val digit = action.text?.firstOrNull() ?: return false
                CallManager.dtmf((primary ?: return false).call, digit)
            }
            WearLink.DIAL -> {
                val number = action.text?.takeIf { it.isNotBlank() } ?: return false
                Dialer.call(app, number, SettingsStore(app).load().defaultSim)
            }
            else -> return false
        }
        publishCall(app, force = true)
        return true
    }

    /** Zegarek poprosił o odświeżenie (po uruchomieniu aplikacji na nadgarstku). */
    fun sync(context: Context) {
        val app = context.applicationContext
        watchPresent = true
        checkedPresence = true
        publishCall(app, force = true)
        publishDirectory(app)
    }
}
