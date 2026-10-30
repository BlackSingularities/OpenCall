package com.blacksingularities.opencall.wear

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Odbiera polecenia z zegarka i śledzi, czy zegarek z OpenDial jest w zasięgu. */
class WearListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearLink.PATH_ACTION -> WearLink.parseAction(event.data)?.let { WearBridge.handle(this, it) }
            WearLink.PATH_SYNC -> WearBridge.sync(this)
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == WearLink.CAPABILITY_WATCH) {
            WearBridge.presenceChanged(this, info.nodes.isNotEmpty())
        }
    }
}
