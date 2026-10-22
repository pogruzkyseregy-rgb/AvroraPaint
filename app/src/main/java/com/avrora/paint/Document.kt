package com.avrora.paint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
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
import kotlin.math.ceil
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

// Кусок картинки, который двигают, масштабируют и вращают.
// mask — выделение, которое поедет вместе с куском.
class FloatingPiece(val bitmap: Bitmap, val mask: Bitmap?, val layer: Layer, val matrix: Matrix)

// Снимок выделения для истории (mask == null — выделения не было).
internal class SelSnapshot(val mask: Bitmap?)

// Запись истории. layerList — порядок слоёв (если менялся),
// pixels — картинки слоёв до изменения, sel — выделение до изменения.
internal class HistoryEntry(
    val layerList: List<Layer>?,
    val active: Int,
    val pixels: List<Pair<Layer, Bitmap>>,
    val sel: SelSnapshot? = null
)

internal enum class Op { NONE, STROKE, ERASE, SHAPE, GRADIENT, TRANSFORM }

private val WHITE = android.graphics.Color.WHITE
private val CLEAR = android.graphics.Color.TRANSPARENT

class Document(val width: Int, val height: Int, val id: String = UUID.randomUUID().toString()) {

    val layers = mutableStateListOf<Layer>()
    var activeIndex by mutableIntStateOf(-1)
        private set

    // Счётчик изменений: холст перерисовывается при его изменении.
    var revision by mutableIntStateOf(0)
        private set

    // Меняется только когда завершено действие или изменилась история.
    // Кнопки и панели следят за ним, а не за revision.
    var historyVersion by mutableIntStateOf(0)
        private set

    // Есть несохранённые изменения.
    var dirty = false

    // Временный буфер текущего мазка: мазок копится здесь,
    // а в слой переносится один раз в конце — так прозрачность ровная.
    val strokeBuffer: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    var bufferVisible by mutableStateOf(false)
        private set
    var bufferAlpha = 1f
        private set
    val bufferIsEraser: Boolean get() = op == Op.ERASE

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

    // Кисти-«штампы» (мягкая, аэрограф): отпечатки с ровным шагом.
    private var dabTip: Bitmap? = null
    private var dabPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var dabSpacing = 1f
    private var dabCarry = 0f

    val active: Layer? get() = layers.getOrNull(activeIndex)
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val busy: Boolean get() = op != Op.NONE

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

    private fun pushSelection() {
        push(HistoryEntry(null, activeIndex, emptyList(), SelSnapshot(selectionMask?.let { copyOf(it) })))
    }

    // Применяет запись и возвращает обратную ей (для повтора/отмены).
    private fun applyEntry(e: HistoryEntry): HistoryEntry {
        val inverse = HistoryEntry(
            if (e.layerList != null) layers.toList() else null,
            activeIndex,
            e.pixels.map { (layer, _) -> layer to layer.bitmap },
            if (e.sel != null) SelSnapshot(selectionMask) else null
        )
        e.layerList?.let {
            layers.clear()
            layers.addAll(it)
        }
        for ((layer, bmp) in e.pixels) layer.bitmap = bmp
        activeIndex = e.active.coerceIn(0, max(0, layers.lastIndex))
        if (e.sel != null) {
            selectionMask = e.sel.mask
            refreshSelection()
        }
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
        if (busy) return
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
        if (i !in layers.indices || layers.size <= 1 || busy) return
        pushStructure()
        layers.removeAt(i)
        activeIndex = (i - 1).coerceAtLeast(0)
        touch()
    }

    fun moveActive(delta: Int) {
        val from = activeIndex
        val to = from + delta
        if (from !in layers.indices || to !in layers.indices || busy) return
        pushStructure()
        val item = layers.removeAt(from)
        layers.add(to, item)
        activeIndex = to
        touch()
    }

    // Сливаем активный слой с тем, что под ним.
    fun mergeDown() {
        val i = activeIndex
        if (i <= 0 || i > layers.lastIndex || busy) return
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

    // Стереть содержимое (внутри выделения, если оно есть).
    fun clearActive() {
        val layer = active ?: return
        if (busy) return
        pushPixels(layer)
        val mask = selectionMask
        if (mask != null) {
            Canvas(layer.bitmap).drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        } else {
            layer.bitmap.eraseColor(CLEAR)
        }
        touch()
        settled()
    }

    // Залить цветом (внутри выделения, если оно есть).
    fun fillActive(color: Int) {
        val layer = active ?: return
        if (busy) return
        pushPixels(layer)
        val fill = newBitmap()
        fill.eraseColor(color)
        selectionMask?.let { maskInto(fill, it) }
        Canvas(layer.bitmap).drawBitmap(fill, 0f, 0f, Paint().apply {
            if (layer.alphaLock) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        })
        fill.recycle()
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

    // ---------- Маска выделения: общие помощники ----------

    // Оставить в bitmap только то, что внутри маски.
    private fun maskInto(bitmap: Bitmap, mask: Bitmap, dx: Float = 0f, dy: Float = 0f) {
        Canvas(bitmap).drawBitmap(mask, dx, dy, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
    }

    // Заменить содержимое слоя на content внутри выделения (или целиком).
    // content портится и после вызова не нужен.
    private fun replaceMasked(layer: Layer, content: Bitmap) {
        val mask = selectionMask
        if (mask == null) {
            val c = Canvas(layer.bitmap)
            c.drawColor(CLEAR, PorterDuff.Mode.CLEAR)
            c.drawBitmap(content, 0f, 0f, null)
            return
        }
        maskInto(content, mask)
        val c = Canvas(layer.bitmap)
        c.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        c.drawBitmap(content, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })
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
        dabTip?.recycle()
        dabTip = null
        op = if (tool == Tool.ERASER) Op.ERASE else Op.STROKE
        beginBuffer(strength)

        if (tool == Tool.SOFT || tool == Tool.AIRBRUSH) {
            // Мягкие кисти: круглые отпечатки с мягким краем и ровным шагом —
            // так нет полос и «бусин» на линии.
            dabTip = makeTip(color, size)
            dabSpacing = max(0.5f, size * 0.08f)
            // Сколько отпечатков ложится в одну точку: под это подбираем прозрачность.
            val overlap = (size / dabSpacing).coerceAtLeast(1f)
            val target = if (tool == Tool.AIRBRUSH) 0.25f else 0.95f
            val a = 1f - Math.pow((1f - target).toDouble(), 1.0 / overlap).toFloat()
            dabPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (a * 255).toInt().coerceIn(2, 255) }
            dabCarry = 0f
            stamp(x, y)
        } else {
            opPaint = if (tool == Tool.ERASER) eraserPaint(size) else brushPaint(tool, color, size)
            Canvas(strokeBuffer).drawPoint(x, y, opPaint)
        }
        touch()
    }

    fun strokeTo(x: Float, y: Float) {
        if (op != Op.STROKE && op != Op.ERASE) return
        if (dabTip != null) {
            dabLine(lastX, lastY, x, y)
            lastX = x; lastY = y
            touch()
            return
        }
        // Сглаживаем линию кривыми через середины отрезков.
        val mx = (lastX + x) / 2f
        val my = (lastY + y) / 2f
        val path = Path().apply {
            moveTo(midX, midY)
            quadTo(lastX, lastY, mx, my)
        }
        Canvas(strokeBuffer).drawPath(path, opPaint)
        midX = mx; midY = my
        lastX = x; lastY = y
        touch()
    }

    // Отпечатки вдоль отрезка с постоянным шагом.
    private fun dabLine(x0: Float, y0: Float, x1: Float, y1: Float) {
        val dist = hypot(x1 - x0, y1 - y0)
        if (dist <= 0f) return
        var t = dabSpacing - dabCarry
        while (t <= dist) {
            val k = t / dist
            stamp(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k)
            t += dabSpacing
        }
        dabCarry = dist - (t - dabSpacing)
    }

    private fun stamp(x: Float, y: Float) {
        val tip = dabTip ?: return
        Canvas(strokeBuffer).drawBitmap(tip, x - tip.width / 2f, y - tip.height / 2f, dabPaint)
    }

    // Круглый отпечаток: плотный центр, плавно тающий край.
    private fun makeTip(color: Int, size: Float): Bitmap {
        val d = max(2, ceil(size).toInt())
        val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val r = d / 2f
        val rgb = color and 0xFFFFFF
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                r, r, r,
                intArrayOf((0xFF shl 24) or rgb, (0x8C shl 24) or rgb, (0x30 shl 24) or rgb, rgb),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        Canvas(bmp).drawCircle(r, r, r, paint)
        return bmp
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
        strokeBuffer.eraseColor(CLEAR)
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
        strokeBuffer.eraseColor(CLEAR)
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
            Op.STROKE, Op.ERASE -> if (layer != null) {
                if (dabTip == null && hypot(lastX - midX, lastY - midY) > 0.5f) {
                    Canvas(strokeBuffer).drawLine(midX, midY, lastX, lastY, opPaint)
                }
                if (op == Op.ERASE) commitErase(layer) else commitBuffer(layer)
            }
            Op.SHAPE, Op.GRADIENT -> if (layer != null) commitBuffer(layer)
        }
        dabTip?.recycle()
        dabTip = null
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
        strokeBuffer.eraseColor(CLEAR)
        dabTip?.recycle()
        dabTip = null
        op = Op.NONE
        bufferVisible = false
        touch()
        settled()
    }

    private fun beginBuffer(strength: Float) {
        strokeBuffer.eraseColor(CLEAR)
        bufferAlpha = strength.coerceIn(0f, 1f)
        bufferVisible = true
    }

    private fun commitBuffer(layer: Layer) {
        selectionMask?.let { maskInto(strokeBuffer, it) }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (bufferAlpha * 255).toInt()
            if (layer.alphaLock) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        Canvas(layer.bitmap).drawBitmap(strokeBuffer, 0f, 0f, paint)
        strokeBuffer.eraseColor(CLEAR)
    }

    // Ластик: весь мазок стирается разом — прозрачность ровная, без полос.
    private fun commitErase(layer: Layer) {
        selectionMask?.let { maskInto(strokeBuffer, it) }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (bufferAlpha * 255).toInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        Canvas(layer.bitmap).drawBitmap(strokeBuffer, 0f, 0f, paint)
        strokeBuffer.eraseColor(CLEAR)
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
        val composite = flatten(WHITE)
        val pixels = IntArray(w * h)
        composite.getPixels(pixels, 0, w, 0, 0, w, h)
        composite.recycle()

        val region = regionMask(pixels, w, h, ix, iy, tolerance, contiguous = true)

        // Расширяем область на 1 пиксель, чтобы не было светлой каймы у линий.
        val out = IntArray(w * h)
        var any = false
        for (yy in 0 until h) {
            val rowBase = yy * w
            for (xx in 0 until w) {
                val i = rowBase + xx
                val m = region[i] ||
                    (xx > 0 && region[i - 1]) ||
                    (xx < w - 1 && region[i + 1]) ||
                    (yy > 0 && region[i - w]) ||
                    (yy < h - 1 && region[i + w])
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

    // ---------- Выделение ----------

    // Маска выделения: белые пиксели выделены, прозрачные — нет.
    // Мягкие края (полупрозрачные пиксели) тоже работают.
    var selectionMask: Bitmap? = null
        private set
    var hasSelection by mutableStateOf(false)
        private set

    // Меняется при каждом изменении выделения — по нему перестраивается контур.
    var selectionVersion by mutableIntStateOf(0)
        private set

    // Контур выделения: отрезки x1, y1, x2, y2 в координатах холста.
    var selectionOutline: FloatArray = FloatArray(0)
        private set
    private var selBounds: Rect? = null

    // Контур, который рисуют прямо сейчас (рамка или лассо).
    var selectionDraft by mutableStateOf<List<Offset>>(emptyList())
        private set

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

    fun clearDraft() {
        selectionDraft = emptyList()
        touch()
    }

    // Нарисованная рамка/лассо добавляется к выделению, вычитается или заменяет его.
    fun commitDraft(mode: SelectMode) {
        val pts = selectionDraft
        selectionDraft = emptyList()
        if (pts.size < 3 || polygonArea(pts) <= 16f) {
            touch()
            return
        }
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        path.close()
        editMask(mode) { c, paint -> c.drawPath(path, paint) }
    }

    // Волшебная палочка: выделить похожий по цвету участок.
    // contiguous = true — только связанную область, false — такой цвет по всему холсту.
    fun magicWand(x: Float, y: Float, mode: SelectMode, tolerance: Int, contiguous: Boolean, allLayers: Boolean): Boolean {
        if (busy) return false
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until width || iy !in 0 until height) return false
        val w = width
        val h = height
        val source = if (allLayers) flatten(WHITE) else (active?.bitmap ?: return false)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        if (allLayers) source.recycle()

        val region = regionMask(pixels, w, h, ix, iy, tolerance, contiguous)
        for (i in pixels.indices) pixels[i] = if (region[i]) WHITE else CLEAR
        val picked = newBitmap()
        picked.setPixels(pixels, 0, w, 0, 0, w, h)
        editMask(mode) { c, paint -> c.drawBitmap(picked, 0f, 0f, paint) }
        picked.recycle()
        return true
    }

    fun selectAll() {
        if (busy) return
        pushSelection()
        val m = newBitmap()
        m.eraseColor(WHITE)
        selectionMask = m
        refreshSelection()
        touch()
    }

    fun invertSelection() {
        if (busy) return
        pushSelection()
        val m = newBitmap()
        m.eraseColor(WHITE)
        selectionMask?.let { old ->
            Canvas(m).drawBitmap(old, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        selectionMask = m
        refreshSelection()
        touch()
    }

    fun clearSelection() {
        if (busy) return
        selectionDraft = emptyList()
        if (selectionMask == null) {
            touch()
            return
        }
        pushSelection()
        selectionMask = null
        refreshSelection()
        touch()
    }

    // Общая правка маски: с записью в историю и учётом режима.
    private fun editMask(mode: SelectMode, draw: (Canvas, Paint) -> Unit) {
        pushSelection()
        val mask = if (mode == SelectMode.REPLACE || selectionMask == null) newBitmap() else selectionMask!!
        if (mode == SelectMode.SUBTRACT && selectionMask == null) {
            // Вычитать не из чего.
            refreshSelection()
            touch()
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = WHITE
            style = Paint.Style.FILL
            if (mode == SelectMode.SUBTRACT) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        draw(Canvas(mask), paint)
        selectionMask = mask
        refreshSelection()
        touch()
    }

    // Пересчитываем границы и контур. Пустая маска — значит выделения нет.
    private fun refreshSelection() {
        val mask = selectionMask
        if (mask == null) {
            hasSelection = false
            selBounds = null
            selectionOutline = FloatArray(0)
            selectionVersion++
            return
        }
        val w = width
        val h = height
        val row = IntArray(w)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            mask.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 8) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    maxY = y
                }
            }
        }
        if (maxX < 0) {
            selectionMask = null
            hasSelection = false
            selBounds = null
            selectionOutline = FloatArray(0)
            selectionVersion++
            return
        }
        selBounds = Rect(minX, minY, maxX + 1, maxY + 1)
        selectionOutline = buildOutline(mask)
        hasSelection = true
        selectionVersion++
    }

    // Контур по сетке (на больших холстах — с шагом, чтобы было быстро).
    private fun buildOutline(mask: Bitmap): FloatArray {
        val w = width
        val h = height
        val step = max(1, (max(w, h) + 1199) / 1200)
        val gw = (w + step - 1) / step
        val gh = (h + step - 1) / step
        val inside = BooleanArray(gw * gh)
        val row = IntArray(w)
        for (gy in 0 until gh) {
            mask.getPixels(row, 0, w, 0, min(h - 1, gy * step), w, 1)
            for (gx in 0 until gw) {
                inside[gy * gw + gx] = (row[min(w - 1, gx * step)] ushr 24) > 127
            }
        }
        fun ins(gx: Int, gy: Int): Boolean = gx in 0 until gw && gy in 0 until gh && inside[gy * gw + gx]

        var out = FloatArray(4096)
        var n = 0
        fun add(x1: Int, y1: Int, x2: Int, y2: Int) {
            if (n + 4 > out.size) out = out.copyOf(out.size * 2)
            out[n++] = min(w, x1 * step).toFloat()
            out[n++] = min(h, y1 * step).toFloat()
            out[n++] = min(w, x2 * step).toFloat()
            out[n++] = min(h, y2 * step).toFloat()
        }
        // Горизонтальные границы
        for (gy in 0..gh) {
            var gx = 0
            while (gx < gw) {
                if (ins(gx, gy - 1) != ins(gx, gy)) {
                    val start = gx
                    while (gx < gw && ins(gx, gy - 1) != ins(gx, gy)) gx++
                    add(start, gy, gx, gy)
                } else {
                    gx++
                }
            }
        }
        // Вертикальные границы
        for (gx in 0..gw) {
            var gy = 0
            while (gy < gh) {
                if (ins(gx - 1, gy) != ins(gx, gy)) {
                    val start = gy
                    while (gy < gh && ins(gx - 1, gy) != ins(gx, gy)) gy++
                    add(gx, start, gx, gy)
                } else {
                    gy++
                }
            }
        }
        return out.copyOf(n)
    }

    // Копия выделенного (или всего слоя) и её левый верхний угол на холсте.
    fun copySelection(): Pair<Bitmap, Offset>? {
        val layer = active ?: return null
        if (busy) return null
        val mask = selectionMask
        val b = if (mask != null) selBounds ?: return null else Rect(0, 0, width, height)
        val out = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(layer.bitmap, -b.left.toFloat(), -b.top.toFloat(), null)
        if (mask != null) maskInto(out, mask, -b.left.toFloat(), -b.top.toFloat())
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

    // Поднимаем выделенное (или весь слой) в плавающий кусок.
    fun beginTransform(): Boolean {
        val layer = active ?: return false
        if (busy) return false
        val mask = selectionMask
        val b = if (mask != null) selBounds ?: return false else Rect(0, 0, width, height)

        push(HistoryEntry(null, activeIndex, listOf(layer to copyOf(layer.bitmap)), SelSnapshot(mask)))

        val piece = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888)
        Canvas(piece).drawBitmap(layer.bitmap, -b.left.toFloat(), -b.top.toFloat(), null)
        var pieceMask: Bitmap? = null
        if (mask != null) {
            maskInto(piece, mask, -b.left.toFloat(), -b.top.toFloat())
            pieceMask = Bitmap.createBitmap(mask, b.left, b.top, b.width(), b.height())
            Canvas(layer.bitmap).drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        } else {
            layer.bitmap.eraseColor(CLEAR)
        }

        val m = Matrix()
        m.setTranslate(b.left.toFloat(), b.top.toFloat())
        // Пока кусок двигают, контур выделения не показываем.
        selectionMask = null
        refreshSelection()
        floating = FloatingPiece(piece, pieceMask, layer, m)
        op = Op.TRANSFORM
        touch()
        settled()
        return true
    }

    // Вставка картинки новым слоем сразу в режиме трансформации.
    fun beginPaste(bitmap: Bitmap, placement: Matrix, name: String) {
        if (busy) return
        push(HistoryEntry(layers.toList(), activeIndex, emptyList(), SelSnapshot(selectionMask)))
        val layer = Layer(nextId++, name, newBitmap())
        val index = if (activeIndex in layers.indices) activeIndex + 1 else layers.size
        layers.add(index, layer)
        activeIndex = index
        selectionMask = null
        refreshSelection()
        floating = FloatingPiece(bitmap, null, layer, Matrix(placement))
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
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        Canvas(f.layer.bitmap).drawBitmap(f.bitmap, f.matrix, paint)
        // Выделение едет вместе с куском.
        f.mask?.let { pm ->
            val m = newBitmap()
            Canvas(m).drawBitmap(pm, f.matrix, paint)
            selectionMask = m
            refreshSelection()
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
        touch()
        settled()
    }

    // ---------- Фильтры ----------

    // Цветовая коррекция активного слоя (внутри выделения).
    fun applyColorMatrix(cm: ColorMatrix) {
        val layer = active ?: return
        if (busy) return
        pushPixels(layer)
        val filtered = newBitmap()
        Canvas(filtered).drawBitmap(layer.bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        replaceMasked(layer, filtered)
        filtered.recycle()
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
        val scaled = Bitmap.createScaledBitmap(cur, width, height, true)
        if (scaled !== cur) cur.recycle()
        val blurred = if (scaled.isMutable) scaled else copyOf(scaled).also { scaled.recycle() }
        replaceMasked(layer, blurred)
        blurred.recycle()
        touch()
        settled()
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
        val full = flatten(WHITE)
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
            if (tool == Tool.MARKER) {
                strokeCap = Paint.Cap.SQUARE
                strokeJoin = Paint.Join.BEVEL
            }
        }

    // Ластик рисует в буфер непрозрачно; сила стирания задаётся при переносе.
    private fun eraserPaint(size: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.BLACK
        }

    private companion object {
        const val MAX_HISTORY = 20
    }
}

private fun similar(a: Int, b: Int, tol: Int): Boolean =
    abs((a ushr 24) - (b ushr 24)) <= tol &&
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tol &&
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tol &&
        abs((a and 0xFF) - (b and 0xFF)) <= tol

// Область похожего цвета от точки (sx, sy).
// contiguous = true — только связанная область (заливка по контуру),
// false — все пиксели такого цвета на картинке.
private fun regionMask(pixels: IntArray, w: Int, h: Int, sx: Int, sy: Int, tolerance: Int, contiguous: Boolean): BooleanArray {
    val target = pixels[sy * w + sx]
    val mask = BooleanArray(w * h)
    if (!contiguous) {
        for (i in pixels.indices) mask[i] = similar(pixels[i], target, tolerance)
        return mask
    }
    var stack = IntArray(4096)
    var sp = 0

    fun push(v: Int) {
        if (sp == stack.size) stack = stack.copyOf(sp * 2)
        stack[sp++] = v
    }

    fun matches(i: Int): Boolean = !mask[i] && similar(pixels[i], target, tolerance)

    push(sy * w + sx)
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
    return mask
}

private fun polygonArea(pts: List<Offset>): Float {
    var a = 0f
    for (i in pts.indices) {
        val p = pts[i]
        val q = pts[(i + 1) % pts.size]
        a += p.x * q.y - q.x * p.y
    }
    return abs(a) / 2f
}
