package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

// Один законченный мазок: точки, цвет и толщина.
data class BrushStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

// Состояние рисования. Вся история мазков хранится здесь,
// поэтому отмена (undo) — это просто удаление последнего мазка.
class DrawingState {
    val strokes = mutableStateListOf<BrushStroke>()
    val current = mutableStateListOf<Offset>()

    var brushColor by mutableStateOf(AvroraColors.Fluorite)
    var brushWidth by mutableFloatStateOf(12f)

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    fun clear() {
        strokes.clear()
        current.clear()
    }
}

@Composable
fun rememberDrawingState(): DrawingState = remember { DrawingState() }

@Composable
fun DrawingCanvas(state: DrawingState, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        state.current.clear()
                        state.current.add(offset)
                    },
                    onDrag = { change, _ ->
                        state.current.add(change.position)
                    },
                    onDragEnd = {
                        if (state.current.size > 1) {
                            state.strokes.add(
                                BrushStroke(
                                    points = state.current.toList(),
                                    color = state.brushColor,
                                    width = state.brushWidth
                                )
                            )
                        }
                        state.current.clear()
                    },
                    onDragCancel = {
                        state.current.clear()
                    }
                )
            }
    ) {
        // Сначала рисуем готовые мазки, потом тот, который сейчас рисуется.
        state.strokes.forEach { stroke ->
            drawStroke(stroke.points, stroke.color, stroke.width)
        }
        drawStroke(state.current.toList(), state.brushColor, state.brushWidth)
    }
}

private fun DrawScope.drawStroke(points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
