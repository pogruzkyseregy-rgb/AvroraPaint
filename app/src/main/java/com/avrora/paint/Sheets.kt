package com.avrora.paint

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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

// ---------- Инструменты ----------

@Composable
fun ToolsSheet(editor: EditorState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        SheetTitle("ИНСТРУМЕНТЫ")
        Tool.values().toList().chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                row.forEach { t ->
                    HudTile(t.label, t.caption, editor.tool == t, { editor.selectTool(t) }, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        val tool = editor.tool
        if (tool.isBrush) {
            SectionLabel("СГЛАЖИВАНИЕ ЛИНИИ: ${(editor.smoothing * 100).toInt()}%")
            Slider(value = editor.smoothing, onValueChange = { editor.smoothing = it }, valueRange = 0f..0.9f)
        }
        if (tool == Tool.RECT || tool == Tool.OVAL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Залитая фигура", color = AvroraColors.Text, modifier = Modifier.weight(1f))
                Switch(checked = editor.shapeFill, onCheckedChange = { editor.shapeFill = it })
            }
        }
        if (tool == Tool.FILL) {
            SectionLabel("ДОПУСК ЗАЛИВКИ: ${editor.fillTolerance.toInt()}")
            Slider(value = editor.fillTolerance, onValueChange = { editor.fillTolerance = it }, valueRange = 0f..128f)
        }
        if (tool == Tool.GRADIENT) {
            SectionLabel("ВТОРОЙ ЦВЕТ ГРАДИЕНТА")
            SwatchRow(Palette, editor.secondColor) { editor.secondColor = it }
            TextButton(onClick = {
                val c = editor.color
                editor.color = editor.secondColor
                editor.secondColor = c
            }) { Text("⇄ Поменять цвета местами", color = AvroraColors.Fluorite) }
        }
    }
}

// ---------- Цвет ----------

@Composable
fun ColorSheet(editor: EditorState) {
    var editSecond by remember { mutableStateOf(false) }
    val second = editSecond && editor.tool == Tool.GRADIENT
    val current = if (second) editor.secondColor else editor.color
    val set: (Color) -> Unit = { if (second) editor.secondColor = it else editor.color = it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetTitle("ЦВЕТ")
            Spacer(Modifier.weight(1f))
            Text(
                String.format("#%06X", current.toArgb() and 0xFFFFFF),
                color = AvroraColors.Text,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Box(Modifier.size(width = 44.dp, height = 24.dp).background(current).border(1.dp, AvroraColors.Text))
        }
        if (editor.tool == Tool.GRADIENT) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !second, onClick = { editSecond = false }, label = { Text("Цвет 1") })
                FilterChip(selected = second, onClick = { editSecond = true }, label = { Text("Цвет 2") })
            }
        }
        HsvPicker(current, set)
        if (editor.recent.isNotEmpty()) {
            SectionLabel("НЕДАВНИЕ")
            SwatchRow(editor.recent.toList(), current, set)
        }
        SectionLabel("ПАЛИТРА АВРОРЫ")
        SwatchRow(Palette.take(8), current, set)
        SwatchRow(Palette.drop(8), current, set)
        Spacer(Modifier.height(6.dp))
        SmallAction("ПИПЕТКА") { editor.selectTool(Tool.PICKER) }
    }
}

// ---------- Слои ----------

@Composable
fun LayersSheet(doc: Document, editor: EditorState, onImport: () -> Unit) {
    // Читаем счётчик, чтобы миниатюры обновлялись.
    @Suppress("UNUSED_VARIABLE")
    val rev = doc.revision

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        SheetTitle("СЛОИ")
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            SmallAction("+ НОВЫЙ") { doc.addLayer() }
            SmallAction("КОПИЯ") { doc.duplicateActive() }
            SmallAction("ОБЪЕДИНИТЬ ↓") { doc.mergeDown() }
            SmallAction("▲") { doc.moveActive(1) }
            SmallAction("▼") { doc.moveActive(-1) }
            SmallAction("ФОТО") { onImport() }
            SmallAction("ЗАЛИТЬ") { doc.fillActive(editor.color.toArgb()) }
            SmallAction("ОЧИСТИТЬ") { doc.clearActive() }
            SmallAction("УДАЛИТЬ", danger = true) { doc.removeActive() }
        }

        doc.active?.let { layer ->
            SectionLabel("НЕПРОЗРАЧНОСТЬ СЛОЯ: ${(layer.opacity * 100).toInt()}%")
            Slider(
                value = layer.opacity,
                onValueChange = {
                    layer.opacity = it
                    doc.touch()
                },
                valueRange = 0f..1f
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Защита прозрачности", color = AvroraColors.Text, fontSize = 13.sp)
                    Text("рисовать только поверх нарисованного", color = AvroraColors.Violet, fontSize = 10.sp)
                }
                Switch(checked = layer.alphaLock, onCheckedChange = {
                    layer.alphaLock = it
                    doc.touch()
                })
            }
            SectionLabel(if (blendModesSupported) "РЕЖИМ НАЛОЖЕНИЯ" else "РЕЖИМ НАЛОЖЕНИЯ (нужен Android 10+)")
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
        }

        SectionLabel("СТЕК СЛОЁВ")
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
    }
}

@Composable
private fun LayerRow(layer: Layer, active: Boolean, onSelect: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (active) AvroraColors.Violet.copy(alpha = 0.35f) else AvroraColors.Black)
            .border(1.dp, if (active) AvroraColors.Fluorite else AvroraColors.Panel)
            .clickable(onClick = onSelect)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).background(Color.White)) {
            Image(
                bitmap = layer.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(layer.name, color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            val lock = if (layer.alphaLock) " · защита" else ""
            Text(
                "${(layer.opacity * 100).toInt()}% · ${layer.blend.label}$lock",
                color = AvroraColors.Violet,
                fontSize = 10.sp
            )
        }
        Box(
            modifier = Modifier.size(44.dp).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (layer.visible) "◉" else "○",
                color = if (layer.visible) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.4f),
                fontSize = 22.sp
            )
        }
    }
}
