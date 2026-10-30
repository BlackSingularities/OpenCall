package com.blacksingularities.opencall.wear.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText

/** Jedyny ekran zegarka: rozmowa, gdy trwa, a poza rozmową – ulubione i ostatnie numery. */
class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WatchRoot() } }
    }

    override fun onStart() {
        super.onStart()
        Phone.listen(this)
    }

    override fun onStop() {
        Phone.stopListening(this)
        super.onStop()
    }

    internal fun keepAwake(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Widok nałożony na ekran rozmowy: szybkie odpowiedzi albo klawiatura DTMF. */
internal enum class Overlay { NONE, REPLIES, KEYPAD }

@Composable
private fun WatchRoot() {
    val state by Phone.call.collectAsStateWithLifecycle()
    val directory by Phone.directory.collectAsStateWithLifecycle()
    val reachable by Phone.reachable.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf(Overlay.NONE) }

    val call = state.primary
    KeepAwake(call != null)
    // Zmiana stanu rozmowy zamyka nakładkę – po odebraniu nie zostaje lista szybkich odpowiedzi.
    LaunchedEffect(call?.state) { overlay = Overlay.NONE }

    Scaffold(timeText = { if (call == null) TimeText() }) {
        when {
            call == null && !reachable -> NoPhoneScreen()
            call == null -> DirectoryScreen(directory)
            overlay == Overlay.REPLIES -> RepliesScreen(state.quickReplies) { overlay = Overlay.NONE }
            overlay == Overlay.KEYPAD -> KeypadScreen { overlay = Overlay.NONE }
            call.ringing -> IncomingScreen(state) { overlay = it }
            else -> ActiveScreen(state) { overlay = it }
        }
    }
}

@Composable
private fun KeepAwake(on: Boolean) {
    val activity = LocalContext.current as? WatchActivity ?: return
    DisposableEffect(on) {
        activity.keepAwake(on)
        onDispose { activity.keepAwake(false) }
    }
}
