package com.avrora.paint

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

private enum class Panel { NONE, BRUSH, LAYERS, TEMPLATES }

private val Swatches = listOf(
    Color.Black,
    Color.White,
    AvroraColors.Fluorite,
    AvroraColors.Violet,
    AvroraColors.Pink,
    Color(0xFFFFD166),
    Color(0xFF06D6A0),
    Color(0xFF1B1B2A)
)

@Composable
fun EditorScreen() {
    val context = LocalContext.current
    var doc by remember { mutableStateOf(Templates.first().createDocument()) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(AvroraColors.Fluorite) }
    var secondColor by remember { mutableStateOf(AvroraColors.Violet) }
    var width by remember { mutableFloatStateOf(12f) }
    var panel by remember { mutableStateOf(Panel.NONE) }

    fun toggle(p: Panel) {
        panel = if (panel == p) Panel.NONE else p
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AvroraColors.Black)
            .statusBarsPadding()
    ) {
        // Шапка в стиле HUD
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("AVRORA PAINT", color = AvroraColors.Fluorite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "${doc.width} × ${doc.height} · слоёв: ${doc.layers.size}",
                    color = AvroraColors.Violet,
                    fontSize = 11.sp
                )
            }
            Text("СТАТУС: ГОТОВО", color = AvroraColors.Text, fontSize = 10.sp)
        }

        // Холст в рамке
        HudFrame(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            DocumentCanvas(
                doc = doc,
                tool = tool,
                color = color,
                secondColor = secondColor,
                width = width,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Выдвижная панель в зависимости от выбранной плитки
        when (panel) {
            Panel.BRUSH -> BrushPanel(
                tool = tool,
                onTool = { tool = it },
                color = color,
                onColor = { color = it },
                secondColor = secondColor,
                onSecond = { secondColor = it },
                width = width,
                onWidth = { width = it }
            )
            Panel.LAYERS -> LayersPanel(doc)
            Panel.TEMPLATES -> TemplatesPanel { template ->
                doc = template.createDocument()
                panel = Panel.NONE
            }
            Panel.NONE -> Unit
        }

        // Плитки внизу
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudTile("КИСТИ", "перо · мягкая · маркер", panel == Panel.BRUSH, { toggle(Panel.BRUSH) }, Modifier.width(140.dp))
            HudTile("СЛОИ", "стек слоёв", panel == Panel.LAYERS, { toggle(Panel.LAYERS) }, Modifier.width(110.dp))
            HudTile("ШАБЛОНЫ", "новый холст", panel == Panel.TEMPLATES, { toggle(Panel.TEMPLATES) }, Modifier.width(120.dp))
            HudTile("ОТМЕНА", "шаг назад", false, { doc.undo() }, Modifier.width(110.dp))
            HudTile("СОХРАНИТЬ", "PNG", false, {
                val path = saveImage(context, doc)
                Toast.makeText(context, "Сохранено: $path", Toast.LENGTH_LONG).show()
            }, Modifier.width(130.dp))
        }
    }
}

@Composable
private fun BrushPanel(
    tool: Tool,
    onTool: (Tool) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    secondColor: Color,
    onSecond: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Tool.values().forEach { t ->
                FilterChip(
                    selected = tool == t,
                    onClick = { onTool(t) },
                    label = { Text(t.label()) }
                )
            }
        }
        Text("Цвет", color = AvroraColors.Violet, fontSize = 11.sp)
        SwatchRow(selected = color, onPick = onColor)
        if (tool == Tool.GRADIENT) {
            Text("Цвет 2 для градиента", color = AvroraColors.Violet, fontSize = 11.sp)
            SwatchRow(selected = secondColor, onPick = onSecond)
        }
        Text("Толщина: ${width.toInt()}", color = AvroraColors.Text, fontSize = 12.sp)
        Slider(value = width, onValueChange = onWidth, valueRange = 2f..80f)
    }
}

@Composable
private fun SwatchRow(selected: Color, onPick: (Color) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())
    ) {
        Swatches.forEach { c ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (c == selected) 3.dp else 1.dp,
                        color = if (c == selected) AvroraColors.Text else AvroraColors.Panel,
                        shape = CircleShape
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

@Composable
private fun LayersPanel(doc: Document) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            OutlinedButton(onClick = { doc.addLayer() }) { Text("+ Слой") }
            OutlinedButton(onClick = { doc.duplicateActive() }) { Text("Копия") }
            OutlinedButton(onClick = { doc.removeActive() }) { Text("Удалить") }
            OutlinedButton(onClick = { doc.moveActive(1) }) { Text("Вверх") }
            OutlinedButton(onClick = { doc.moveActive(-1) }) { Text("Вниз") }
        }

        doc.active?.let { layer ->
            Text(
                "Прозрачность слоя: ${(layer.opacity * 100).toInt()}%",
                color = AvroraColors.Text,
                fontSize = 12.sp
            )
            Slider(
                value = layer.opacity,
                onValueChange = { layer.opacity = it },
                valueRange = 0.05f..1f
            )
        }

        // Список слоёв: верхний слой показываем первым
        for (i in doc.layers.indices.reversed()) {
            val layer = doc.layers[i]
            val isActive = i == doc.activeIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(if (isActive) AvroraColors.Violet.copy(alpha = 0.35f) else AvroraColors.Panel)
                    .clickable { doc.activeIndex = i }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(layer.name, color = AvroraColors.Text, modifier = Modifier.weight(1f))
                TextButton(onClick = { layer.visible = !layer.visible }) {
                    Text(if (layer.visible) "Видим" else "Скрыт", color = AvroraColors.Fluorite)
                }
            }
        }
    }
}

@Composable
private fun TemplatesPanel(onPick: (Template) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Templates.forEach { t ->
            HudTile(
                title = t.name,
                caption = t.size,
                selected = false,
                onClick = { onPick(t) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            )
        }
    }
}

private fun Tool.label(): String = when (this) {
    Tool.PEN -> "Перо"
    Tool.SOFT -> "Мягкая"
    Tool.MARKER -> "Маркер"
    Tool.ERASER -> "Ластик"
    Tool.GRADIENT -> "Градиент"
}

private fun saveImage(context: Context, doc: Document): String {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
    dir.mkdirs()
    val file = File(dir, "avrora_${System.currentTimeMillis()}.png")
    val bitmap = doc.flatten()
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file.absolutePath
}
