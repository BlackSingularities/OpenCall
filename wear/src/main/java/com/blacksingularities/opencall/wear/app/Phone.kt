package com.blacksingularities.opencall.wear.app

import android.content.Context
import com.blacksingularities.opencall.wear.WearCallState
import com.blacksingularities.opencall.wear.WearDirectory
import com.blacksingularities.opencall.wear.WearLink
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Strona zegarka: trzyma ostatni stan rozmowy i katalog numerów z telefonu oraz wysyła polecenia.
 *
 * Stan jest w obiekcie, bo tę samą pamięć dzielą ekran i [WatchListenerService] (ten sam proces) –
 * po wybudzeniu zegarek pokazuje rozmowę bez mrugnięcia pustym ekranem.
 */
object Phone {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _call = MutableStateFlow(WearCallState())
    val call: StateFlow<WearCallState> = _call

    private val _directory = MutableStateFlow(WearDirectory())
    val directory: StateFlow<WearDirectory> = _directory

    /** Czy telefon z OpenDial jest w zasięgu – inaczej zegarek pokazuje „Brak połączenia z telefonem”. */
    private val _reachable = MutableStateFlow(true)
    val reachable: StateFlow<Boolean> = _reachable

    /**
     * Różnica zegarów telefon–zegarek. Licznik rozmowy liczy się z czasu połączenia nadanego przez
     * telefon, więc bez tej korekty mógłby się rozjechać o kilka sekund.
     */
    @Volatile
    var clockOffset: Long = 0
        private set

    private val listener = DataClient.OnDataChangedListener { buffer: DataEventBuffer ->
        buffer.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) consume(event.dataItem) else cleared(event.dataItem)
        }
        buffer.release()
    }

    fun listen(context: Context) {
        Wearable.getDataClient(context.applicationContext).addListener(listener)
        refresh(context)
    }

    fun stopListening(context: Context) {
        Wearable.getDataClient(context.applicationContext).removeListener(listener)
    }

    /** Wczytuje bieżące wartości i prosi telefon o świeżą migawkę (po otwarciu aplikacji). */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val items = Wearable.getDataClient(app).dataItems.await()
                items.forEach { consume(it) }
                items.release()
            }
            val nodes = runCatching {
                Wearable.getCapabilityClient(app)
                    .getCapability(WearLink.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .await().nodes
            }.getOrNull()
            _reachable.value = !nodes.isNullOrEmpty()
            send(app, WearLink.PATH_SYNC)
        }
    }

    internal fun consume(item: DataItem) {
        val raw = item.data?.let { String(it) } ?: return
        when (item.uri.path) {
            WearLink.PATH_CALL -> {
                val state = WearCallState.decode(raw)
                clockOffset = if (state.stamp > 0) System.currentTimeMillis() - state.stamp else 0
                _call.value = state
                _reachable.value = true
            }
            WearLink.PATH_DIRECTORY -> _directory.value = WearDirectory.decode(raw)
        }
    }

    private fun cleared(item: DataItem) {
        if (item.uri.path == WearLink.PATH_CALL) _call.value = WearCallState()
    }

    /** Czas trwania rozmowy w sekundach, z korektą różnicy zegarów. */
    fun durationSec(connectTime: Long): Long {
        if (connectTime <= 0) return 0
        val phoneNow = System.currentTimeMillis() - clockOffset
        return ((phoneNow - connectTime) / 1000).coerceAtLeast(0)
    }

    fun action(context: Context, name: String, text: String? = null, on: Boolean? = null) {
        send(context.applicationContext, WearLink.PATH_ACTION, WearLink.action(name, text, on))
    }

    private fun send(context: Context, path: String, payload: ByteArray = ByteArray(0)) {
        scope.launch {
            val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }.getOrNull()
            if (nodes.isNullOrEmpty()) {
                _reachable.value = false
                return@launch
            }
            var delivered = false
            nodes.forEach { node ->
                val ok = runCatching {
                    Wearable.getMessageClient(context).sendMessage(node.id, path, payload).await()
                }.isSuccess
                delivered = delivered || ok
            }
            _reachable.value = delivered
        }
    }
}
