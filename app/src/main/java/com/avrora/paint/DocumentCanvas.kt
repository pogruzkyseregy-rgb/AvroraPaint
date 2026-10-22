package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
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
    modifier: Modifier = Modifier,
    onTouch: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val touchCallback by rememberUpdatedState(onTouch)
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
                    touchCallback()

                    // Трансформация: один палец двигает, два — масштабируют и вращают.
                    if (doc.floating != null) {
                        down.consume()
                        var prev: List<Offset> = listOf(view.toDoc(down.position))
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                ev.changes.forEach { it.consume() }
                                break
                            }
                            val pts = pressed.take(2).map { view.toDoc(it.position) }
                            if (pts.size == prev.size) {
                                val m = android.graphics.Matrix()
                                if (pts.size == 1) {
                                    m.setTranslate(pts[0].x - prev[0].x, pts[0].y - prev[0].y)
                                } else {
                                    val d0 = prev[1] - prev[0]
                                    val d1 = pts[1] - pts[0]
                                    val len0 = d0.getDistance()
                                    if (len0 > 1f) {
                                        val k = d1.getDistance() / len0
                                        val deg = Math.toDegrees(
                                            (atan2(d1.y, d1.x) - atan2(d0.y, d0.x)).toDouble()
                                        ).toFloat()
                                        val c0 = (prev[0] + prev[1]) / 2f
                                        val c1 = (pts[0] + pts[1]) / 2f
                                        m.setTranslate(-c0.x, -c0.y)
                                        m.postScale(k, k)
                                        m.postRotate(deg)
                                        m.postTranslate(c1.x, c1.y)
                                    }
                                }
                                doc.transformBy(m)
                            }
                            prev = pts
                            ev.changes.forEach { it.consume() }
                        }
                        return@awaitEachGesture
                    }
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                    if (quick && maxPointers == 2) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        editor.flash(if (doc.undo()) "↶  Отмена" else "Нечего отменять")
                                    }
                                    if (quick && maxPointers >= 3) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        editor.flash(if (doc.redo()) "↷  Повтор" else "Нечего повторять")
                                    }
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
                                        if (abs(dist - prevDist) > 0.5f) {
                                            editor.flash("${(view.scale / view.fitScale * 100).roundToInt()}%")
                                        }
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

        // Мягкое неоновое свечение вокруг листа.
        val glow = 14.dp.toPx()
        for (k in 1..4) {
            val g = glow * k / 4f
            drawRoundRect(
                AvroraColors.Violet.copy(alpha = 0.07f),
                topLeft = topLeft - Offset(g, g),
                size = Size(w + 2 * g, h + 2 * g),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(g, g)
            )
        }
        drawRect(
            AvroraColors.Fluorite.copy(alpha = 0.35f),
            topLeft = topLeft - Offset(1f, 1f),
            size = Size(w + 2f, h + 2f)
        )

        // Точка холста -> точка экрана (отражение делает блок scale ниже).
        fun screen(o: Offset) = Offset(view.left + o.x * view.scale, view.top + o.y * view.scale)
        fun polygon(points: List<Offset>, close: Boolean): Path {
            val path = Path()
            points.forEachIndexed { idx, o ->
                val sp = screen(o)
                if (idx == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
            }
            if (close) path.close()
            return path
        }

        val selection = doc.selection
        val selectionScreen = selection?.let { polygon(it, true) }
        val filterMatrix = if (editor.filterPreview && !editor.filter.isIdentity) {
            ColorFilter.colorMatrix(editor.filter.toComposeMatrix())
        } else {
            null
        }

        scale(scaleX = if (view.flipped) -1f else 1f, scaleY = 1f, pivot = pivot) {
            drawRect(Color.White, topLeft = topLeft, size = Size(w, h))
            doc.layers.forEachIndexed { i, layer ->
                val isActive = i == doc.activeIndex
                if (layer.visible) {
                    val drawLayer = { cf: ColorFilter? ->
                        drawImage(
                            image = layer.bitmap.asImageBitmap(),
                            srcOffset = IntOffset.Zero,
                            srcSize = srcSize,
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            alpha = layer.opacity,
                            colorFilter = cf,
                            blendMode = layer.blend.composeMode(),
                            filterQuality = quality
                        )
                    }
                    if (isActive && filterMatrix != null && selectionScreen != null) {
                        // Фильтр виден только внутри выделения — как и при применении.
                        val inside = if (doc.selectionInverted) ClipOp.Difference else ClipOp.Intersect
                        val outside = if (doc.selectionInverted) ClipOp.Intersect else ClipOp.Difference
                        clipPath(selectionScreen, clipOp = outside) { drawLayer(null) }
                        clipPath(selectionScreen, clipOp = inside) { drawLayer(filterMatrix) }
                    } else {
                        drawLayer(if (isActive) filterMatrix else null)
                    }
                }
                if (isActive && doc.bufferVisible) {
                    val drawBuffer = {
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
                    if (selectionScreen != null) {
                        clipPath(
                            selectionScreen,
                            clipOp = if (doc.selectionInverted) ClipOp.Difference else ClipOp.Intersect
                        ) { drawBuffer() }
                    } else {
                        drawBuffer()
                    }
                }
                val f = doc.floating
                if (f != null && f.layer === layer) {
                    val m = android.graphics.Matrix(f.matrix)
                    m.postScale(view.scale, view.scale)
                    m.postTranslate(view.left, view.top)
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = (layer.opacity * 255).toInt()
                    }
                    drawIntoCanvas { it.nativeCanvas.drawBitmap(f.bitmap, m, paint) }

                    // Рамка с уголками вокруг куска.
                    val pts = floatArrayOf(
                        0f, 0f, f.bitmap.width.toFloat(), 0f,
                        f.bitmap.width.toFloat(), f.bitmap.height.toFloat(), 0f, f.bitmap.height.toFloat()
                    )
                    m.mapPoints(pts)
                    val frame = Path().apply {
                        moveTo(pts[0], pts[1])
                        lineTo(pts[2], pts[3])
                        lineTo(pts[4], pts[5])
                        lineTo(pts[6], pts[7])
                        close()
                    }
                    drawPath(frame, Color.Black.copy(alpha = 0.6f), style = Stroke(width = 3.dp.toPx()))
                    drawPath(frame, AvroraColors.Fluorite, style = Stroke(width = 1.5.dp.toPx()))
                    for (k in 0 until 4) {
                        val c = Offset(pts[k * 2], pts[k * 2 + 1])
                        drawCircle(Color.White, radius = 6.dp.toPx(), center = c)
                        drawCircle(AvroraColors.Violet, radius = 6.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }

            // Контур выделения: белая линия и чёрный пунктир поверх.
            val outline = selectionScreen ?: doc.selectionDraft.takeIf { it.size >= 2 }?.let {
                polygon(it, editor.tool == Tool.SELECT_RECT)
            }
            if (outline != null) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                drawPath(outline, Color.White, style = Stroke(width = 1.5.dp.toPx()))
                drawPath(outline, Color.Black, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
                if (selectionScreen != null && doc.selectionInverted) {
                    drawRect(Color.White, topLeft = topLeft, size = Size(w, h), style = Stroke(width = 1.5.dp.toPx()))
                    drawRect(
                        Color.Black,
                        topLeft = topLeft,
                        size = Size(w, h),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
                    )
                }
            }
        }

        // Кружок размера кисти, пока двигают ползунок.
        if (editor.sizePreview) {
            val r = (editor.size * view.scale / 2f).coerceAtLeast(2f)
            drawCircle(editor.color.copy(alpha = 0.35f), radius = r, center = center)
            drawCircle(Color.White, radius = r, center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.Black, radius = r + 2.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
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
