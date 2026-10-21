package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// Плитка: заголовок, подпись и треугольник в углу.
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
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Column {
            Text(
                title,
                color = AvroraColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Text(caption, color = accent, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// Квадратная кнопка с символом (назад, отмена, меню).
@Composable
fun HudButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(AvroraColors.Panel)
            .border(1.dp, if (enabled) AvroraColors.Fluorite else AvroraColors.Panel)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.3f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Небольшая кнопка действия внутри панелей.
@Composable
fun SmallAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(AvroraColors.Black)
            .border(1.dp, if (danger) AvroraColors.Pink else AvroraColors.Violet)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (danger) AvroraColors.Pink else AvroraColors.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        color = AvroraColors.Fluorite,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = AvroraColors.Violet, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

// Вертикальный ползунок у края холста (размер и непрозрачность).
// fraction — положение от 0 (низ) до 1 (верх).
@Composable
fun VerticalSlider(caption: String, value: String, fraction: Float, onFraction: (Float) -> Unit) {
    val latest by rememberUpdatedState(onFraction)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AvroraColors.Fluorite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(150.dp)
                .background(AvroraColors.Panel.copy(alpha = 0.9f))
                .border(1.dp, AvroraColors.Violet)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun set(y: Float) {
                            latest(1f - (y / size.height).coerceIn(0f, 1f))
                        }
                        set(down.position.y)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            set(ch.position.y)
                            ch.consume()
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                    .background(AvroraColors.Violet.copy(alpha = 0.55f))
            )
        }
        Text(caption, color = AvroraColors.Text, fontSize = 8.sp)
    }
}

// Ряд кружков с цветами.
@Composable
fun SwatchRow(colors: List<Color>, selected: Color, onPick: (Color) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp).horizontalScroll(rememberScrollState())
    ) {
        colors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (c == selected) 3.dp else 1.dp,
                        color = if (c == selected) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}
