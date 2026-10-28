package com.blacksingularities.opencall.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.Contact
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Keypad
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive

private data class Suggestion(val contact: Contact?, val number: String, val score: Int)

@Composable
fun DialerTab(vm: AppViewModel) {
    val tones = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { tones?.release() } }
    val digits = vm.dialed

    // Podpowiedzi: dopasowanie T9 nazwy albo fragmentu numeru, potem numery z historii.
    val suggestions = remember(digits, vm.contacts, vm.calls, vm.settings.t9Search) {
        val q = digits.filter { it.isDigit() }
        if (q.isEmpty()) return@remember emptyList()
        val out = ArrayList<Suggestion>()
        vm.contacts.forEach { c ->
            val t9 = if (vm.settings.t9Search) PhoneText.t9Score(c.t9, q) else 0
            val byNumber = c.numbers.firstOrNull { PhoneText.numberMatches(it.number, q) }
            when {
                byNumber != null -> out.add(Suggestion(c, byNumber.number, 4))
                t9 > 0 && c.primaryNumber != null -> out.add(Suggestion(c, c.primaryNumber!!.number, t9))
            }
        }
        vm.calls.asSequence()
            .filter { !it.hidden && PhoneText.numberMatches(it.number, q) }
            .distinctBy { PhoneText.key(it.number) }
            .filter { e -> out.none { PhoneText.sameNumber(it.number, e.number) } }
            .take(5)
            .forEach { out.add(Suggestion(null, it.number, 1)) }
        out.sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.contact?.name ?: "" }).take(30)
    }

    fun press(k: Char) {
        if (vm.settings.dtmfTones) {
            val tone = when (k) {
                in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (k - '0')
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> null
            }
            tone?.let { tones?.startTone(it, 120) }
        }
        vm.dialed = digits + k
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Tytułu tu nie ma; zostaje tylko informacja o dodawaniu drugiego połączenia.
            if (vm.addingCall) {
                Text(tr("Dodaj połączenie"), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            RoundIcon(Icons.Rounded.ContentPaste, tr("Wklej numer")) { vm.pasteNumber()?.let { vm.dialed = it } }
            Spacer(Modifier.width(6.dp))
            RoundIcon(Icons.Rounded.Settings, tr("Ustawienia")) { vm.push(Route.Settings) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(suggestions, key = { (it.contact?.id?.toString() ?: "n") + it.number }) { s ->
                Row(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .reactive(null, pressScale = 0.98f) { vm.call(s.number, s.contact?.name) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(s.contact?.name, s.contact?.photoUri, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.contact?.name ?: PhoneText.pretty(s.number), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val type = s.contact?.numbers?.firstOrNull { it.number == s.number }?.let { Format.phoneType(it.type, it.label) }
                        Text(listOfNotNull(type, if (s.contact != null) PhoneText.pretty(s.number) else tr("Z historii")).joinToString(" · "), fontSize = 12.sp, color = Palette.TextDim, maxLines = 1)
                    }
                    Icon(Icons.Rounded.Call, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
                }
            }
        }
        // Wpisany numer, a pod nim akcje dla numeru spoza kontaktów.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(44.dp))
                Text(
                    if (digits.isEmpty()) "" else PhoneText.pretty(digits).takeIf { digits.length in 9..12 } ?: digits,
                    fontSize = when {
                        digits.length > 16 -> 24.sp
                        digits.length > 12 -> 30.sp
                        else -> 36.sp
                    },
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.weight(1f).height(48.dp),
                )
                if (digits.isNotEmpty()) {
                    RoundIcon(
                        Icons.AutoMirrored.Rounded.Backspace,
                        tr("Usuń cyfrę · przytrzymaj: wyczyść"),
                        tint = Palette.TextDim,
                        onLongPress = { vm.dialed = "" },
                    ) { vm.dialed = digits.dropLast(1) }
                } else {
                    Spacer(Modifier.size(44.dp))
                }
            }
            val known = digits.length >= 3 && vm.contactFor(digits) != null
            if (digits.length >= 3 && !known) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    SmallPill(Icons.Rounded.PersonAdd, tr("Dodaj do kontaktów")) { vm.newContact(digits) }
                    SmallPill(Icons.AutoMirrored.Rounded.Message, tr("SMS")) { vm.sms(digits) }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val key = (maxWidth / 4.4f).coerceAtMost(76.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Keypad(
                    haptics = vm.settings.keypadVibrate,
                    keySize = key,
                    speedDial = vm.settings.speedDial.keys,
                    onKey = { press(it) },
                    onLongKey = { k ->
                        when (k) {
                            '0' -> {
                                vm.dialed = digits + "+"
                                true
                            }
                            '1' -> if (digits.isEmpty()) {
                                vm.voicemail()
                                true
                            } else {
                                false
                            }
                            in '2'..'9' -> digits.isEmpty() && vm.speedDial(k - '0')
                            else -> false
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .reactive(tr("Zadzwoń"), enabled = true) {
                            if (digits.isEmpty()) {
                                // Puste pole: ostatni wybierany numer, jak w klasycznych telefonach.
                                vm.calls.firstOrNull { it.kind == com.blacksingularities.opencall.data.CallKind.OUTGOING }?.let { vm.dialed = PhoneText.digits(it.number) }
                            } else {
                                val n = digits
                                vm.call(n, vm.contactFor(n)?.name)
                                vm.dialed = ""
                            }
                        }
                        .size(width = 120.dp, height = 64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Answer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .reactive(label, pressScale = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
