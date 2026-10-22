package com.avrora.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Palette = listOf(
    Color(0xFF000000), Color(0xFF3A3A48), Color(0xFF8A8A99), Color(0xFFFFFFFF),
    Color(0xFFE53950), Color(0xFFFF8C42), Color(0xFFFFD166), Color(0xFF06D6A0),
    Color(0xFF118C8C), AvroraColors.Fluorite, Color(0xFF3A5BFF), AvroraColors.Violet,
    AvroraColors.Pink, Color(0xFF7A4B2A), Color(0xFFF2C6A8), Color(0xFF2A1450)
)

private fun Tool.hint(): String = when (this) {
    Tool.PEN -> "Чёткая линия с ровным краем"
    Tool.SOFT -> "Мягкий размытый край — для теней и света"
    Tool.AIRBRUSH -> "Цвет набирается, пока ведёте по одному месту"
    Tool.MARKER -> "Полупрозрачный штрих с прямым концом"
    Tool.ERASER -> "Стирает до прозрачности. Размер — свой, отдельно от кисти"
    Tool.FILL -> "Коснитесь замкнутой области, чтобы залить её"
    Tool.GRADIENT -> "Проведите пальцем: от цвета 1 к цвету 2"
    Tool.PICKER -> "Коснитесь или ведите по холсту, чтобы взять цвет"
    Tool.LINE -> "Тяните от начала до конца линии"
    Tool.RECT -> "Тяните от одного угла до другого"
    Tool.OVAL -> "Тяните от одного угла до другого"
    Tool.SELECT_RECT -> "Тяните рамку. Потом можно копировать, вырезать и двигать"
    Tool.SELECT_LASSO -> "Обведите пальцем нужную часть рисунка"
    Tool.SELECT_WAND -> "Коснитесь цвета — выделится всё такое же"
}

// Общая рамка выдвижной панели: ручка, заголовок, кнопка закрытия.
// Смахните ручку вниз или нажмите ✕, чтобы закрыть.
@Composable
fun SheetContainer(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .glass(shape, alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { change, dy ->
                            total += dy
                            change.consume()
                            if (total > 60f) {
                                total = Float.NEGATIVE_INFINITY
                                onClose()
                            }
                        }
                    )
                }
                .padding(top = 8.dp, start = 16.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(AvroraColors.Text.copy(alpha = 0.3f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) { SheetTitle(title) }
                IconBtn(Ico.CLOSE) { onClose() }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            content = content
        )
    }
}

// ---------- Инструменты ----------

@Composable
fun ToolsSheet(editor: EditorState, onTransform: () -> Unit, onFilters: () -> Unit, onClose: () -> Unit) {
    SheetContainer("ИНСТРУМЕНТЫ", onClose) {
        ToolGroup("КИСТИ", listOf(Tool.PEN, Tool.SOFT, Tool.AIRBRUSH, Tool.MARKER, Tool.ERASER), editor)
        ToolGroup("ЦВЕТ И ЗАЛИВКА", listOf(Tool.FILL, Tool.GRADIENT, Tool.PICKER), editor)
        ToolGroup("ФИГУРЫ", listOf(Tool.LINE, Tool.RECT, Tool.OVAL), editor)
        SectionLabel("ВЫДЕЛЕНИЕ И ПРАВКА")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            ToolTile(Tool.SELECT_RECT, editor.tool == Tool.SELECT_RECT, Modifier.weight(1f)) { editor.selectTool(Tool.SELECT_RECT) }
            ToolTile(Tool.SELECT_LASSO, editor.tool == Tool.SELECT_LASSO, Modifier.weight(1f)) { editor.selectTool(Tool.SELECT_LASSO) }
            ToolTile(Tool.SELECT_WAND, editor.tool == Tool.SELECT_WAND, Modifier.weight(1f)) { editor.selectTool(Tool.SELECT_WAND) }
            Spacer(Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            ActionTile(Ico.TRANSFORM, "Трансформ", Modifier.weight(1f), onTransform)
            ActionTile(Ico.FX, "Эффекты", Modifier.weight(1f), onFilters)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }

        val tool = editor.tool
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(16.dp), alpha = 0.6f)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvIcon(tool.icon(), AvroraColors.Fluorite, 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(tool.label, color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(tool.hint(), color = AvroraColors.Text.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

            if (tool.isBrush) {
                SectionLabel("СГЛАЖИВАНИЕ ЛИНИИ · ${(editor.smoothing * 100).toInt()}%")
                Slider(value = editor.smoothing, onValueChange = { editor.smoothing = it }, valueRange = 0f..0.9f)
            }
            if (tool == Tool.RECT || tool == Tool.OVAL) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Залитая фигура", color = AvroraColors.Text, modifier = Modifier.weight(1f))
                    Switch(checked = editor.shapeFill, onCheckedChange = { editor.shapeFill = it })
                }
            }
            if (tool.isSelect) {
                SectionLabel("НОВОЕ ВЫДЕЛЕНИЕ")
                SelectModeRow(editor)
            }
            if (tool == Tool.SELECT_WAND) {
                SectionLabel("ПОХОЖЕСТЬ ЦВЕТА · ${editor.wandTolerance.toInt()}")
                Slider(value = editor.wandTolerance, onValueChange = { editor.wandTolerance = it }, valueRange = 0f..128f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Только связанная область", color = AvroraColors.Text, fontSize = 13.sp)
                        Text("выкл — этот цвет по всему холсту", color = AvroraColors.Text.copy(alpha = 0.55f), fontSize = 10.sp)
                    }
                    Switch(checked = editor.wandContiguous, onCheckedChange = { editor.wandContiguous = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("По всем слоям", color = AvroraColors.Text, fontSize = 13.sp)
                        Text("выкл — только по активному слою", color = AvroraColors.Text.copy(alpha = 0.55f), fontSize = 10.sp)
                    }
                    Switch(checked = editor.wandAllLayers, onCheckedChange = { editor.wandAllLayers = it })
                }
            }
            if (tool == Tool.FILL) {
                SectionLabel("ДОПУСК ЗАЛИВКИ · ${editor.fillTolerance.toInt()}")
                Slider(value = editor.fillTolerance, onValueChange = { editor.fillTolerance = it }, valueRange = 0f..128f)
            }
            if (tool == Tool.GRADIENT) {
                SectionLabel("ВТОРОЙ ЦВЕТ")
                SwatchRow(Palette, editor.secondColor) { editor.secondColor = it }
                Spacer(Modifier.height(6.dp))
                SmallAction("Поменять цвета местами", Ico.FLIP) {
                    val c = editor.color
                    editor.color = editor.secondColor
                    editor.secondColor = c
                }
            }
        }
    }
}

@Composable
private fun ToolGroup(title: String, tools: List<Tool>, editor: EditorState) {
    SectionLabel(title)
    tools.chunked(4).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            row.forEach { t ->
                ToolTile(t, editor.tool == t, Modifier.weight(1f)) { editor.selectTool(t) }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ---------- Цвет ----------

@Composable
fun ColorSheet(editor: EditorState, onClose: () -> Unit) {
    var editSecond by remember { mutableStateOf(false) }
    val second = editSecond && editor.tool == Tool.GRADIENT
    val current = if (second) editor.secondColor else editor.color
    val set: (Color) -> Unit = { if (second) editor.secondColor = it else editor.color = it }

    SheetContainer("ЦВЕТ", onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(current)
                    .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                String.format("#%06X", current.toArgb() and 0xFFFFFF),
                color = AvroraColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SmallAction("Пипетка", Ico.PICKER) {
                editor.selectTool(Tool.PICKER)
                onClose()
            }
        }
        if (editor.tool == Tool.GRADIENT) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilterChip(selected = !second, onClick = { editSecond = false }, label = { Text("Цвет 1") })
                FilterChip(selected = second, onClick = { editSecond = true }, label = { Text("Цвет 2") })
            }
        }
        Box(Modifier.clip(RoundedCornerShape(14.dp))) {
            HsvPicker(current, set)
        }
        if (editor.recent.isNotEmpty()) {
            SectionLabel("НЕДАВНИЕ")
            SwatchRow(editor.recent.toList(), current, set)
        }
        SectionLabel("ПАЛИТРА АВРОРЫ")
        SwatchRow(Palette.take(8), current, set)
        SwatchRow(Palette.drop(8), current, set)
    }
}

// ---------- Слои ----------

@Composable
fun LayersSheet(doc: Document, editor: EditorState, onImport: () -> Unit, onClose: () -> Unit) {
    // Миниатюры обновляются после каждого завершённого действия.
    @Suppress("UNUSED_VARIABLE")
    val version = doc.historyVersion

    SheetContainer("СЛОИ", onClose) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            SmallAction("Новый", Ico.PLUS) { doc.addLayer() }
            SmallAction("Фото", Ico.PHOTO) { onImport() }
            SmallAction("Копия", Ico.COPY) { doc.duplicateActive() }
            SmallAction("Объединить", Ico.MERGE) { doc.mergeDown() }
            SmallAction("Выше", Ico.UP) { doc.moveActive(1) }
            SmallAction("Ниже", Ico.DOWN) { doc.moveActive(-1) }
        }

        Spacer(Modifier.height(10.dp))
        for (i in doc.layers.indices.reversed()) {
            val layer = doc.layers[i]
            LayerRow(
                layer = layer,
                active = i == doc.activeIndex,
                onSelect = { doc.setActive(i) },
                onToggle = {
                    layer.visible = !layer.visible
                    doc.touch()
                }
            )
        }

        doc.active?.let { layer ->
            SectionLabel("АКТИВНЫЙ СЛОЙ · ${layer.name.uppercase()}")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp), alpha = 0.6f)
                    .padding(12.dp)
            ) {
                Text("Непрозрачность · ${(layer.opacity * 100).toInt()}%", color = AvroraColors.Text, fontSize = 13.sp)
                Slider(
                    value = layer.opacity,
                    onValueChange = {
                        layer.opacity = it
                        doc.touch()
                    },
                    valueRange = 0f..1f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvIcon(Ico.LOCK, AvroraColors.Violet, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Защита прозрачности", color = AvroraColors.Text, fontSize = 13.sp)
                        Text("рисовать только поверх нарисованного", color = AvroraColors.Text.copy(alpha = 0.55f), fontSize = 10.sp)
                    }
                    Switch(checked = layer.alphaLock, onCheckedChange = {
                        layer.alphaLock = it
                        doc.touch()
                    })
                }
                Text(
                    if (blendModesSupported) "Режим наложения" else "Режим наложения (нужен Android 10+)",
                    color = AvroraColors.Text,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    LayerBlend.values().forEach { mode ->
                        FilterChip(
                            selected = layer.blend == mode,
                            onClick = {
                                layer.blend = mode
                                doc.touch()
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    SmallAction("Залить цветом", Ico.FILL) { doc.fillActive(editor.color.toArgb()) }
                    SmallAction("Очистить", Ico.CLEAR) { doc.clearActive() }
                    SmallAction("Удалить", Ico.TRASH, danger = true) { doc.removeActive() }
                }
            }
        }
    }
}

@Composable
private fun LayerRow(layer: Layer, active: Boolean, onSelect: () -> Unit, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (active) AvroraColors.Violet.copy(alpha = 0.28f) else AvroraColors.Black.copy(alpha = 0.5f))
            .border(1.dp, if (active) AccentBorder else GlassBorder, shape)
            .clickable(onClick = onSelect)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))) {
            Checker(Modifier.fillMaxSize())
            Image(
                bitmap = layer.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(layer.name, color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val lock = if (layer.alphaLock) " · защита" else ""
            Text(
                "${(layer.opacity * 100).toInt()}% · ${layer.blend.label}$lock",
                color = if (active) AvroraColors.Fluorite else AvroraColors.Violet,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            AvIcon(
                if (layer.visible) Ico.EYE else Ico.EYE_OFF,
                if (layer.visible) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.4f),
                22.dp
            )
        }
    }
}

// Шахматка — показывает прозрачные места на миниатюре.
@Composable
fun Checker(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = 6.dp.toPx()
        drawRect(Color(0xFFE8E8EE))
        val dark = Color(0xFFC4C4D0)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(dark, topLeft = Offset(x, y), size = Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

// Плитка-действие (не инструмент): иконка и подпись.
@Composable
fun ActionTile(icon: Ico, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(shape)
            .background(AvroraColors.Black.copy(alpha = 0.5f))
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AvIcon(icon, AvroraColors.Violet, 26.dp)
            Spacer(Modifier.height(4.dp))
            Text(label, color = AvroraColors.Text.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

// ---------- Эффекты ----------

@Composable
fun FiltersSheet(doc: Document, editor: EditorState, onClose: () -> Unit) {
    // Пока панель открыта, изменения видны на холсте.
    androidx.compose.runtime.DisposableEffect(Unit) {
        editor.filterPreview = true
        onDispose { editor.filterPreview = false }
    }
    var blur by remember { androidx.compose.runtime.mutableFloatStateOf(8f) }
    val f = editor.filter

    SheetContainer("ЭФФЕКТЫ", onClose) {
        Text(
            if (doc.hasSelection) "Меняется только выделенная часть активного слоя"
            else "Меняется активный слой · ${doc.active?.name ?: ""}",
            color = AvroraColors.Text.copy(alpha = 0.65f),
            fontSize = 12.sp
        )
        FilterSlider("ЯРКОСТЬ", "${(f.brightness * 100).toInt()}", f.brightness, -1f..1f) {
            editor.filter = editor.filter.copy(brightness = it)
        }
        FilterSlider("КОНТРАСТ", "${(f.contrast * 100).toInt()}%", f.contrast, 0f..2f) {
            editor.filter = editor.filter.copy(contrast = it)
        }
        FilterSlider("НАСЫЩЕННОСТЬ", "${(f.saturation * 100).toInt()}%", f.saturation, 0f..2f) {
            editor.filter = editor.filter.copy(saturation = it)
        }
        FilterSlider("ОТТЕНОК", "${f.hue.toInt()}°", f.hue, -180f..180f) {
            editor.filter = editor.filter.copy(hue = it)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            AvIcon(Ico.INVERT, AvroraColors.Violet, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text("Инверсия цветов", color = AvroraColors.Text, modifier = Modifier.weight(1f))
            Switch(checked = f.invert, onCheckedChange = { editor.filter = editor.filter.copy(invert = it) })
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            SmallAction("Ч/Б") { editor.filter = FilterParams(saturation = 0f) }
            SmallAction("Ярче") { editor.filter = FilterParams(brightness = 0.12f, contrast = 1.15f, saturation = 1.2f) }
            SmallAction("Холодный") { editor.filter = FilterParams(hue = -18f, saturation = 1.1f) }
            SmallAction("Сброс") { editor.filter = FilterParams() }
        }
        Spacer(Modifier.height(10.dp))
        SmallAction("Применить", Ico.CHECK) {
            if (!editor.filter.isIdentity) {
                doc.applyColorMatrix(editor.filter.toColorMatrix())
                editor.filter = FilterParams()
                editor.flash("Эффект применён")
            }
        }

        SectionLabel("РАЗМЫТИЕ · ${blur.toInt()}")
        Slider(value = blur, onValueChange = { blur = it }, valueRange = 2f..64f)
        SmallAction("Размыть слой", Ico.SOFT) {
            doc.blurActive(blur)
            editor.flash("Размыто")
        }
    }
}

@Composable
private fun FilterSlider(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(title, color = AvroraColors.Violet, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Text(value, color = AvroraColors.Fluorite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    Slider(value = current, onValueChange = onChange, valueRange = range)
}

// Как сочетать новое выделение со старым: добавить, убрать или заменить.
@Composable
fun SelectModeRow(editor: EditorState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectMode.values().forEach { mode ->
            val icon = when (mode) {
                SelectMode.ADD -> Ico.SEL_ADD
                SelectMode.SUBTRACT -> Ico.SEL_SUB
                SelectMode.REPLACE -> Ico.SEL_NEW
            }
            FilterChip(
                selected = editor.selectMode == mode,
                onClick = { editor.selectMode = mode },
                label = { Text(mode.label) },
                leadingIcon = { AvIcon(icon, AvroraColors.Text, 18.dp) }
            )
        }
    }
}
