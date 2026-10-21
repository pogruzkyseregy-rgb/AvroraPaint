package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun toColor(): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    companion object {
        fun of(c: Color): Hsv {
            val a = FloatArray(3)
            android.graphics.Color.colorToHSV(c.toArgb(), a)
            return Hsv(a[0], a[1], a[2])
        }
    }
}

private val Rainbow = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
)

// Квадрат насыщенности/яркости и полоса оттенка.
@Composable
fun HsvPicker(color: Color, onChange: (Color) -> Unit) {
    var hsv by remember { mutableStateOf(Hsv.of(color)) }
    val latest by rememberUpdatedState(onChange)

    // Цвет поменялся снаружи (например, пипеткой) — подстраиваемся.
    LaunchedEffect(color) {
        if (hsv.toColor() != color) hsv = Hsv.of(color)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(p: Offset) {
                            val s = (p.x / size.width).coerceIn(0f, 1f)
                            val v = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            hsv = hsv.copy(s = s, v = v)
                            latest(hsv.toColor())
                        }
                        update(down.position)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            update(ch.position)
                            ch.consume()
                        }
                    }
                }
        ) {
            val hueColor = Hsv(hsv.h, 1f, 1f).toColor()
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val c = Offset(hsv.s * size.width, (1f - hsv.v) * size.height)
            drawCircle(Color.Black, radius = 11.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.White, radius = 9.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
        }

        Spacer(Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(p: Offset) {
                            val h = (p.x / size.width).coerceIn(0f, 1f) * 360f
                            hsv = hsv.copy(h = h.coerceAtMost(359.9f))
                            latest(hsv.toColor())
                        }
                        update(down.position)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            update(ch.position)
                            ch.consume()
                        }
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(Rainbow))
            val x = hsv.h / 360f * size.width
            drawRect(
                Color.White,
                topLeft = Offset(x - 3.dp.toPx(), 0f),
                size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
