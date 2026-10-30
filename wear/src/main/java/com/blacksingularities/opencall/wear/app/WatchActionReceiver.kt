package com.blacksingularities.opencall.wear.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blacksingularities.opencall.wear.WearLink

/** Przyciski z powiadomienia na zegarku: odbierz, odrzuć, rozłącz. */
class WatchActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            WearLink.ANSWER, WearLink.REJECT, WearLink.HANGUP -> Phone.action(context, action)
            else -> return
        }
    }
}
