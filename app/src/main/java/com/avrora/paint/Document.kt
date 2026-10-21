package com.avrora.paint

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Инструменты рисования.
enum class Tool { PEN, SOFT, MARKER, ERASER, GRADIENT }

// Один слой — это отдельная битмапа того же размера, что и холст.
class Layer(val id: Long, name: String, var bitmap: Bitmap) {
    var name by mutableStateOf(name)
    var visible by mutableStateOf(true)
    var opacity by mutableFloatStateOf(1f)
}

// Документ — холст со слоями и историей отмены.
class Document(val width: Int, val height: Int) {

    val layers = mutableStateListOf<Layer>()
    var activeIndex by mutableIntStateOf(-1)

    // Счётчик изменений. Каждое изменение пикселей увеличивает его,
    // и Compose перерисовывает холст.
    var revision by mutableIntStateOf(0)

    private var nextId = 1L
    private val history = ArrayDeque<Pair<Long, Bitmap>>()
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    val active: Layer? get() = layers.getOrNull(activeIndex)

    private fun changed() {
        revision++
    }

    private fun newBitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    fun addLayer(name: String? = null): Layer {
        val layer = Layer(nextId++, name ?: "Слой ${layers.size + 1}", newBitmap())
        layers.add(layer)
        activeIndex = layers.lastIndex
        changed()
        return layer
    }

    fun duplicateActive() {
        val src = active ?: return
        val copy = Layer(nextId++, "${src.name} копия", src.bitmap.copy(Bitmap.Config.ARGB_8888, true))
        copy.visible = src.visible
        copy.opacity = src.opacity
        layers.add(activeIndex + 1, copy)
        activeIndex += 1
        changed()
    }

    fun removeActive() {
        val i = activeIndex
        if (i !in layers.indices || layers.size <= 1) return
        layers.removeAt(i)
        activeIndex = (i - 1).coerceAtLeast(0)
        changed()
    }

    fun moveActive(delta: Int) {
        val from = activeIndex
        val to = from + delta
        if (from !in layers.indices || to !in layers.indices) return
        val item = layers.removeAt(from)
        layers.add(to, item)
        activeIndex = to
        changed()
    }

    // Запоминаем слой перед действием, чтобы его можно было отменить.
    fun beginAction() {
        val layer = active ?: return
        history.addLast(layer.id to layer.bitmap.copy(Bitmap.Config.ARGB_8888, false))
        if (history.size > MAX_HISTORY) history.removeFirst()
        endStroke()
    }

    fun undo() {
        val (id, snapshot) = history.removeLastOrNull() ?: return
        layers.firstOrNull { it.id == id }?.bitmap = snapshot
        changed()
    }

    // Рисуем отрезок от предыдущей точки до текущей прямо в битмапу слоя.
    fun stroke(x: Float, y: Float, tool: Tool, color: Int, width: Float) {
        val layer = active ?: return
        val canvas = Canvas(layer.bitmap)
        val paint = brushPaint(tool, color, width)
        if (lastX.isNaN()) {
            canvas.drawLine(x, y, x + 0.01f, y + 0.01f, paint)
        } else {
            canvas.drawLine(lastX, lastY, x, y, paint)
        }
        lastX = x
        lastY = y
        changed()
    }

    fun endStroke() {
        lastX = Float.NaN
        lastY = Float.NaN
    }

    // Линейный градиент от точки (x0, y0) до точки (x1, y1).
    fun fillGradient(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int) {
        val layer = active ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP)
        }
        Canvas(layer.bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        changed()
    }

    private fun brushPaint(tool: Tool, color: Int, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            when (tool) {
                Tool.PEN -> setColor(color)
                Tool.SOFT -> {
                    setColor(color)
                    maskFilter = BlurMaskFilter(width * 0.6f, BlurMaskFilter.Blur.NORMAL)
                }
                Tool.MARKER -> {
                    setColor(color)
                    alpha = 150
                    strokeCap = Paint.Cap.SQUARE
                }
                Tool.ERASER -> xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                Tool.GRADIENT -> setColor(color)
            }
        }

    // Склеиваем все видимые слои в одну картинку для сохранения.
    fun flatten(): Bitmap {
        val out = newBitmap()
        val canvas = Canvas(out)
        val paint = Paint()
        for (layer in layers) {
            if (!layer.visible) continue
            paint.alpha = (layer.opacity * 255).toInt()
            canvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
        }
        return out
    }

    private companion object {
        const val MAX_HISTORY = 10
    }
}
