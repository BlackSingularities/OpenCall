package com.blacksingularities.opencall.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pełnoekranowe kadrowanie zdjęcia kontaktu: przesuwanie, przybliżanie dwoma palcami, dwuklik
 * i obrót o 90°. [onDone] dostaje kwadrat w pikselach bitmapy (lewo, góra, bok) i bitmapę po obrocie.
 */
@Composable
fun PhotoCropper(source: Bitmap, onCancel: () -> Unit, onDone: (Bitmap, Int, Int, Int) -> Unit) {
    var bmp by remember { mutableStateOf(source) }
    val image = remember(bmp) { bmp.asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    val diameter = (minOf(box.width, box.height) - margin * 2).coerceAtLeast(1f)
    val minScale = diameter / minOf(bmp.width, bmp.height)
    val maxScale = max(minScale * 6f, minScale)
    // Skala i przesunięcie środka zdjęcia względem środka koła.
    val scale = remember(bmp) { Animatable(0f) }
    val offset = remember(bmp) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bmp, box) {
        if (box != IntSize.Zero) {
            scale.snapTo(minScale)
            offset.snapTo(Offset.Zero)
        }
    }

    fun clampOffset(o: Offset, s: Float): Offset {
        val mx = ((bmp.width * s - diameter) / 2f).coerceAtLeast(0f)
        val my = ((bmp.height * s - diameter) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    fun animateTo(s: Float, o: Offset) {
        val target = s.coerceIn(minScale, maxScale)
        val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        scope.launch { scale.animateTo(target, spec) }
        scope.launch { offset.animateTo(clampOffset(o, target), spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Text(
                tr("Kadruj zdjęcie"),
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp),
            )
            Text(
                tr("Przesuń i przybliż dwoma palcami"),
                color = Palette.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 8.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { box = it }
                    .pointerInput(bmp, diameter) {
                        detectTapGestures(onDoubleTap = { p ->
                            val s = scale.value
                            val c = p - Offset(size.width / 2f, size.height / 2f)
                            val target = if (s < minScale * 1.8f) minScale * 2.5f else minScale
                            animateTo(target, (offset.value - c) * (target / s) + c)
                        })
                    }
                    .pointerInput(bmp, diameter) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            scope.launch { scale.stop(); offset.stop() }
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val s = scale.value
                                    // Gumka poza zakresem – opór zamiast twardej blokady.
                                    val rawTarget = s * zoom
                                    val ns = when {
                                        rawTarget < minScale -> s * (1f + (zoom - 1f) * 0.35f)
                                        rawTarget > maxScale -> s * (1f + (zoom - 1f) * 0.35f)
                                        else -> rawTarget
                                    }
                                    val c = if (centroid == Offset.Unspecified) Offset.Zero else centroid - Offset(size.width / 2f, size.height / 2f)
                                    var o = (offset.value - c) * (ns / s) + c + pan
                                    val limit = clampOffset(o, ns)
                                    o = Offset(
                                        if (o.x != limit.x) limit.x + (o.x - limit.x) * 0.35f else o.x,
                                        if (o.y != limit.y) limit.y + (o.y - limit.y) * 0.35f else o.y,
                                    )
                                    scope.launch { scale.snapTo(ns); offset.snapTo(o) }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            animateTo(scale.value, offset.value)
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    val s = scale.value.takeIf { it > 0f } ?: return@Canvas
                    val w = bmp.width * s
                    val h = bmp.height * s
                    val cx = size.width / 2f + offset.value.x
                    val cy = size.height / 2f + offset.value.y
                    drawImage(
                        image,
                        dstOffset = IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                    // Przyciemnienie poza kołem kadru.
                    drawRect(Color.Black.copy(alpha = 0.62f))
                    drawCircle(Color.Black, radius = diameter / 2f, blendMode = BlendMode.DstOut)
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = diameter / 2f, style = Stroke(1.5.dp.toPx()))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(tr("Anuluj"), primary = false, icon = Icons.Rounded.Close, onClick = onCancel)
                RoundIcon(Icons.Rounded.Rotate90DegreesCw, tr("Obróć")) {
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(90f) }, true)
                }
                Box(Modifier.weight(1f))
                PillButton(tr("Gotowe"), icon = Icons.Rounded.Check) {
                    val s = scale.value.takeIf { it > 0f } ?: minScale
                    val o = clampOffset(offset.value, s)
                    val side = (diameter / s).roundToInt()
                    val left = (bmp.width / 2f - (o.x + diameter / 2f) / s).roundToInt()
                    val top = (bmp.height / 2f - (o.y + diameter / 2f) / s).roundToInt()
                    onDone(bmp, left, top, side)
                }
            }
        }
    }
}
