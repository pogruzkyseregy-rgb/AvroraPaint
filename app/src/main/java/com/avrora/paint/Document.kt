package com.avrora.paint

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Один слой — отдельная картинка размером с холст.
class Layer(val id: Long, name: String, var bitmap: Bitmap) {
    var name by mutableStateOf(name)
    var visible by mutableStateOf(true)
    var opacity by mutableFloatStateOf(1f)
    var blend by mutableStateOf(LayerBlend.NORMAL)

    // Защита прозрачности: рисовать можно только поверх уже нарисованного.
    var alphaLock by mutableStateOf(false)
}

// Запись истории. layerList — порядок слоёв (если менялся),
// pixels — копии картинок слоёв до изменения.
internal class HistoryEntry(
    val layerList: List<Layer>?,
    val active: Int,
    val pixels: List<Pair<Layer, Bitmap>>
)

internal enum class Op { NONE, STROKE, ERASE, SHAPE, GRADIENT }

class Document(val width: Int, val height: Int, val id: String = UUID.randomUUID().toString()) {

    val layers = mutableStateListOf<Layer>()
    var activeIndex by mutableIntStateOf(-1)
        private set

    // Счётчик изменений: холст и панели перерисовываются при его изменении.
    var revision by mutableIntStateOf(0)
        private set

    // Есть несохранённые изменения.
    var dirty = false

    // Временный буфер текущего мазка. Мазок рисуется сюда целиком,
    // а в слой переносится один раз в конце — так прозрачность ровная.
    val strokeBuffer: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    var bufferVisible by mutableStateOf(false)
        private set
    var bufferAlpha = 1f
        private set

    private var nextId = 1L
    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    private var op = Op.NONE
    private var opTool = Tool.PEN
    private var opPaint = Paint()
    private var opColor1 = 0
    private var opColor2 = 0
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var midX = 0f
    private var midY = 0f

    val active: Layer? get() = layers.getOrNull(activeIndex)
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val busy: Boolean get() = op != Op.NONE

    fun touch() {
        dirty = true
        revision++
    }

    private fun newBitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun copyOf(b: Bitmap): Bitmap = b.copy(Bitmap.Config.ARGB_8888, true)

    // ---------- История ----------

    private fun push(entry: HistoryEntry) {
        undoStack.addLast(entry)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun pushPixels(layer: Layer) {
        push(HistoryEntry(null, activeIndex, listOf(layer to copyOf(layer.bitmap))))
    }

    private fun pushStructure(vararg pixelLayers: Layer) {
        push(HistoryEntry(layers.toList(), activeIndex, pixelLayers.map { it to copyOf(it.bitmap) }))
    }

    // Применяет запись и возвращает обратную ей (для повтора/отмены).
    private fun applyEntry(e: HistoryEntry): HistoryEntry {
        val inverse = HistoryEntry(
            if (e.layerList != null) layers.toList() else null,
            activeIndex,
            e.pixels.map { (layer, _) -> layer to layer.bitmap }
        )
        e.layerList?.let {
            layers.clear()
            layers.addAll(it)
        }
        for ((layer, bmp) in e.pixels) layer.bitmap = bmp
        activeIndex = e.active.coerceIn(0, max(0, layers.lastIndex))
        return inverse
    }

    fun undo(): Boolean {
        if (busy) return false
        val e = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(applyEntry(e))
        touch()
        return true
    }

    fun redo(): Boolean {
        if (busy) return false
        val e = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(applyEntry(e))
        touch()
        return true
    }

    // ---------- Слои ----------

    fun addLayer(name: String? = null, record: Boolean = true): Layer {
        if (record) pushStructure()
        val layer = Layer(nextId++, name ?: "Слой ${layers.size + 1}", newBitmap())
        val index = if (activeIndex in layers.indices) activeIndex + 1 else layers.size
        layers.add(index, layer)
        activeIndex = index
        touch()
        return layer
    }

    // Используется при загрузке сохранённой работы.
    fun addLayerFromBitmap(name: String, bitmap: Bitmap): Layer {
        val bmp = if (bitmap.isMutable && bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            val out = newBitmap()
            Canvas(out).drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            out
        }
        val layer = Layer(nextId++, name, bmp)
        layers.add(layer)
        activeIndex = layers.lastIndex
        return layer
    }

    fun setActive(index: Int) {
        if (index in layers.indices) activeIndex = index
    }

    fun duplicateActive() {
        val src = active ?: return
        pushStructure()
        val copy = Layer(nextId++, "${src.name} копия", copyOf(src.bitmap))
        copy.visible = src.visible
        copy.opacity = src.opacity
        copy.blend = src.blend
        copy.alphaLock = src.alphaLock
        layers.add(activeIndex + 1, copy)
        activeIndex += 1
        touch()
    }

    fun removeActive() {
        val i = activeIndex
        if (i !in layers.indices || layers.size <= 1) return
        pushStructure()
        layers.removeAt(i)
        activeIndex = (i - 1).coerceAtLeast(0)
        touch()
    }

    fun moveActive(delta: Int) {
        val from = activeIndex
        val to = from + delta
        if (from !in layers.indices || to !in layers.indices) return
        pushStructure()
        val item = layers.removeAt(from)
        layers.add(to, item)
        activeIndex = to
        touch()
    }

    // Сливаем активный слой с тем, что под ним.
    fun mergeDown() {
        val i = activeIndex
        if (i <= 0 || i > layers.lastIndex) return
        val upper = layers[i]
        val lower = layers[i - 1]
        pushStructure(lower)
        if (upper.visible) {
            Canvas(lower.bitmap).drawBitmap(upper.bitmap, 0f, 0f, layerPaint(upper))
        }
        layers.removeAt(i)
        activeIndex = i - 1
        touch()
    }

    fun clearActive() {
        val layer = active ?: return
        pushPixels(layer)
        layer.bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        touch()
    }

    fun fillActive(color: Int) {
        val layer = active ?: return
        pushPixels(layer)
        val mode = if (layer.alphaLock) PorterDuff.Mode.SRC_ATOP else PorterDuff.Mode.SRC_OVER
        Canvas(layer.bitmap).drawColor(color, mode)
        touch()
    }

    // Вставляем фото новым слоем, вписав его в холст.
    fun importImage(src: Bitmap) {
        pushStructure()
        val layer = Layer(nextId++, "Фото", newBitmap())
        val s = min(width.toFloat() / src.width, height.toFloat() / src.height)
        val w = src.width * s
        val h = src.height * s
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        Canvas(layer.bitmap).drawBitmap(src, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG))
        val index = if (activeIndex in layers.indices) activeIndex + 1 else layers.size
        layers.add(index, layer)
        activeIndex = index
        touch()
    }

    // ---------- Мазки кистью и ластиком ----------

    fun beginStroke(tool: Tool, color: Int, size: Float, strength: Float, x: Float, y: Float) {
        val layer = active ?: return
        if (busy) cancelOp()
        pushPixels(layer)
        opTool = tool
        startX = x; startY = y
        lastX = x; lastY = y
        midX = x; midY = y
        if (tool == Tool.ERASER) {
            op = Op.ERASE
            opPaint = eraserPaint(size, strength)
            Canvas(layer.bitmap).drawPoint(x, y, opPaint)
        } else {
            op = Op.STROKE
            opPaint = brushPaint(tool, color, size)
            beginBuffer(strength)
            Canvas(strokeBuffer).drawPoint(x, y, opPaint)
        }
        touch()
    }

    // Сглаживаем линию кривыми через середины отрезков.
    fun strokeTo(x: Float, y: Float) {
        if (op != Op.STROKE && op != Op.ERASE) return
        val layer = active ?: return
        val mx = (lastX + x) / 2f
        val my = (lastY + y) / 2f
        val path = Path().apply {
            moveTo(midX, midY)
            quadTo(lastX, lastY, mx, my)
        }
        val target = if (op == Op.ERASE) layer.bitmap else strokeBuffer
        Canvas(target).drawPath(path, opPaint)
        midX = mx; midY = my
        lastX = x; lastY = y
        touch()
    }

    // ---------- Фигуры ----------

    fun beginShape(tool: Tool, color: Int, size: Float, strength: Float, fill: Boolean, x: Float, y: Float) {
        val layer = active ?: return
        if (busy) cancelOp()
        pushPixels(layer)
        op = Op.SHAPE
        opTool = tool
        opPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = size
            style = if (fill && tool != Tool.LINE) Paint.Style.FILL else Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        startX = x; startY = y
        lastX = x; lastY = y
        beginBuffer(strength)
        touch()
    }

    fun updateShape(x: Float, y: Float) {
        if (op != Op.SHAPE) return
        lastX = x; lastY = y
        strokeBuffer.eraseColor(android.graphics.Color.TRANSPARENT)
        val c = Canvas(strokeBuffer)
        val l = min(startX, x)
        val t = min(startY, y)
        val r = max(startX, x)
        val b = max(startY, y)
        when (opTool) {
            Tool.LINE -> c.drawLine(startX, startY, x, y, opPaint)
            Tool.RECT -> c.drawRect(l, t, r, b, opPaint)
            Tool.OVAL -> c.drawOval(RectF(l, t, r, b), opPaint)
            else -> Unit
        }
        touch()
    }

    // ---------- Градиент ----------

    fun beginGradient(color1: Int, color2: Int, strength: Float, x: Float, y: Float) {
        val layer = active ?: return
        if (busy) cancelOp()
        pushPixels(layer)
        op = Op.GRADIENT
        opColor1 = color1
        opColor2 = color2
        startX = x; startY = y
        beginBuffer(strength)
        updateGradient(x, y)
    }

    fun updateGradient(x: Float, y: Float) {
        if (op != Op.GRADIENT) return
        strokeBuffer.eraseColor(android.graphics.Color.TRANSPARENT)
        val c = Canvas(strokeBuffer)
        if (hypot(x - startX, y - startY) < 1f) {
            c.drawColor(opColor1)
        } else {
            val paint = Paint().apply {
                shader = LinearGradient(startX, startY, x, y, opColor1, opColor2, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        touch()
    }

    // ---------- Завершение и отмена действия ----------

    fun endOp() {
        val layer = active
        when (op) {
            Op.NONE -> return
            Op.STROKE -> if (layer != null) {
                if (hypot(lastX - midX, lastY - midY) > 0.5f) {
                    Canvas(strokeBuffer).drawLine(midX, midY, lastX, lastY, opPaint)
                }
                commitBuffer(layer)
            }
            Op.ERASE -> if (layer != null && hypot(lastX - midX, lastY - midY) > 0.5f) {
                Canvas(layer.bitmap).drawLine(midX, midY, lastX, lastY, opPaint)
            }
            Op.SHAPE, Op.GRADIENT -> if (layer != null) commitBuffer(layer)
        }
        op = Op.NONE
        bufferVisible = false
        touch()
    }

    // Отменяем начатое действие так, будто его не было.
    fun cancelOp() {
        if (op == Op.NONE) return
        undoStack.removeLastOrNull()?.let { applyEntry(it) }
        strokeBuffer.eraseColor(android.graphics.Color.TRANSPARENT)
        op = Op.NONE
        bufferVisible = false
        touch()
    }

    private fun beginBuffer(strength: Float) {
        strokeBuffer.eraseColor(android.graphics.Color.TRANSPARENT)
        bufferAlpha = strength.coerceIn(0f, 1f)
        bufferVisible = true
    }

    private fun commitBuffer(layer: Layer) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (bufferAlpha * 255).toInt()
            if (layer.alphaLock) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        Canvas(layer.bitmap).drawBitmap(strokeBuffer, 0f, 0f, paint)
        strokeBuffer.eraseColor(android.graphics.Color.TRANSPARENT)
    }

    // ---------- Заливка ----------

    // Заливаем область по тому, что видно на холсте (все слои),
    // а краску кладём в активный слой.
    fun floodFill(x: Float, y: Float, color: Int, strength: Float, tolerance: Int): Boolean {
        val layer = active ?: return false
        if (busy) return false
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until width || iy !in 0 until height) return false

        val w = width
        val h = height
        val composite = flatten(android.graphics.Color.WHITE)
        val pixels = IntArray(w * h)
        composite.getPixels(pixels, 0, w, 0, 0, w, h)
        composite.recycle()

        val target = pixels[iy * w + ix]
        val mask = BooleanArray(w * h)
        var stack = IntArray(4096)
        var sp = 0

        fun push(v: Int) {
            if (sp == stack.size) stack = stack.copyOf(sp * 2)
            stack[sp++] = v
        }

        fun matches(i: Int): Boolean = !mask[i] && similar(pixels[i], target, tolerance)

        push(iy * w + ix)
        while (sp > 0) {
            val idx = stack[--sp]
            if (!matches(idx)) continue
            val row = idx / w
            val base = row * w
            val cx = idx - base
            var lx = cx
            while (lx > 0 && matches(base + lx - 1)) lx--
            var rx = cx
            while (rx < w - 1 && matches(base + rx + 1)) rx++
            for (i in lx..rx) mask[base + i] = true
            for (ny in intArrayOf(row - 1, row + 1)) {
                if (ny < 0 || ny >= h) continue
                val nb = ny * w
                var inRun = false
                for (i in lx..rx) {
                    val ok = matches(nb + i)
                    if (ok && !inRun) {
                        push(nb + i)
                        inRun = true
                    } else if (!ok) {
                        inRun = false
                    }
                }
            }
        }

        // Расширяем область на 1 пиксель, чтобы не было светлой каймы у линий.
        val out = IntArray(w * h)
        var any = false
        for (yy in 0 until h) {
            val rowBase = yy * w
            for (xx in 0 until w) {
                val i = rowBase + xx
                val m = mask[i] ||
                    (xx > 0 && mask[i - 1]) ||
                    (xx < w - 1 && mask[i + 1]) ||
                    (yy > 0 && mask[i - w]) ||
                    (yy < h - 1 && mask[i + w])
                if (m) {
                    out[i] = color
                    any = true
                }
            }
        }
        if (!any) return false

        pushPixels(layer)
        beginBuffer(strength)
        strokeBuffer.setPixels(out, 0, w, 0, 0, w, h)
        commitBuffer(layer)
        bufferVisible = false
        touch()
        return true
    }

    // ---------- Пипетка ----------

    // Цвет точки так, как он виден на холсте (поверх белой бумаги).
    fun sampleColor(x: Float, y: Float): Int? {
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until width || iy !in 0 until height) return null
        var r = 255f
        var g = 255f
        var b = 255f
        for (layer in layers) {
            if (!layer.visible) continue
            val p = layer.bitmap.getPixel(ix, iy)
            val a = ((p ushr 24) / 255f) * layer.opacity
            if (a <= 0f) continue
            r = r * (1 - a) + ((p shr 16) and 0xFF) * a
            g = g * (1 - a) + ((p shr 8) and 0xFF) * a
            b = b * (1 - a) + (p and 0xFF) * a
        }
        return android.graphics.Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    // ---------- Сборка картинки ----------

    private fun layerPaint(layer: Layer): Paint {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (layer.opacity * 255).toInt()
        if (blendModesSupported) {
            layer.blend.androidMode()?.let { paint.blendMode = it }
        }
        return paint
    }

    // Склеиваем видимые слои в одну картинку.
    fun flatten(background: Int? = null): Bitmap {
        val out = newBitmap()
        if (background != null) out.eraseColor(background)
        val canvas = Canvas(out)
        for (layer in layers) {
            if (!layer.visible) continue
            canvas.drawBitmap(layer.bitmap, 0f, 0f, layerPaint(layer))
        }
        return out
    }

    fun thumbnail(maxSide: Int): Bitmap {
        val full = flatten(android.graphics.Color.WHITE)
        val s = maxSide / max(width, height).toFloat()
        val thumb = Bitmap.createScaledBitmap(
            full,
            max(1, (width * s).toInt()),
            max(1, (height * s).toInt()),
            true
        )
        if (thumb !== full) full.recycle()
        return thumb
    }

    private fun brushPaint(tool: Tool, color: Int, size: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
            when (tool) {
                Tool.SOFT -> maskFilter = BlurMaskFilter(max(1f, size * 0.45f), BlurMaskFilter.Blur.NORMAL)
                Tool.AIRBRUSH -> {
                    maskFilter = BlurMaskFilter(max(1f, size * 0.9f), BlurMaskFilter.Blur.NORMAL)
                    alpha = 40
                }
                Tool.MARKER -> {
                    strokeCap = Paint.Cap.SQUARE
                    strokeJoin = Paint.Join.BEVEL
                }
                else -> Unit
            }
        }

    private fun eraserPaint(size: Float, strength: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.BLACK
            alpha = (strength.coerceIn(0f, 1f) * 255).toInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }

    private companion object {
        const val MAX_HISTORY = 15
    }
}

private fun similar(a: Int, b: Int, tol: Int): Boolean =
    abs((a ushr 24) - (b ushr 24)) <= tol &&
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tol &&
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tol &&
        abs((a and 0xFF) - (b and 0xFF)) <= tol
