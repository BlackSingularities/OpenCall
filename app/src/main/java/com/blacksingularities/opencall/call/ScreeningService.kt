package com.blacksingularities.opencall.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.blacksingularities.opencall.data.ContactsRepo
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.i18n.tr

/**
 * Filtr połączeń przychodzących (domyślny dialer dostaje go dla wszystkich połączeń): numery
 * ukryte, nieznane, reguły prefiksów („0700”, „+48 800*”). Systemowa lista blokad działa
 * niezależnie – Telecom odrzuca te numery, zanim w ogóle trafią tutaj.
 */
class ScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val s = SettingsStore(this).load()
        val number = details.handle?.schemeSpecificPart ?: ""
        val hidden = number.isBlank() ||
            (Build.VERSION.SDK_INT >= 30 && details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED)

        val reason: String? = when {
            hidden && s.blockHidden -> tr("numer ukryty")
            !hidden && s.blockRules.any { PhoneText.matchesRule(number, it) } -> tr("reguła blokowania")
            else -> null
        }
        val unknown = !hidden && (s.blockUnknown || s.silenceUnknown) &&
            runCatching { ContactsRepo(this).lookup(number) == null }.getOrDefault(false)

        val response = CallResponse.Builder()
        when {
            reason != null || (unknown && s.blockUnknown) -> {
                response.setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(!s.logBlocked)
                if (s.notifyBlocked) CallNotifications.blocked(this, number, reason ?: tr("spoza kontaktów"))
            }
            unknown && s.silenceUnknown -> response.setSilenceCall(true)
        }
        respondToCall(details, response.build())
    }
}
