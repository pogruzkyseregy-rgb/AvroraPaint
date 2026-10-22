package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Собственный набор линейных иконок в сетке 24×24.
enum class Ico {
    BACK, UNDO, REDO, MORE, CLOSE, PLUS,
    PEN, SOFT, AIRBRUSH, MARKER, ERASER, FILL, GRADIENT, PICKER, LINE, RECT, OVAL,
    LAYERS, EYE, EYE_OFF, COPY, MERGE, UP, DOWN, PHOTO, TRASH, CLEAR, LOCK,
    FLIP, FIT, SAVE, EXPORT, HELP,
    SELECT_RECT, LASSO, TRANSFORM, CUT, PASTE, FX, INVERT, SELECT_ALL, DESELECT, ROTATE, FLIP_V, CHECK
}

fun Tool.icon(): Ico = when (this) {
    Tool.PEN -> Ico.PEN
    Tool.SOFT -> Ico.SOFT
    Tool.AIRBRUSH -> Ico.AIRBRUSH
    Tool.MARKER -> Ico.MARKER
    Tool.ERASER -> Ico.ERASER
    Tool.FILL -> Ico.FILL
    Tool.GRADIENT -> Ico.GRADIENT
    Tool.PICKER -> Ico.PICKER
    Tool.LINE -> Ico.LINE
    Tool.RECT -> Ico.RECT
    Tool.OVAL -> Ico.OVAL
    Tool.SELECT_RECT -> Ico.SELECT_RECT
    Tool.SELECT_LASSO -> Ico.LASSO
}

@Composable
fun AvIcon(icon: Ico, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIco(icon, tint) }
}

// Помощник: координаты задаются в сетке 24×24 и масштабируются.
private class G(val scope: DrawScope, val tint: Color) {
    val u = scope.size.minDimension / 24f
    val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun o(x: Float, y: Float) = Offset(x * u, y * u)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 1.9f) {
        scope.drawLine(tint, o(x1, y1), o(x2, y2), strokeWidth = w * u, cap = StrokeCap.Round)
    }

    fun poly(vararg pts: Float, close: Boolean = false, fill: Boolean = false) {
        val p = Path()
        p.moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i + 1 < pts.size) {
            p.lineTo(pts[i] * u, pts[i + 1] * u)
            i += 2
        }
        if (close) p.close()
        if (fill) scope.drawPath(p, tint) else scope.drawPath(p, tint, style = stroke)
    }

    fun circle(x: Float, y: Float, r: Float, fill: Boolean = false, alpha: Float = 1f) {
        scope.drawCircle(
            color = tint.copy(alpha = tint.alpha * alpha),
            radius = r * u,
            center = o(x, y),
            style = if (fill) Fill else stroke
        )
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, fill: Boolean = false) {
        scope.drawRect(
            color = tint,
            topLeft = o(l, t),
            size = Size((r - l) * u, (b - t) * u),
            style = if (fill) Fill else stroke
        )
    }

    fun oval(l: Float, t: Float, r: Float, b: Float) {
        scope.drawOval(color = tint, topLeft = o(l, t), size = Size((r - l) * u, (b - t) * u), style = stroke)
    }

    fun path(fill: Boolean = false, block: Path.() -> Unit) {
        val p = Path().apply(block)
        if (fill) scope.drawPath(p, tint) else scope.drawPath(p, tint, style = stroke)
    }

    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x3 * u, y3 * u)
}

fun DrawScope.drawIco(icon: Ico, tint: Color) {
    with(G(this, tint)) {
        when (icon) {
            Ico.BACK -> poly(15f, 5f, 8f, 12f, 15f, 19f)
            Ico.UNDO -> {
                path {
                    m(4f, 9f); l(14f, 9f)
                    c(18f, 9f, 20f, 12f, 20f, 15f)
                    c(20f, 18f, 17f, 20f, 14f, 20f)
                    l(9f, 20f)
                }
                poly(8f, 5f, 4f, 9f, 8f, 13f)
            }
            Ico.REDO -> {
                path {
                    m(20f, 9f); l(10f, 9f)
                    c(6f, 9f, 4f, 12f, 4f, 15f)
                    c(4f, 18f, 7f, 20f, 10f, 20f)
                    l(15f, 20f)
                }
                poly(16f, 5f, 20f, 9f, 16f, 13f)
            }
            Ico.MORE -> {
                circle(12f, 5f, 1.8f, fill = true)
                circle(12f, 12f, 1.8f, fill = true)
                circle(12f, 19f, 1.8f, fill = true)
            }
            Ico.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            Ico.PLUS -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            Ico.PEN -> {
                poly(12f, 3f, 18f, 13f, 12f, 21f, 6f, 13f, close = true)
                line(12f, 9f, 12f, 14f)
                circle(12f, 15f, 1.2f, fill = true)
            }
            Ico.SOFT -> {
                circle(12f, 12f, 9f, fill = true, alpha = 0.2f)
                circle(12f, 12f, 6.5f, fill = true, alpha = 0.35f)
                circle(12f, 12f, 4f, fill = true, alpha = 0.7f)
                circle(12f, 12f, 2f, fill = true)
            }
            Ico.AIRBRUSH -> {
                circle(7f, 8f, 1.3f, fill = true)
                circle(12f, 5f, 1.1f, fill = true)
                circle(16f, 9f, 1.5f, fill = true)
                circle(10f, 12f, 1.6f, fill = true)
                circle(15f, 14f, 1.2f, fill = true)
                circle(6f, 15f, 1.1f, fill = true)
                circle(11f, 18f, 1.4f, fill = true)
                circle(18f, 18f, 1f, fill = true)
                circle(19f, 5f, 0.9f, fill = true)
            }
            Ico.MARKER -> {
                poly(15f, 3f, 21f, 9f, 11f, 19f, 5f, 13f, close = true)
                poly(5f, 13f, 11f, 19f, 4f, 20f, close = true, fill = true)
            }
            Ico.ERASER -> {
                poly(14f, 4f, 20f, 10f, 11f, 19f, 5f, 13f, close = true)
                line(8f, 10f, 14f, 16f)
                line(12f, 20f, 20f, 20f)
            }
            Ico.FILL -> {
                poly(5f, 11f, 12f, 4f, 19f, 11f, 12f, 18f, close = true)
                line(5f, 11f, 19f, 11f)
                circle(20.5f, 17f, 1.8f, fill = true)
            }
            Ico.GRADIENT -> {
                scope.drawRect(
                    brush = Brush.horizontalGradient(listOf(tint, tint.copy(alpha = 0.05f)), startX = 4f * u, endX = 20f * u),
                    topLeft = o(4f, 4f),
                    size = Size(16f * u, 16f * u)
                )
                rect(4f, 4f, 20f, 20f)
            }
            Ico.PICKER -> {
                line(5f, 19f, 14f, 10f, 2.4f)
                circle(16.5f, 7.5f, 3.2f, fill = true)
                line(11f, 7f, 17f, 13f)
                circle(4f, 20f, 1f, fill = true)
            }
            Ico.LINE -> {
                line(5f, 19f, 19f, 5f)
                circle(5f, 19f, 1.8f, fill = true)
                circle(19f, 5f, 1.8f, fill = true)
            }
            Ico.RECT -> rect(4f, 6f, 20f, 18f)
            Ico.OVAL -> oval(3f, 6f, 21f, 18f)
            Ico.LAYERS -> {
                poly(12f, 4f, 20f, 9f, 12f, 14f, 4f, 9f, close = true)
                poly(4f, 13f, 12f, 18f, 20f, 13f)
                poly(4f, 17f, 12f, 22f, 20f, 17f)
            }
            Ico.EYE -> {
                path {
                    m(2f, 12f)
                    c(6f, 5f, 18f, 5f, 22f, 12f)
                    c(18f, 19f, 6f, 19f, 2f, 12f)
                }
                circle(12f, 12f, 3f, fill = true)
            }
            Ico.EYE_OFF -> {
                path {
                    m(2f, 12f)
                    c(6f, 5f, 18f, 5f, 22f, 12f)
                    c(18f, 19f, 6f, 19f, 2f, 12f)
                }
                line(4f, 4f, 20f, 20f)
            }
            Ico.COPY -> {
                rect(8f, 8f, 20f, 20f)
                poly(16f, 4f, 4f, 4f, 4f, 16f)
            }
            Ico.MERGE -> {
                line(12f, 4f, 12f, 15f)
                poly(7f, 10f, 12f, 15f, 17f, 10f)
                line(5f, 20f, 19f, 20f)
            }
            Ico.UP -> poly(6f, 15f, 12f, 9f, 18f, 15f)
            Ico.DOWN -> poly(6f, 9f, 12f, 15f, 18f, 9f)
            Ico.PHOTO -> {
                rect(3f, 5f, 21f, 19f)
                poly(3f, 17f, 9f, 11f, 13f, 15f, 16f, 12f, 21f, 17f)
                circle(16f, 9f, 1.5f, fill = true)
            }
            Ico.TRASH -> {
                line(4f, 7f, 20f, 7f)
                poly(9f, 7f, 10f, 4f, 14f, 4f, 15f, 7f)
                poly(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
                line(10f, 11f, 10f, 16f)
                line(14f, 11f, 14f, 16f)
            }
            Ico.CLEAR -> {
                circle(12f, 12f, 8f)
                line(6.5f, 17.5f, 17.5f, 6.5f)
            }
            Ico.LOCK -> {
                rect(6f, 11f, 18f, 20f)
                path {
                    m(8.5f, 11f); l(8.5f, 8f)
                    c(8.5f, 4f, 15.5f, 4f, 15.5f, 8f)
                    l(15.5f, 11f)
                }
            }
            Ico.FLIP -> {
                line(12f, 3f, 12f, 21f, 1.4f)
                poly(9f, 6f, 9f, 18f, 3f, 18f, close = true, fill = true)
                poly(15f, 6f, 15f, 18f, 21f, 18f, close = true)
            }
            Ico.FIT -> {
                poly(4f, 9f, 4f, 4f, 9f, 4f)
                poly(15f, 4f, 20f, 4f, 20f, 9f)
                poly(20f, 15f, 20f, 20f, 15f, 20f)
                poly(9f, 20f, 4f, 20f, 4f, 15f)
            }
            Ico.SAVE -> {
                line(12f, 3f, 12f, 14f)
                poly(7f, 9f, 12f, 14f, 17f, 9f)
                poly(4f, 15f, 4f, 20f, 20f, 20f, 20f, 15f)
            }
            Ico.EXPORT -> {
                rect(4f, 4f, 20f, 20f)
                poly(4f, 16f, 9f, 11f, 13f, 15f, 16f, 12f, 20f, 16f)
                circle(15.5f, 8.5f, 1.4f, fill = true)
            }
            Ico.SELECT_RECT -> {
                // Пунктирная рамка
                line(4f, 4f, 8f, 4f); line(11f, 4f, 13f, 4f); line(16f, 4f, 20f, 4f)
                line(4f, 20f, 8f, 20f); line(11f, 20f, 13f, 20f); line(16f, 20f, 20f, 20f)
                line(4f, 4f, 4f, 8f); line(4f, 11f, 4f, 13f); line(4f, 16f, 4f, 20f)
                line(20f, 4f, 20f, 8f); line(20f, 11f, 20f, 13f); line(20f, 16f, 20f, 20f)
            }
            Ico.LASSO -> {
                path {
                    m(12f, 4f)
                    c(18f, 4f, 21f, 7f, 21f, 10f)
                    c(21f, 13f, 17f, 15f, 12f, 15f)
                    c(7f, 15f, 3f, 13f, 3f, 10f)
                    c(3f, 7f, 6f, 4f, 12f, 4f)
                }
                path {
                    m(8f, 14f)
                    c(7f, 17f, 9f, 19f, 7f, 21f)
                }
                circle(7f, 21f, 1.2f, fill = true)
            }
            Ico.TRANSFORM -> {
                rect(6f, 6f, 18f, 18f)
                rect(3f, 3f, 7f, 7f, fill = true)
                rect(17f, 3f, 21f, 7f, fill = true)
                rect(3f, 17f, 7f, 21f, fill = true)
                rect(17f, 17f, 21f, 21f, fill = true)
            }
            Ico.CUT -> {
                circle(6.5f, 17.5f, 3f)
                circle(17.5f, 17.5f, 3f)
                line(8.5f, 15.5f, 18f, 3f)
                line(15.5f, 15.5f, 6f, 3f)
            }
            Ico.PASTE -> {
                rect(5f, 5f, 19f, 21f)
                rect(9f, 3f, 15f, 7f, fill = true)
                line(9f, 12f, 15f, 12f)
                line(9f, 16f, 13f, 16f)
            }
            Ico.FX -> {
                path {
                    m(12f, 2f); l(14f, 9f); l(21f, 11f); l(14f, 13f)
                    l(12f, 20f); l(10f, 13f); l(3f, 11f); l(10f, 9f); close()
                }
                circle(19f, 19f, 1.5f, fill = true)
                circle(5f, 4f, 1f, fill = true)
            }
            Ico.INVERT -> {
                circle(12f, 12f, 8.5f)
                path(fill = true) {
                    m(12f, 3.5f)
                    c(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
                    c(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
                    close()
                }
            }
            Ico.SELECT_ALL -> {
                rect(4f, 4f, 20f, 20f)
                rect(8f, 8f, 16f, 16f, fill = true)
            }
            Ico.DESELECT -> {
                line(4f, 4f, 8f, 4f); line(16f, 4f, 20f, 4f)
                line(4f, 20f, 8f, 20f); line(16f, 20f, 20f, 20f)
                line(4f, 4f, 4f, 8f); line(4f, 16f, 4f, 20f)
                line(20f, 4f, 20f, 8f); line(20f, 16f, 20f, 20f)
                line(9f, 9f, 15f, 15f)
                line(15f, 9f, 9f, 15f)
            }
            Ico.ROTATE -> {
                path {
                    m(19f, 12f)
                    c(19f, 16f, 16f, 19f, 12f, 19f)
                    c(8f, 19f, 5f, 16f, 5f, 12f)
                    c(5f, 8f, 8f, 5f, 12f, 5f)
                    l(16f, 5f)
                }
                poly(13f, 2f, 16f, 5f, 13f, 8f)
            }
            Ico.FLIP_V -> {
                line(3f, 12f, 21f, 12f, 1.4f)
                poly(6f, 9f, 18f, 9f, 18f, 3f, close = true, fill = true)
                poly(6f, 15f, 18f, 15f, 18f, 21f, close = true)
            }
            Ico.CHECK -> poly(5f, 12f, 10f, 17f, 19f, 7f)
            Ico.HELP -> {
                circle(12f, 12f, 9f)
                path {
                    m(9.5f, 9.5f)
                    c(9.5f, 6.5f, 14.5f, 6.5f, 14.5f, 9.5f)
                    c(14.5f, 12f, 12f, 12f, 12f, 14.5f)
                }
                circle(12f, 17.5f, 1.1f, fill = true)
            }
        }
    }
}
