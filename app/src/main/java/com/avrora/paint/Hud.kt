package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Рамка с уголками в стиле HUD вокруг содержимого.
@Composable
fun HudFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.drawBehind {
            val l = 18.dp.toPx()
            val s = 2.dp.toPx()
            val c = AvroraColors.Fluorite
            val w = size.width
            val h = size.height
            drawLine(c, Offset(0f, 0f), Offset(l, 0f), s)
            drawLine(c, Offset(0f, 0f), Offset(0f, l), s)
            drawLine(c, Offset(w, 0f), Offset(w - l, 0f), s)
            drawLine(c, Offset(w, 0f), Offset(w, l), s)
            drawLine(c, Offset(0f, h), Offset(l, h), s)
            drawLine(c, Offset(0f, h), Offset(0f, h - l), s)
            drawLine(c, Offset(w, h), Offset(w - l, h), s)
            drawLine(c, Offset(w, h), Offset(w, h - l), s)
        }
    ) {
        content()
    }
}

// Кнопка-плитка: заголовок, подпись и треугольник в углу, как на референсе.
@Composable
fun HudTile(
    title: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (selected) AvroraColors.Fluorite else AvroraColors.Violet
    Box(
        modifier = modifier
            .background(if (selected) AvroraColors.Violet.copy(alpha = 0.35f) else AvroraColors.Panel)
            .border(1.dp, accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(title, color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(caption, color = accent, fontSize = 9.sp)
        }
        Canvas(Modifier.align(Alignment.TopEnd).size(8.dp)) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(p, accent)
        }
    }
}
