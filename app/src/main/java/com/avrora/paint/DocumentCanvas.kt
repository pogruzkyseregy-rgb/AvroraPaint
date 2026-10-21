package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

// Соответствие между координатами экрана и координатами документа.
class Viewport(val scale: Float, val left: Float, val top: Float) {
    fun toDoc(p: Offset): Offset = Offset((p.x - left) / scale, (p.y - top) / scale)
}

// Вписываем документ в экран с небольшим отступом.
fun viewportFor(doc: Document, view: IntSize): Viewport {
    if (view.width == 0 || view.height == 0) return Viewport(1f, 0f, 0f)
    val scale = min(view.width.toFloat() / doc.width, view.height.toFloat() / doc.height) * 0.9f
    val w = doc.width * scale
    val h = doc.height * scale
    return Viewport(scale, (view.width - w) / 2f, (view.height - h) / 2f)
}

@Composable
fun DocumentCanvas(
    doc: Document,
    tool: Tool,
    color: Color,
    secondColor: Color,
    width: Float,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(doc, tool, color, secondColor, width) {
                var gradStart: Offset? = null
                var gradEnd: Offset? = null
                detectDragGestures(
                    onDragStart = { p ->
                        val d = viewportFor(doc, viewSize).toDoc(p)
                        if (tool == Tool.GRADIENT) {
                            gradStart = d
                            gradEnd = d
                        } else {
                            doc.beginAction()
                            doc.stroke(d.x, d.y, tool, color.toArgb(), width)
                        }
                    },
                    onDrag = { change, _ ->
                        val d = viewportFor(doc, viewSize).toDoc(change.position)
                        if (tool == Tool.GRADIENT) {
                            gradEnd = d
                        } else {
                            doc.stroke(d.x, d.y, tool, color.toArgb(), width)
                        }
                    },
                    onDragEnd = {
                        if (tool == Tool.GRADIENT) {
                            val s = gradStart
                            val e = gradEnd
                            if (s != null && e != null) {
                                doc.beginAction()
                                doc.fillGradient(s.x, s.y, e.x, e.y, color.toArgb(), secondColor.toArgb())
                            }
                        } else {
                            doc.endStroke()
                        }
                    },
                    onDragCancel = { doc.endStroke() }
                )
            }
    ) {
        // Подписываемся на изменения, чтобы холст перерисовывался.
        val rev = doc.revision

        val v = viewportFor(doc, viewSize)
        val w = doc.width * v.scale
        val h = doc.height * v.scale

        // Белая бумага под документом.
        drawRect(Color.White, topLeft = Offset(v.left, v.top), size = Size(w, h))

        // Слои рисуем снизу вверх.
        for (layer in doc.layers) {
            if (!layer.visible) continue
            drawImage(
                image = layer.bitmap.asImageBitmap(),
                srcSize = IntSize(doc.width, doc.height),
                dstOffset = IntOffset(v.left.toInt(), v.top.toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                alpha = layer.opacity
            )
        }
    }
}
