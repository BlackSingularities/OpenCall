package com.blacksingularities.opencall.wear.app

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Stan z telefonu dociera też wtedy, gdy aplikacja zegarka nie jest otwarta – stąd powiadomienie. */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) Phone.consume(event.dataItem)
        }
        events.release()
        WatchNotifications.update(this, Phone.call.value)
    }
}
