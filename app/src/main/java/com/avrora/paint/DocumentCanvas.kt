package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

// Состояние вида: масштаб и смещение холста на экране.
class ViewState {
    var scale by mutableFloatStateOf(1f)
    var left by mutableFloatStateOf(0f)
    var top by mutableFloatStateOf(0f)
    var fitScale = 1f

    private var viewW = 0
    private var viewH = 0

    fun resize(w: Int, h: Int) {
        viewW = w
        viewH = h
    }

    // Вписываем весь документ в экран.
    fun fit(doc: Document) {
        if (viewW == 0 || viewH == 0) return
        fitScale = min(viewW.toFloat() / doc.width, viewH.toFloat() / doc.height) * 0.9f
        scale = fitScale
        left = (viewW - doc.width * fitScale) / 2f
        top = (viewH - doc.height * fitScale) / 2f
    }

    // Переводим точку экрана в координаты документа.
    fun toDoc(p: Offset): Offset = Offset((p.x - left) / scale, (p.y - top) / scale)
}

@Composable
fun DocumentCanvas(
    doc: Document,
    view: ViewState,
    tool: Tool,
    color: Color,
    secondColor: Color,
    width: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                view.resize(it.width, it.height)
                view.fit(doc)
            }
            .pointerInput(doc, tool, color, secondColor, width) {
                awaitPointerEventScope {
                    var drawing = false
                    var gradStart: Offset? = null
                    var gradEnd: Offset? = null
                    var prevCentroid: Offset? = null
                    var prevDist = 0f

                    // Завершаем мазок или заливку градиентом.
                    fun finishDrawing() {
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
                        drawing = false
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        when {
                            // Два пальца: масштаб и перемещение холста
                            pressed.size >= 2 -> {
                                if (drawing) finishDrawing()
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                val dist = (a - b).getDistance()
                                val pc = prevCentroid
                                if (pc != null && prevDist > 0f) {
                                    // Точка документа под центром жеста остаётся на месте.
                                    val docPoint = view.toDoc(pc)
                                    val newScale = (view.scale * (dist / prevDist))
                                        .coerceIn(view.fitScale * 0.8f, view.fitScale * 8f)
                                    view.scale = newScale
                                    view.left = centroid.x - docPoint.x * newScale
                                    view.top = centroid.y - docPoint.y * newScale
                                }
                                prevCentroid = centroid
                                prevDist = dist
                                pressed.forEach { it.consume() }
                            }

                            // Один палец: рисуем
                            pressed.size == 1 -> {
                                prevCentroid = null
                                prevDist = 0f
                                val p = pressed[0]
                                val d = view.toDoc(p.position)
                                if (p.changedToDown()) {
                                    if (tool == Tool.GRADIENT) {
                                        gradStart = d
                                        gradEnd = d
                                    } else {
                                        doc.beginAction()
                                        doc.stroke(d.x, d.y, tool, color.toArgb(), width)
                                    }
                                    drawing = true
                                } else if (drawing) {
                                    if (tool == Tool.GRADIENT) {
                                        gradEnd = d
                                    } else {
                                        doc.stroke(d.x, d.y, tool, color.toArgb(), width)
                                    }
                                }
                                p.consume()
                            }

                            // Пальцев нет: завершаем, если что-то рисовали
                            else -> {
                                prevCentroid = null
                                prevDist = 0f
                                if (drawing) finishDrawing()
                            }
                        }
                    }
                }
            }
    ) {
        // Подписываемся на изменения, чтобы холст перерисовывался.
        val rev = doc.revision

        val w = doc.width * view.scale
        val h = doc.height * view.scale

        // Белая бумага под документом.
        drawRect(Color.White, topLeft = Offset(view.left, view.top), size = Size(w, h))

        // Слои рисуем снизу вверх.
        for (layer in doc.layers) {
            if (!layer.visible) continue
            drawImage(
                image = layer.bitmap.asImageBitmap(),
                srcSize = IntSize(doc.width, doc.height),
                dstOffset = IntOffset(view.left.toInt(), view.top.toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                alpha = layer.opacity
            )
        }
    }
}
