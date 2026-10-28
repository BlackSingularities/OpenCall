package com.blacksingularities.opencall.ui

import android.provider.ContactsContract
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.i18n.I18n
import com.blacksingularities.opencall.i18n.tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** Daty i etykiety w języku interfejsu. */
object Format {
    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun time(t: Long): String = SimpleDateFormat("HH:mm", I18n.locale).format(Date(t))

    /** „14:32”, „Wczoraj”, „poniedziałek”, „12 mar”, „12.03.2024”. */
    fun relative(t: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfDay(now)
        val day = 86_400_000L
        return when {
            t >= today -> time(t)
            t >= today - day -> tr("Wczoraj")
            t >= today - 6 * day -> SimpleDateFormat("EEEE", I18n.locale).format(Date(t))
            Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) ->
                SimpleDateFormat("d MMM", I18n.locale).format(Date(t))
            else -> SimpleDateFormat("dd.MM.yyyy", I18n.locale).format(Date(t))
        }
    }

    /** Nagłówek dnia na liście: „Dzisiaj”, „Wczoraj”, „Ten tydzień”, „Wcześniej”. */
    fun bucket(t: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfDay(now)
        val day = 86_400_000L
        return when {
            t >= today -> tr("Dzisiaj")
            t >= today - day -> tr("Wczoraj")
            t >= today - 6 * day -> tr("Ostatnie 7 dni")
            t >= today - 30 * day -> tr("Ostatnie 30 dni")
            else -> tr("Wcześniej")
        }
    }

    fun full(t: Long): String = SimpleDateFormat("d MMMM yyyy, HH:mm", I18n.locale).format(Date(t))

    fun csvDate(t: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", I18n.locale).format(Date(t))

    fun kind(k: CallKind): String = when (k) {
        CallKind.INCOMING -> tr("Przychodzące")
        CallKind.OUTGOING -> tr("Wychodzące")
        CallKind.MISSED -> tr("Nieodebrane")
        CallKind.REJECTED -> tr("Odrzucone")
        CallKind.BLOCKED -> tr("Zablokowane")
        CallKind.VOICEMAIL -> tr("Poczta głosowa")
    }

    /** Typy numerów w języku interfejsu (systemowe etykiety bywają tylko po angielsku). */
    fun phoneType(type: Int, label: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Inny")
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> tr("Komórka")
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> tr("Dom")
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> tr("Praca")
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> tr("Komórka służbowa")
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> tr("Główny")
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK, ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> tr("Faks")
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> tr("Pager")
        else -> tr("Inny")
    }

    fun emailType(type: Int, label: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Inny")
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> tr("Prywatny")
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> tr("Praca")
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> tr("Komórka")
        else -> tr("Inny")
    }

    val phoneTypes = listOf(
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE,
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN,
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER,
    )

    val emailTypes = listOf(
        ContactsContract.CommonDataKinds.Email.TYPE_HOME,
        ContactsContract.CommonDataKinds.Email.TYPE_WORK,
        ContactsContract.CommonDataKinds.Email.TYPE_OTHER,
    )

    /** Urodziny z zapisu „1990-05-12” lub „--05-12” (bez roku) – „12 maja 1990 (34 lata)”. */
    fun birthday(raw: String): String {
        val m = Regex("^(\\d{4}|-)-?-(\\d{2})-(\\d{2})").find(raw.trim()) ?: return raw
        val y = m.groupValues[1].toIntOrNull()
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        val cal = Calendar.getInstance().apply { set(y ?: 2000, month - 1, day) }
        val text = SimpleDateFormat(if (y != null) "d MMMM yyyy" else "d MMMM", I18n.locale).format(cal.time)
        if (y == null) return text
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - y
        if (now.get(Calendar.MONTH) + 1 < month || (now.get(Calendar.MONTH) + 1 == month && now.get(Calendar.DAY_OF_MONTH) < day)) age--
        return if (age in 0..130) "$text · ${tr("{0} lat", age)}" else text
    }
}
