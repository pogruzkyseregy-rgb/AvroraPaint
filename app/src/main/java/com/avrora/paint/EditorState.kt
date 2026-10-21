package com.avrora.paint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

    // У кисти и ластика свои размеры.
    var size: Float
        get() = if (tool == Tool.ERASER) eraserSize else brushSize
        set(value) {
            if (tool == Tool.ERASER) eraserSize = value else brushSize = value
        }

    val drawsOnDrag: Boolean
        get() = tool != Tool.FILL && tool != Tool.PICKER

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
            else -> Unit
        }
    }

    fun move(doc: Document, p: Offset) {
        when {
            tool.isBrush -> doc.strokeTo(p.x, p.y)
            tool.isShape -> doc.updateShape(p.x, p.y)
            tool == Tool.GRADIENT -> doc.updateGradient(p.x, p.y)
            else -> Unit
        }
    }

    fun end(doc: Document) {
        if (!doc.busy) return
        doc.endOp()
        if (tool != Tool.ERASER) pushRecent(color)
    }

    fun cancel(doc: Document) {
        doc.cancelOp()
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
            else -> Unit
        }
    }

    fun pickAt(doc: Document, p: Offset) {
        val c = doc.sampleColor(p.x, p.y) ?: return
        color = Color(c)
    }
}
