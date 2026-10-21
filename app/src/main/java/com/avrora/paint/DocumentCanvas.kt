package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// Масштаб, смещение и отражение холста на экране.
class ViewState {
    var scale by mutableFloatStateOf(1f)
    var left by mutableFloatStateOf(0f)
    var top by mutableFloatStateOf(0f)
    var flipped by mutableStateOf(false)

    var fitScale = 1f
        private set

    private var viewW = 0
    private var viewH = 0
    private var docW = 1

    fun resize(w: Int, h: Int) {
        viewW = w
        viewH = h
    }

    // Вписываем весь холст в экран.
    fun fit(doc: Document) {
        docW = doc.width
        if (viewW == 0 || viewH == 0) return
        fitScale = min(viewW.toFloat() / doc.width, viewH.toFloat() / doc.height) * 0.92f
        scale = fitScale
        left = (viewW - doc.width * fitScale) / 2f
        top = (viewH - doc.height * fitScale) / 2f
    }

    private fun toDocRaw(p: Offset): Offset = Offset((p.x - left) / scale, (p.y - top) / scale)

    // Точка экрана → точка на холсте (с учётом отражения).
    fun toDoc(p: Offset): Offset {
        val raw = toDocRaw(p)
        return if (flipped) Offset(docW - raw.x, raw.y) else raw
    }

    // Масштаб двумя пальцами: точка холста под пальцами остаётся под пальцами.
    fun zoomPan(prevCentroid: Offset, centroid: Offset, factor: Float) {
        val docPoint = toDocRaw(prevCentroid)
        val newScale = (scale * factor).coerceIn(fitScale * 0.25f, fitScale * 12f)
        scale = newScale
        left = centroid.x - docPoint.x * newScale
        top = centroid.y - docPoint.y * newScale
    }
}

private enum class Gesture { PENDING, DRAW, PICK, MULTI, IGNORE }

@Composable
fun DocumentCanvas(
    doc: Document,
    view: ViewState,
    editor: EditorState,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged {
                view.resize(it.width, it.height)
                view.fit(doc)
            }
            .pointerInput(doc, view, editor) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = down.uptimeMillis
                    var lastTime = startTime
                    val slop = viewConfiguration.touchSlop
                    val longPress = viewConfiguration.longPressTimeoutMillis
                    val downPos = down.position
                    var lastPos = downPos
                    var maxPointers = 1
                    var drawStart = 0L
                    var smooth = view.toDoc(downPos)
                    var prevCentroid: Offset? = null
                    var prevDist = 0f
                    var multiTravel = 0f
                    var mode = if (editor.tool == Tool.PICKER) Gesture.PICK else Gesture.PENDING

                    // Стабилизатор: точка догоняет палец с задержкой.
                    fun moveSmooth(screen: Offset) {
                        val target = view.toDoc(screen)
                        val k = if (editor.tool.isBrush) 1f - editor.smoothing else 1f
                        smooth = smooth + (target - smooth) * k
                        editor.move(doc, smooth)
                    }

                    fun finishDraw() {
                        if (editor.tool.isBrush && editor.smoothing > 0f) {
                            val target = view.toDoc(lastPos)
                            repeat(4) {
                                smooth = smooth + (target - smooth) * 0.5f
                                editor.move(doc, smooth)
                            }
                        }
                        editor.end(doc)
                    }

                    if (mode == Gesture.PICK) {
                        editor.pickAt(doc, view.toDoc(downPos))
                        editor.loupe = downPos
                    }
                    down.consume()

                    while (true) {
                        // Пока палец не сдвинулся, ждём событие не дольше
                        // времени долгого нажатия. Не дождались — включаем пипетку.
                        val event: PointerEvent? = if (mode == Gesture.PENDING) {
                            val remain = longPress - (lastTime - startTime)
                            if (remain <= 0L) null else withTimeoutOrNull(remain) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            mode = Gesture.PICK
                            editor.pickAt(doc, view.toDoc(lastPos))
                            editor.loupe = lastPos
                            continue
                        }

                        val changes = event.changes
                        for (c in changes) {
                            if (c.uptimeMillis > lastTime) lastTime = c.uptimeMillis
                        }
                        val pressed = changes.filter { it.pressed }
                        if (pressed.size > maxPointers) maxPointers = pressed.size

                        // Все пальцы подняты — завершаем жест.
                        if (pressed.isEmpty()) {
                            when (mode) {
                                Gesture.PENDING -> editor.tap(doc, view.toDoc(downPos))
                                Gesture.DRAW -> finishDraw()
                                Gesture.PICK -> {
                                    editor.loupe = null
                                    editor.pushRecent(editor.color)
                                }
                                Gesture.MULTI -> {
                                    // Быстрое касание без движения: 2 пальца — отмена, 3 — повтор.
                                    val quick = lastTime - startTime < 400 && multiTravel < slop * 4
                                    if (quick && maxPointers == 2) doc.undo()
                                    if (quick && maxPointers >= 3) doc.redo()
                                }
                                Gesture.IGNORE -> Unit
                            }
                            changes.forEach { it.consume() }
                            break
                        }

                        // Появился второй палец — переходим в масштаб.
                        if (pressed.size >= 2 && mode != Gesture.MULTI) {
                            when (mode) {
                                Gesture.DRAW -> if (lastTime - drawStart < 250) editor.cancel(doc) else finishDraw()
                                Gesture.PICK -> editor.loupe = null
                                else -> Unit
                            }
                            mode = Gesture.MULTI
                            prevCentroid = null
                        }

                        when (mode) {
                            Gesture.PENDING -> {
                                val p = pressed[0].position
                                lastPos = p
                                if ((p - downPos).getDistance() > slop) {
                                    if (editor.drawsOnDrag) {
                                        mode = Gesture.DRAW
                                        drawStart = lastTime
                                        smooth = view.toDoc(downPos)
                                        editor.begin(doc, smooth)
                                        moveSmooth(p)
                                    } else {
                                        mode = Gesture.IGNORE
                                    }
                                }
                            }
                            Gesture.DRAW -> {
                                val p = pressed[0].position
                                lastPos = p
                                moveSmooth(p)
                            }
                            Gesture.PICK -> {
                                val p = pressed[0].position
                                lastPos = p
                                editor.loupe = p
                                editor.pickAt(doc, view.toDoc(p))
                            }
                            Gesture.MULTI -> {
                                if (pressed.size >= 2) {
                                    val a = pressed[0].position
                                    val b = pressed[1].position
                                    val centroid = (a + b) / 2f
                                    val dist = (a - b).getDistance()
                                    val pc = prevCentroid
                                    if (pc != null && prevDist > 0f) {
                                        multiTravel += (centroid - pc).getDistance() + abs(dist - prevDist)
                                        view.zoomPan(pc, centroid, dist / prevDist)
                                    }
                                    prevCentroid = centroid
                                    prevDist = dist
                                } else {
                                    prevCentroid = null
                                }
                            }
                            Gesture.IGNORE -> Unit
                        }
                        changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        // Читаем счётчик, чтобы холст перерисовывался после каждого изменения.
        @Suppress("UNUSED_VARIABLE")
        val rev = doc.revision

        val w = doc.width * view.scale
        val h = doc.height * view.scale
        val topLeft = Offset(view.left, view.top)
        val pivot = Offset(view.left + w / 2f, view.top + h / 2f)
        val quality = if (view.scale > 2f) FilterQuality.None else FilterQuality.Low
        val dstOffset = IntOffset(view.left.roundToInt(), view.top.roundToInt())
        val dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
        val srcSize = IntSize(doc.width, doc.height)

        // Неоновая рамка вокруг листа.
        drawRect(
            AvroraColors.Violet.copy(alpha = 0.7f),
            topLeft = topLeft - Offset(2f, 2f),
            size = Size(w + 4f, h + 4f)
        )

        scale(scaleX = if (view.flipped) -1f else 1f, scaleY = 1f, pivot = pivot) {
            drawRect(Color.White, topLeft = topLeft, size = Size(w, h))
            doc.layers.forEachIndexed { i, layer ->
                if (layer.visible) {
                    drawImage(
                        image = layer.bitmap.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = srcSize,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        alpha = layer.opacity,
                        blendMode = layer.blend.composeMode(),
                        filterQuality = quality
                    )
                }
                if (i == doc.activeIndex && doc.bufferVisible) {
                    drawImage(
                        image = doc.strokeBuffer.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = srcSize,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        alpha = (doc.bufferAlpha * layer.opacity).coerceIn(0f, 1f),
                        filterQuality = quality
                    )
                }
            }
        }

        // Лупа пипетки над пальцем.
        editor.loupe?.let { p ->
            val c = Offset(p.x, p.y - 90.dp.toPx())
            drawCircle(Color.Black, radius = 42.dp.toPx(), center = c)
            drawCircle(editor.color, radius = 38.dp.toPx(), center = c)
            drawCircle(AvroraColors.Fluorite, radius = 42.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
