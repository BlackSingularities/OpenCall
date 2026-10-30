package com.blacksingularities.opencall.wear.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.blacksingularities.opencall.wear.WearCall
import com.blacksingularities.opencall.wear.WearCallState
import com.blacksingularities.opencall.wear.WearDirectory
import com.blacksingularities.opencall.wear.WearEntry
import com.blacksingularities.opencall.wear.WearLink
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

private val Green = Color(0xFF1B8E4A)
private val Red = Color(0xFFB3261E)

/** Połączenie przychodzące: odrzuć / odbierz, pod nimi wyciszenie dzwonka i szybka odpowiedź. */
@Composable
internal fun IncomingScreen(state: WearCallState, overlay: (Overlay) -> Unit) {
    val context = LocalContext.current
    val call = state.primary ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Caller(call, tr("Połączenie przychodzące"))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RoundButton(Icons.Rounded.CallEnd, tr("Odrzuć"), Red) { Phone.action(context, WearLink.REJECT) }
            RoundButton(Icons.Rounded.Call, tr("Odbierz"), Green) { Phone.action(context, WearLink.ANSWER) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactChip(
                onClick = { Phone.action(context, WearLink.SILENCE) },
                label = { Text(tr("Wycisz dzwonek"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = ChipDefaults.secondaryChipColors(),
            )
            if (state.quickReplies.isNotEmpty()) {
                CompactChip(
                    onClick = { overlay(Overlay.REPLIES) },
                    label = { Icon(Icons.Rounded.Sms, tr("Odpowiedz SMS-em"), Modifier.size(16.dp)) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

/** Rozmowa w toku: licznik, mikrofon, głośnik, zawieszenie, klawiatura i rozłączenie. */
@Composable
internal fun ActiveScreen(state: WearCallState, overlay: (Overlay) -> Unit) {
    val context = LocalContext.current
    val call = state.primary ?: return
    val status = when {
        call.dialing -> tr("Łączenie…")
        call.held -> tr("Zawieszone")
        call.conference -> tr("Uczestnicy: {0}", call.children)
        call.active -> Duration(call.connectTime)
        else -> tr("Trwa rozmowa")
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Caller(call, status)
        state.secondary?.let {
            Text(
                tr("Oczekuje: {0}", it.name.ifBlank { it.number }),
                style = MaterialTheme.typography.caption3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(
                if (state.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                tr("Mikrofon"),
                state.muted,
            ) { Phone.action(context, WearLink.MUTE, on = !state.muted) }
            SmallButton(Icons.Rounded.VolumeUp, tr("Głośnik"), state.speaker) {
                Phone.action(context, WearLink.SPEAKER, on = !state.speaker)
            }
            if (state.canHold) {
                SmallButton(
                    if (call.held) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    if (call.held) tr("Wznów") else tr("Zawieś"),
                    call.held,
                ) { Phone.action(context, WearLink.HOLD) }
            }
            SmallButton(Icons.Rounded.Dialpad, tr("Klawiatura"), false) { overlay(Overlay.KEYPAD) }
        }
        if (state.canSwap || state.canMerge) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canSwap) {
                    CompactChip(
                        onClick = { Phone.action(context, WearLink.SWAP) },
                        label = { Icon(Icons.Rounded.SwapCalls, tr("Zamień"), Modifier.size(16.dp)) },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
                if (state.canMerge) {
                    CompactChip(
                        onClick = { Phone.action(context, WearLink.MERGE) },
                        label = { Icon(Icons.Rounded.Merge, tr("Scal"), Modifier.size(16.dp)) },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RoundButton(Icons.Rounded.CallEnd, tr("Rozłącz"), Red) { Phone.action(context, WearLink.HANGUP) }
    }
}

/** Szybkie odpowiedzi z ustawień telefonu – odrzucenie rozmowy SMS-em. */
@Composable
internal fun RepliesScreen(replies: List<String>, done: () -> Unit) {
    val context = LocalContext.current
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
    ) {
        item { ListHeader { Text(tr("Odpowiedz SMS-em")) } }
        items(replies) { text ->
            Chip(
                onClick = {
                    Phone.action(context, WearLink.REJECT_SMS, text = text)
                    done()
                },
                label = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Klawiatura DTMF – cyfry lecą do telefonu, który wysyła tony w rozmowie. */
@Composable
internal fun KeypadScreen(done: () -> Unit) {
    val context = LocalContext.current
    val rows = listOf("123", "456", "789", "*0#")
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { digit ->
                    Button(
                        onClick = { Phone.action(context, WearLink.DTMF, text = digit.toString()) },
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.size(36.dp),
                    ) { Text(digit.toString(), style = MaterialTheme.typography.button) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        CompactChip(
            onClick = done,
            label = { Text(tr("Wróć"), maxLines = 1) },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

/** Poza rozmową: ulubione i ostatnie numery – dotknięcie dzwoni z telefonu. */
@Composable
internal fun DirectoryScreen(directory: WearDirectory) {
    val context = LocalContext.current
    if (directory.empty) {
        Message(tr("Brak numerów do pokazania"), tr("Otwórz OpenDial na telefonie, aby zobaczyć ulubione i historię.")) {
            Phone.refresh(context)
        }
        return
    }
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = rememberScalingLazyListState()) {
        if (directory.favorites.isNotEmpty()) {
            item { ListHeader { Text(tr("Ulubione")) } }
            items(directory.favorites) { EntryChip(it) }
        }
        if (directory.recents.isNotEmpty()) {
            item { ListHeader { Text(tr("Ostatnie")) } }
            items(directory.recents) { EntryChip(it) }
        }
        item {
            CompactChip(
                onClick = { Phone.refresh(context) },
                label = { Text(tr("Odśwież"), maxLines = 1) },
                icon = { Icon(Icons.Rounded.Refresh, tr("Odśwież"), Modifier.size(16.dp)) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

@Composable
private fun EntryChip(entry: WearEntry) {
    val context = LocalContext.current
    val title = entry.name.ifBlank { entry.number.ifBlank { tr("Numer prywatny") } }
    Chip(
        onClick = { Phone.action(context, WearLink.DIAL, text = entry.number) },
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            val sub = when {
                entry.missed -> tr("Nieodebrane")
                entry.name.isNotBlank() -> entry.number
                else -> tr("Zadzwoń")
            }
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        icon = {
            if (entry.favorite) {
                Icon(Icons.Rounded.Star, tr("Ulubione"), Modifier.size(20.dp))
            } else {
                Icon(Icons.Rounded.Call, tr("Zadzwoń"), Modifier.size(20.dp))
            }
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun NoPhoneScreen() {
    val context = LocalContext.current
    Message(
        tr("Brak połączenia z telefonem"),
        tr("Sprawdź, czy zegarek jest sparowany i czy OpenDial działa na telefonie."),
    ) { Phone.refresh(context) }
}

@Composable
private fun Message(title: String, text: String, retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        CompactChip(
            onClick = retry,
            label = { Text(tr("Odśwież"), maxLines = 1) },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

@Composable
private fun Caller(call: WearCall, status: String) {
    val title = when {
        call.name.isNotBlank() -> call.name
        call.number.isNotBlank() -> call.number
        else -> tr("Numer prywatny")
    }
    Text(
        title,
        style = MaterialTheme.typography.title2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Text(
        if (call.conference) tr("Konferencja") else status,
        style = MaterialTheme.typography.caption2,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.primaryButtonColors(backgroundColor = color, contentColor = Color.White),
        modifier = Modifier.size(52.dp),
    ) { Icon(icon, label, Modifier.size(24.dp)) }
}

@Composable
private fun SmallButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (on) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(36.dp),
    ) { Icon(icon, label, Modifier.size(18.dp)) }
}

/** Licznik rozmowy odświeżany co pół sekundy – bez tego zegarek pokazywałby zastygły czas. */
@Composable
private fun Duration(connectTime: Long): String {
    var seconds by remember { mutableLongStateOf(Phone.durationSec(connectTime)) }
    LaunchedEffect(connectTime) {
        while (true) {
            seconds = Phone.durationSec(connectTime)
            delay(500)
        }
    }
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
