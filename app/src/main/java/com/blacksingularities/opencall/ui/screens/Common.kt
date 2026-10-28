package com.blacksingularities.opencall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.UpdateBanner
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive

/** Nagłówek zakładki: duży tytuł, akcje i ustawienia (z plakietką, gdy jest aktualizacja). */
@Composable
fun TabHeader(vm: AppViewModel, title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
                RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), badge = if (vm.updatePending) "1" else null) { vm.push(Route.Settings) }
            }
        }
        UpdateBanner(vm, Modifier.padding(top = 10.dp, end = 6.dp))
    }
}

/** Pole wyszukiwania w kształcie pigułki. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Palette.TextFaint, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                tr("Wyczyść"),
                tint = Palette.TextDim,
                modifier = Modifier.size(20.dp).reactive(tr("Wyczyść")) { onChange("") },
            )
        }
    }
}

/**
 * Karta konfiguracji: rola domyślnej aplikacji telefonu (ekran rozmowy, blokowanie, rejestr)
 * oraz brakujące uprawnienia. Znika, gdy wszystko jest gotowe.
 */
@Composable
fun SetupCard(vm: AppViewModel, onRequestRole: () -> Unit, onRequestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val needRole = !vm.isDefaultDialer
    val needPerms = vm.missingPermissions.isNotEmpty()
    if (!needRole && !needPerms) return
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (needRole) Icons.AutoMirrored.Rounded.PhoneForwarded else Icons.Rounded.Lock, null, tint = Palette.Ink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (needRole) tr("Ustaw OpenCall jako aplikację telefonu") else tr("Zezwól na dostęp"),
                color = Palette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (needRole) {
                tr("Wtedy OpenCall obsługuje ekran rozmowy, odbieranie, blokowanie numerów i powiadomienia o nieodebranych.")
            } else {
                tr("Kontakty, historia połączeń i telefon są potrzebne, żeby wyświetlać listy i dzwonić.")
            },
            color = Palette.Ink.copy(alpha = 0.65f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        PillButtonDark(if (needRole) tr("Ustaw jako domyślną") else tr("Zezwól")) {
            if (needRole) onRequestRole() else onRequestPermissions()
        }
    }
}

@Composable
private fun PillButtonDark(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier
            .reactive(text, pressScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Palette.Ink)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Komunikat o braku uprawnienia w miejscu listy. */
@Composable
fun NoPermission(text: String, onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, null, tint = Palette.TextDim, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = Palette.TextDim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        PillButton(tr("Zezwól"), onClick = onRequest)
    }
}

/** Nagłówek grupy na liście („DZISIAJ”, „A”). */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Palette.Amber,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
    )
}
