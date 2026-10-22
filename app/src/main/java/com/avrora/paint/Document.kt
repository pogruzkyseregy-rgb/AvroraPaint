package com.avrora.paint

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
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
import androidx.compose.ui.geometry.Offset
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
// Кусок картинки, который двигают, масштабируют и вращают.
class FloatingPiece(val bitmap: Bitmap, val layer: Layer, val matrix: Matrix)

internal class HistoryEntry(
    val layerList: List<Layer>?,
    val active: Int,
    val pixels: List<Pair<Layer, Bitmap>>
)

internal enum class Op { NONE, STROKE, ERASE, SHAPE, GRADIENT, TRANSFORM }

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

    // Меняется только когда завершено действие или изменилась история.
    // Кнопки и панели следят за ним, а не за revision, чтобы не
    // перерисовываться на каждое движение пальца.
    var historyVersion by mutableIntStateOf(0)
        private set

    fun touch() {
        dirty = true
        revision++
    }

    private fun settled() {
        historyVersion++
    }

    private fun newBitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun copyOf(b: Bitmap): Bitmap = b.copy(Bitmap.Config.ARGB_8888, true)

    // ---------- История ----------

    private fun push(entry: HistoryEntry) {
        undoStack.addLast(entry)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        settled()
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
        settled()
        return true
    }

    fun redo(): Boolean {
        if (busy) return false
        val e = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(applyEntry(e))
        touch()
        settled()
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
        layerCanvas(layer).drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        touch()
        settled()
    }

    fun fillActive(color: Int) {
        val layer = active ?: return
        pushPixels(layer)
        val mode = if (layer.alphaLock) PorterDuff.Mode.SRC_ATOP else PorterDuff.Mode.SRC_OVER
        layerCanvas(layer).drawColor(color, mode)
        touch()
        settled()
    }

    // Вставляем фото новым слоем, вписав в холст, и сразу даём его подвинуть.
    fun importImage(src: Bitmap) {
        val s = min(width.toFloat() / src.width, height.toFloat() / src.height)
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate((width - src.width * s) / 2f, (height - src.height * s) / 2f)
        beginPaste(src.copy(Bitmap.Config.ARGB_8888, false), m, "Фото")
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
            layerCanvas(layer).drawPoint(x, y, opPaint)
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
        val canvas = if (op == Op.ERASE) layerCanvas(layer) else Canvas(strokeBuffer)
        canvas.drawPath(path, opPaint)
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
            Op.NONE, Op.TRANSFORM -> return
            Op.STROKE -> if (layer != null) {
                if (hypot(lastX - midX, lastY - midY) > 0.5f) {
                    Canvas(strokeBuffer).drawLine(midX, midY, lastX, lastY, opPaint)
                }
                commitBuffer(layer)
            }
            Op.ERASE -> if (layer != null && hypot(lastX - midX, lastY - midY) > 0.5f) {
                layerCanvas(layer).drawLine(midX, midY, lastX, lastY, opPaint)
            }
            Op.SHAPE, Op.GRADIENT -> if (layer != null) commitBuffer(layer)
        }
        op = Op.NONE
        bufferVisible = false
        touch()
        settled()
    }

    // Отменяем начатое действие так, будто его не было.
    fun cancelOp() {
        if (op == Op.NONE) return
        if (op == Op.TRANSFORM) {
            cancelTransform()
            return
        }
        undoStack.removeLastOrNull()?.let { applyEntry(it) }
        strokeBuffer.eraseColor(android.graphics.Color.TRANSPARENT)
        op = Op.NONE
        bufferVisible = false
        touch()
        settled()
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
        layerCanvas(layer).drawBitmap(strokeBuffer, 0f, 0f, paint)
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
        settled()
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

    // ---------- Выделение ----------

    // Контур выделения в координатах холста. null — выделения нет.
    var selection by mutableStateOf<List<Offset>?>(null)
        private set
    var selectionInverted by mutableStateOf(false)
        private set

    // Контур, который рисуют прямо сейчас (рамка или лассо).
    var selectionDraft by mutableStateOf<List<Offset>>(emptyList())
        private set

    val hasSelection: Boolean get() = selection != null

    fun setDraft(points: List<Offset>) {
        selectionDraft = points
        touch()
    }

    fun addDraftPoint(p: Offset) {
        val last = selectionDraft.lastOrNull()
        if (last == null || (p - last).getDistance() > 2f) {
            selectionDraft = selectionDraft + p
            touch()
        }
    }

    fun commitDraft() {
        val pts = selectionDraft
        selectionDraft = emptyList()
        if (pts.size >= 3 && polygonArea(pts) > 16f) {
            selection = pts
            selectionInverted = false
        } else {
            selection = null
        }
        touch()
        settled()
    }

    fun clearDraft() {
        selectionDraft = emptyList()
        touch()
    }

    fun selectAll() {
        selection = listOf(
            Offset(0f, 0f), Offset(width.toFloat(), 0f),
            Offset(width.toFloat(), height.toFloat()), Offset(0f, height.toFloat())
        )
        selectionInverted = false
        touch()
        settled()
    }

    fun invertSelection() {
        if (selection == null) return
        selectionInverted = !selectionInverted
        touch()
        settled()
    }

    fun clearSelection() {
        selection = null
        selectionInverted = false
        selectionDraft = emptyList()
        touch()
        settled()
    }

    private fun selectionPath(): Path? {
        val pts = selection ?: return null
        if (pts.size < 3) return null
        val p = Path()
        p.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
        p.close()
        p.fillType = if (selectionInverted) Path.FillType.INVERSE_WINDING else Path.FillType.WINDING
        return p
    }

    // Холст слоя, ограниченный выделением.
    private fun layerCanvas(layer: Layer): Canvas {
        val c = Canvas(layer.bitmap)
        selectionPath()?.let { c.clipPath(it) }
        return c
    }

    // Прямоугольник выделения в целых пикселях, обрезанный по холсту.
    private fun selectionBounds(): android.graphics.Rect? {
        val path = selectionPath() ?: return android.graphics.Rect(0, 0, width, height)
        if (selectionInverted) return android.graphics.Rect(0, 0, width, height)
        val r = RectF()
        path.computeBounds(r, true)
        val out = android.graphics.Rect(
            kotlin.math.floor(r.left).toInt().coerceIn(0, width),
            kotlin.math.floor(r.top).toInt().coerceIn(0, height),
            kotlin.math.ceil(r.right).toInt().coerceIn(0, width),
            kotlin.math.ceil(r.bottom).toInt().coerceIn(0, height)
        )
        return if (out.width() > 0 && out.height() > 0) out else null
    }

    // Копия выделенного (или всего слоя) и её левый верхний угол на холсте.
    fun copySelection(): Pair<Bitmap, Offset>? {
        val layer = active ?: return null
        if (busy) return null
        val b = selectionBounds() ?: return null
        val out = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.translate(-b.left.toFloat(), -b.top.toFloat())
        selectionPath()?.let { c.clipPath(it) }
        c.drawBitmap(layer.bitmap, 0f, 0f, null)
        return out to Offset(b.left.toFloat(), b.top.toFloat())
    }

    fun cutSelection(): Pair<Bitmap, Offset>? {
        val copied = copySelection() ?: return null
        clearActive()
        return copied
    }

    // ---------- Трансформация ----------

    var floating by mutableStateOf<FloatingPiece?>(null)
        private set

    // true — кусок пришёл из буфера или фото: отмена убирает и новый слой.
    private var floatingIsPaste = false
    private var savedSelection: List<Offset>? = null
    private var savedInverted = false
    private var pieceOrigin = Offset(0f, 0f)

    // Поднимаем выделенное (или весь слой) в плавающий кусок.
    fun beginTransform(): Boolean {
        val layer = active ?: return false
        if (busy) return false
        val b = selectionBounds() ?: return false
        pushPixels(layer)
        val piece = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888)
        val c = Canvas(piece)
        c.translate(-b.left.toFloat(), -b.top.toFloat())
        selectionPath()?.let { c.clipPath(it) }
        c.drawBitmap(layer.bitmap, 0f, 0f, null)
        layerCanvas(layer).drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val m = Matrix()
        m.setTranslate(b.left.toFloat(), b.top.toFloat())
        pieceOrigin = Offset(b.left.toFloat(), b.top.toFloat())
        savedSelection = selection
        savedInverted = selectionInverted
        selection = null
        floatingIsPaste = false
        floating = FloatingPiece(piece, layer, m)
        op = Op.TRANSFORM
        touch()
        settled()
        return true
    }

    // Вставка картинки новым слоем сразу в режиме трансформации.
    fun beginPaste(bitmap: Bitmap, placement: Matrix, name: String) {
        if (busy) return
        pushStructure()
        val layer = Layer(nextId++, name, newBitmap())
        val index = if (activeIndex in layers.indices) activeIndex + 1 else layers.size
        layers.add(index, layer)
        activeIndex = index
        savedSelection = null
        savedInverted = false
        selection = null
        floatingIsPaste = true
        floating = FloatingPiece(bitmap, layer, Matrix(placement))
        op = Op.TRANSFORM
        touch()
        settled()
    }

    fun transformBy(m: Matrix) {
        val f = floating ?: return
        f.matrix.postConcat(m)
        touch()
    }

    // Центр куска на холсте — вокруг него крутим и отражаем.
    fun floatingCenter(): Offset? {
        val f = floating ?: return null
        val pts = floatArrayOf(f.bitmap.width / 2f, f.bitmap.height / 2f)
        f.matrix.mapPoints(pts)
        return Offset(pts[0], pts[1])
    }

    fun rotateFloating(degrees: Float) {
        val c = floatingCenter() ?: return
        transformBy(Matrix().apply { setRotate(degrees, c.x, c.y) })
    }

    fun flipFloating(horizontal: Boolean) {
        val c = floatingCenter() ?: return
        transformBy(Matrix().apply {
            if (horizontal) setScale(-1f, 1f, c.x, c.y) else setScale(1f, -1f, c.x, c.y)
        })
    }

    fun applyTransform() {
        val f = floating ?: return
        Canvas(f.layer.bitmap).drawBitmap(f.bitmap, f.matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        // Выделение едет вместе с куском: точки холста -> точки куска -> новое место.
        val sel = savedSelection
        if (sel != null && !savedInverted) {
            val arr = FloatArray(sel.size * 2)
            sel.forEachIndexed { i, o ->
                arr[i * 2] = o.x - pieceOrigin.x
                arr[i * 2 + 1] = o.y - pieceOrigin.y
            }
            f.matrix.mapPoints(arr)
            selection = sel.indices.map { Offset(arr[it * 2], arr[it * 2 + 1]) }
            selectionInverted = false
        }
        floating = null
        op = Op.NONE
        touch()
        settled()
    }

    fun cancelTransform() {
        if (floating == null) return
        floating = null
        op = Op.NONE
        undoStack.removeLastOrNull()?.let { applyEntry(it) }
        selection = savedSelection
        selectionInverted = savedInverted
        touch()
        settled()
    }

    // ---------- Фильтры ----------

    // Цветовая коррекция активного слоя (внутри выделения).
    fun applyColorMatrix(cm: ColorMatrix) {
        val layer = active ?: return
        if (busy) return
        pushPixels(layer)
        val src = copyOf(layer.bitmap)
        val c = layerCanvas(layer)
        c.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        c.drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        src.recycle()
        touch()
        settled()
    }

    // Размытие: уменьшаем и снова увеличиваем картинку со сглаживанием.
    fun blurActive(radius: Float) {
        val layer = active ?: return
        if (busy || radius < 2f) return
        pushPixels(layer)
        val src = layer.bitmap
        var cur: Bitmap = Bitmap.createScaledBitmap(src, max(1, width / 2), max(1, height / 2), true)
        var f = 2
        while (f * 2 <= radius && f < 64) {
            val next = Bitmap.createScaledBitmap(cur, max(1, cur.width / 2), max(1, cur.height / 2), true)
            cur.recycle()
            cur = next
            f *= 2
        }
        while (cur.width * 2 <= width && cur.height * 2 <= height) {
            val next = Bitmap.createScaledBitmap(cur, cur.width * 2, cur.height * 2, true)
            cur.recycle()
            cur = next
        }
        val blurred = Bitmap.createScaledBitmap(cur, width, height, true)
        if (blurred !== cur) cur.recycle()
        val c = layerCanvas(layer)
        c.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        c.drawBitmap(blurred, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()
        touch()
        settled()
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

private fun polygonArea(pts: List<Offset>): Float {
    var a = 0f
    for (i in pts.indices) {
        val p = pts[i]
        val q = pts[(i + 1) % pts.size]
        a += p.x * q.y - q.x * p.y
    }
    return abs(a) / 2f
}
