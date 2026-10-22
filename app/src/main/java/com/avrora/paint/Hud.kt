package com.avrora.paint

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Общий стиль ----------

val GlassBorder = Brush.linearGradient(
    listOf(
        AvroraColors.Fluorite.copy(alpha = 0.55f),
        AvroraColors.Violet.copy(alpha = 0.35f),
        AvroraColors.Pink.copy(alpha = 0.25f)
    )
)

val ActiveFill = Brush.linearGradient(
    listOf(
        AvroraColors.Fluorite.copy(alpha = 0.30f),
        AvroraColors.Violet.copy(alpha = 0.38f)
    )
)

val AccentBorder = Brush.linearGradient(listOf(AvroraColors.Fluorite, AvroraColors.Violet))

// Полупрозрачная «стеклянная» панель со скруглёнными углами.
fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp), alpha: Float = 0.88f): Modifier =
    this
        .clip(shape)
        .background(AvroraColors.Panel.copy(alpha = alpha))
        .border(1.dp, GlassBorder, shape)

// Фон приложения: тёмный градиент, сияние Авроры и тонкая сетка точек.
fun Modifier.auroraBackground(): Modifier = this.drawBehind {
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF07070B), Color(0xFF0D0A1C), Color(0xFF07070B)))
    )
    val c1 = Offset(size.width * 0.1f, size.height * 0.08f)
    val r1 = size.maxDimension * 0.65f
    drawCircle(
        Brush.radialGradient(listOf(AvroraColors.Violet.copy(alpha = 0.22f), Color.Transparent), center = c1, radius = r1),
        radius = r1,
        center = c1
    )
    val c2 = Offset(size.width * 0.95f, size.height * 0.9f)
    val r2 = size.maxDimension * 0.55f
    drawCircle(
        Brush.radialGradient(listOf(AvroraColors.Fluorite.copy(alpha = 0.12f), Color.Transparent), center = c2, radius = r2),
        radius = r2,
        center = c2
    )
    val step = 24.dp.toPx()
    val dotR = 1.dp.toPx()
    val dot = Color.White.copy(alpha = 0.035f)
    var y = step
    while (y < size.height) {
        var x = step
        while (x < size.width) {
            drawCircle(dot, dotR, Offset(x, y))
            x += step
        }
        y += step
    }
}

// Рамка с уголками в стиле HUD.
@Composable
fun HudFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.drawBehind {
            val l = 18.dp.toPx()
            val s = 2.dp.toPx()
            val c = AvroraColors.Fluorite.copy(alpha = 0.8f)
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

// ---------- Кнопки ----------

// Круглая кнопка с иконкой (назад, отмена, меню).
@Composable
fun IconBtn(icon: Ico, enabled: Boolean = true, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (enabled) AvroraColors.Text else AvroraColors.Text.copy(alpha = 0.25f),
        label = "iconTint"
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AvIcon(icon, tint, 22.dp)
    }
}

// Пункт нижней панели: иконка и подпись, активный подсвечен.
@Composable
fun BarItem(icon: Ico, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (selected) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.75f),
        label = "barTint"
    )
    val glow by animateFloatAsState(if (selected) 1f else 0f, label = "barGlow")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .drawBehind {
                if (glow > 0f) {
                    drawRect(ActiveFill, alpha = glow)
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AvIcon(icon, tint, 22.dp)
            Spacer(Modifier.height(3.dp))
            Text(label, color = tint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Плитка с заголовком и подписью (шаблоны, крупные пункты).
@Composable
fun HudTile(
    title: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(
        if (selected) AvroraColors.Violet.copy(alpha = 0.32f) else AvroraColors.Black.copy(alpha = 0.55f),
        label = "tileBg"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) AccentBorder else GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column {
            Text(
                title,
                color = AvroraColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                caption,
                color = if (selected) AvroraColors.Fluorite else AvroraColors.Violet,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Плитка инструмента: иконка и название.
@Composable
fun ToolTile(tool: Tool, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(
        if (selected) AvroraColors.Violet.copy(alpha = 0.32f) else AvroraColors.Black.copy(alpha = 0.5f),
        label = "toolBg"
    )
    val tint by animateColorAsState(
        if (selected) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.85f),
        label = "toolTint"
    )
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) AccentBorder else GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AvIcon(tool.icon(), tint, 26.dp)
            Spacer(Modifier.height(4.dp))
            Text(tool.label, color = tint, fontSize = 11.sp, maxLines = 1)
        }
    }
}

// Кнопка действия в панелях.
@Composable
fun SmallAction(text: String, icon: Ico? = null, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val color = if (danger) AvroraColors.Pink else AvroraColors.Text
    Row(
        modifier = Modifier
            .clip(shape)
            .background(AvroraColors.Black.copy(alpha = 0.55f))
            .border(1.dp, if (danger) Brush.linearGradient(listOf(AvroraColors.Pink, AvroraColors.Pink)) else GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AvIcon(icon, color, 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        color = AvroraColors.Fluorite,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 2.sp
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = AvroraColors.Violet,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

// ---------- Ползунки ----------

// Вертикальный ползунок у края холста.
// fraction — положение от 0 (низ) до 1 (верх), onActive — палец на ползунке.
@Composable
fun VerticalSlider(
    caption: String,
    value: String,
    fraction: Float,
    onFraction: (Float) -> Unit,
    onActive: (Boolean) -> Unit = {}
) {
    val latest by rememberUpdatedState(onFraction)
    val active by rememberUpdatedState(onActive)
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.glass(RoundedCornerShape(18.dp), alpha = 0.75f).padding(horizontal = 5.dp, vertical = 7.dp)
    ) {
        Text(value, color = AvroraColors.Fluorite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(140.dp)
                .clip(shape)
                .background(AvroraColors.Black.copy(alpha = 0.6f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun set(y: Float) {
                            latest(1f - (y / size.height).coerceIn(0f, 1f))
                        }
                        active(true)
                        set(down.position.y)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            set(ch.position.y)
                            ch.consume()
                        }
                        active(false)
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0.04f, 1f))
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(AvroraColors.Fluorite, AvroraColors.Violet)))
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(caption, color = AvroraColors.Text.copy(alpha = 0.7f), fontSize = 8.sp)
    }
}

// ---------- Цвета ----------

@Composable
fun SwatchRow(colors: List<Color>, selected: Color, onPick: (Color) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.padding(vertical = 4.dp).horizontalScroll(rememberScrollState())
    ) {
        colors.forEach { c ->
            val isSel = c == selected
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = if (isSel) 1.1f else 1f
                        scaleY = if (isSel) 1.1f else 1f
                    }
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (isSel) 3.dp else 1.dp,
                        color = if (isSel) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

// Кнопка текущего цвета в нижней панели. Для градиента показывает оба цвета.
@Composable
fun ColorOrb(color: Color, second: Color?, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(
        if (selected) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.8f),
        label = "orbRing"
    )
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(46.dp)) {
            val r = size.minDimension / 2f
            drawCircle(Color.Black, r)
            drawCircle(color, r - 4.dp.toPx())
            if (second != null) {
                drawArc(
                    color = second,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx())
                )
            }
            drawCircle(
                ring,
                r - 1.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}
