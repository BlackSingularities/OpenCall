package com.blacksingularities.opencall.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import com.blacksingularities.opencall.data.CallLogRepo
import com.blacksingularities.opencall.data.ContactsRepo
import com.blacksingularities.opencall.data.SettingsStore

/** Akcje z powiadomień: odrzuć, rozłącz, oddzwoń, nieodebrane obejrzane. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> CallManager.snapshot().firstOrNull { it.ringing }?.let { CallManager.reject(it.call) }
            ACTION_HANGUP -> CallManager.primary()?.let { CallManager.hangup(it.call) }
            ACTION_CALL_BACK -> {
                CallNotifications.cancelMissed(context)
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                Dialer.call(context, number, SettingsStore(context).load().defaultSim)
            }
            ACTION_MISSED_SEEN -> {
                val pending = goAsync()
                Thread {
                    runCatching { CallLogRepo(context).markMissedSeen() }
                    pending.finish()
                }.start()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.blacksingularities.opencall.DECLINE"
        const val ACTION_HANGUP = "com.blacksingularities.opencall.HANGUP"
        const val ACTION_CALL_BACK = "com.blacksingularities.opencall.CALL_BACK"
        const val ACTION_MISSED_SEEN = "com.blacksingularities.opencall.MISSED_SEEN"
        const val EXTRA_NUMBER = "number"
    }
}

/**
 * Telecom prosi domyślną aplikację telefonu o pokazanie powiadomienia o nieodebranych
 * (systemowe wtedy się nie pojawia). Liczba 0 = użytkownik je obejrzał – chowamy.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        if (count <= 0) {
            CallNotifications.cancelMissed(context)
            return
        }
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        val pending = goAsync()
        Thread {
            val name = runCatching { ContactsRepo(context).lookup(number)?.name }.getOrNull()
            CallNotifications.missed(context, count, number, name)
            pending.finish()
        }.start()
    }
}
