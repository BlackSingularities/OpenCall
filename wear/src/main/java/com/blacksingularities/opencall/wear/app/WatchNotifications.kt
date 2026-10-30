package com.blacksingularities.opencall.wear.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.blacksingularities.opencall.wear.WearCallState
import com.blacksingularities.opencall.wear.WearLink

/**
 * Powiadomienia na zegarku. Telefon oznacza swoje powiadomienie o rozmowie jako lokalne, więc to
 * tutaj budzi ekran na nadgarstku – także wtedy, gdy aplikacja zegarka nie jest otwarta.
 */
object WatchNotifications {
    private const val CH_INCOMING = "watch_incoming"
    private const val CH_ONGOING = "watch_ongoing"
    private const val ID_CALL = 200

    private var lastKey = ""

    private fun channels(nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, tr("Połączenie przychodzące"), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 600, 400, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, tr("Trwa rozmowa"), NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun flags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun action(context: Context, name: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            req,
            Intent(context, WatchActionReceiver::class.java).setAction(name),
            flags(),
        )

    fun update(context: Context, state: WearCallState) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val c = state.primary
        if (c == null) {
            cancel(context)
            return
        }
        val key = "${c.state}|${c.name}|${c.number}|${c.connectTime}"
        if (key == lastKey) return
        lastKey = key
        channels(nm)

        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags(),
        )
        val title = c.name.ifBlank { c.number.ifBlank { tr("Numer prywatny") } }
        val b = if (c.ringing) {
            Notification.Builder(context, CH_INCOMING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(title)
                .setContentText(tr("Połączenie przychodzące"))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(Notification.Action.Builder(null, tr("Odrzuć"), action(context, WearLink.REJECT, 2)).build())
                .addAction(Notification.Action.Builder(null, tr("Odbierz"), action(context, WearLink.ANSWER, 3)).build())
        } else {
            Notification.Builder(context, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(title)
                .setContentText(if (c.dialing) tr("Łączenie…") else if (c.held) tr("Zawieszone") else tr("Trwa rozmowa"))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(Notification.Action.Builder(null, tr("Rozłącz"), action(context, WearLink.HANGUP, 4)).build())
                .apply {
                    if (c.active && c.connectTime > 0) {
                        // Licznik liczony zegarem zegarka – po korekcie różnicy zegarów z telefonem.
                        setUsesChronometer(true)
                        setWhen(System.currentTimeMillis() - Phone.durationSec(c.connectTime) * 1000)
                        setShowWhen(true)
                    }
                }
        }
        runCatching { nm.notify(ID_CALL, b.build()) }
    }

    fun cancel(context: Context) {
        lastKey = ""
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_CALL)
    }
}
