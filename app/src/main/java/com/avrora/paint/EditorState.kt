package com.avrora.paint

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Настройки инструментов. Живут, пока открыто приложение,
// и не сбрасываются при переходе между работами.
class EditorState {
    var tool by mutableStateOf(Tool.PEN)
        private set

    // Последняя кисть (не ластик) — для быстрой кнопки «КИСТЬ».
    var lastDrawTool by mutableStateOf(Tool.PEN)
        private set

    var color by mutableStateOf(AvroraColors.Fluorite)
    var secondColor by mutableStateOf(AvroraColors.Violet)
    var brushSize by mutableFloatStateOf(12f)
    var eraserSize by mutableFloatStateOf(40f)
    var opacity by mutableFloatStateOf(1f)
    var smoothing by mutableFloatStateOf(0.35f)
    var shapeFill by mutableStateOf(false)
    var fillTolerance by mutableFloatStateOf(32f)
    val recent = mutableStateListOf<Color>()

    // Где на экране показывать лупу пипетки (null — не показывать).
    var loupe by mutableStateOf<Offset?>(null)

    // Короткое уведомление посередине экрана («Отмена», «150%»).
    var flashText by mutableStateOf("")
        private set
    var flashId by mutableIntStateOf(0)
        private set

    // Показывать кружок размера кисти, пока двигают ползунок.
    var sizePreview by mutableStateOf(false)

    // Внутренний буфер обмена: живёт между работами, пока открыто приложение.
    var clipboard: Bitmap? = null
        private set
    private var clipboardOrigin = Offset(0f, 0f)
    var hasClipboard by mutableStateOf(false)
        private set

    // Цветокоррекция и показывать ли её на холсте до применения.
    var filter by mutableStateOf(FilterParams())
    var filterPreview by mutableStateOf(false)

    private var selStart = Offset(0f, 0f)

    // Выделение: по умолчанию новые участки добавляются к старым.
    var selectMode by mutableStateOf(SelectMode.ADD)
    var wandTolerance by mutableFloatStateOf(24f)
    var wandContiguous by mutableStateOf(true)
    var wandAllLayers by mutableStateOf(true)

    fun copy(doc: Document): Boolean {
        val (bmp, origin) = doc.copySelection() ?: return false
        setClipboard(bmp, origin)
        return true
    }

    fun cut(doc: Document): Boolean {
        val (bmp, origin) = doc.cutSelection() ?: return false
        setClipboard(bmp, origin)
        return true
    }

    private fun setClipboard(bmp: Bitmap, origin: Offset) {
        clipboard?.recycle()
        clipboard = bmp
        clipboardOrigin = origin
        hasClipboard = true
    }

    // Вставляем туда же, откуда скопировали; если не влезает — по центру.
    fun paste(doc: Document): Boolean {
        val src = clipboard ?: return false
        if (doc.busy) return false
        val m = android.graphics.Matrix()
        val fits = clipboardOrigin.x + src.width <= doc.width && clipboardOrigin.y + src.height <= doc.height
        if (fits) {
            m.setTranslate(clipboardOrigin.x, clipboardOrigin.y)
        } else {
            val s = minOf(1f, doc.width.toFloat() / src.width, doc.height.toFloat() / src.height)
            m.setScale(s, s)
            m.postTranslate((doc.width - src.width * s) / 2f, (doc.height - src.height * s) / 2f)
        }
        doc.beginPaste(src.copy(Bitmap.Config.ARGB_8888, false), m, "Вставка")
        return true
    }

    fun flash(text: String) {
        flashText = text
        flashId++
    }

    // У кисти и ластика свои размеры.
    var size: Float
        get() = if (tool == Tool.ERASER) eraserSize else brushSize
        set(value) {
            if (tool == Tool.ERASER) eraserSize = value else brushSize = value
        }

    val drawsOnDrag: Boolean
        get() = tool != Tool.FILL && tool != Tool.PICKER && tool != Tool.SELECT_WAND

    fun selectTool(t: Tool) {
        tool = t
        if (t.isBrush && t != Tool.ERASER) lastDrawTool = t
    }

    fun pushRecent(c: Color) {
        recent.remove(c)
        recent.add(0, c)
        while (recent.size > 14) recent.removeAt(recent.lastIndex)
    }

    private fun strokeOpacity(): Float = if (tool == Tool.MARKER) opacity * 0.6f else opacity

    fun begin(doc: Document, p: Offset) {
        when {
            tool.isBrush -> doc.beginStroke(tool, color.toArgb(), size, strokeOpacity(), p.x, p.y)
            tool.isShape -> doc.beginShape(tool, color.toArgb(), size, opacity, shapeFill, p.x, p.y)
            tool == Tool.GRADIENT -> doc.beginGradient(color.toArgb(), secondColor.toArgb(), opacity, p.x, p.y)
            tool == Tool.SELECT_RECT -> {
                selStart = p
                doc.setDraft(rectPoints(p, p))
            }
            tool == Tool.SELECT_LASSO -> doc.setDraft(listOf(p))
            else -> Unit
        }
    }

    fun move(doc: Document, p: Offset) {
        when {
            tool.isBrush -> doc.strokeTo(p.x, p.y)
            tool.isShape -> doc.updateShape(p.x, p.y)
            tool == Tool.GRADIENT -> doc.updateGradient(p.x, p.y)
            tool == Tool.SELECT_RECT -> doc.setDraft(rectPoints(selStart, p))
            tool == Tool.SELECT_LASSO -> doc.addDraftPoint(p)
            else -> Unit
        }
    }

    private fun rectPoints(a: Offset, b: Offset): List<Offset> =
        listOf(Offset(a.x, a.y), Offset(b.x, a.y), Offset(b.x, b.y), Offset(a.x, b.y))

    fun end(doc: Document) {
        if (tool.isSelect) {
            doc.commitDraft(selectMode)
            return
        }
        if (!doc.busy) return
        doc.endOp()
        if (tool != Tool.ERASER) pushRecent(color)
    }

    fun cancel(doc: Document) {
        if (tool.isSelect) doc.clearDraft() else doc.cancelOp()
    }

    // Короткое касание одним пальцем без движения.
    fun tap(doc: Document, p: Offset) {
        when {
            tool.isBrush -> {
                begin(doc, p)
                end(doc)
            }
            tool == Tool.FILL -> {
                if (doc.floodFill(p.x, p.y, color.toArgb(), opacity, fillTolerance.toInt())) pushRecent(color)
            }
            tool == Tool.PICKER -> pickAt(doc, p)
            tool == Tool.SELECT_WAND -> doc.magicWand(
                p.x, p.y, selectMode, wandTolerance.toInt(), wandContiguous, wandAllLayers
            )
            else -> Unit
        }
    }

    fun pickAt(doc: Document, p: Offset) {
        val c = doc.sampleColor(p.x, p.y) ?: return
        color = Color(c)
    }
}
