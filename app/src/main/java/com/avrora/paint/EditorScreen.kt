package com.avrora.paint

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

private enum class Sheet { NONE, TOOLS, COLOR, LAYERS }

// Размер кисти: ползунок нелинейный, чтобы тонкие размеры было легко выставить.
private const val MIN_SIZE = 1f
private const val MAX_SIZE = 200f
private fun sizeToFraction(v: Float): Float = sqrt(((v - MIN_SIZE) / (MAX_SIZE - MIN_SIZE)).coerceIn(0f, 1f))
private fun fractionToSize(f: Float): Float = MIN_SIZE + (MAX_SIZE - MIN_SIZE) * f * f

@Composable
fun EditorScreen(doc: Document, editor: EditorState, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = remember(doc) { ViewState() }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(firstLaunch(context)) }

    fun toggle(s: Sheet) {
        sheet = if (sheet == s) Sheet.NONE else s
    }

    BackHandler {
        if (sheet != Sheet.NONE) sheet = Sheet.NONE else onExit()
    }

    // Приложение ушло в фон — сохраняем работу, чтобы ничего не потерять.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (doc.dirty) scope.launch { ProjectStore.save(context, doc) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = decodeSampled(context, uri, max(doc.width, doc.height))
            if (bmp != null) {
                doc.importImage(bmp)
                bmp.recycle()
                sheet = Sheet.LAYERS
            } else {
                Toast.makeText(context, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Читаем счётчик, чтобы кнопки отмены/повтора обновлялись.
    @Suppress("UNUSED_VARIABLE")
    val rev = doc.revision

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AvroraColors.Black)
            .systemBarsPadding()
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudButton("◄") { onExit() }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text("AVRORA PAINT", color = AvroraColors.Fluorite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                val flip = if (view.flipped) " · ОТРАЖЕНО" else ""
                Text(
                    "${doc.width}×${doc.height} · ${editor.tool.label}$flip",
                    color = AvroraColors.Violet,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            HudButton("↶", enabled = doc.canUndo) { doc.undo() }
            HudButton("↷", enabled = doc.canRedo) { doc.redo() }
            Box {
                HudButton("⋮") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Вписать холст в экран") }, onClick = {
                        menuOpen = false
                        view.fit(doc)
                    })
                    DropdownMenuItem(text = { Text(if (view.flipped) "Убрать отражение" else "Отразить холст") }, onClick = {
                        menuOpen = false
                        view.flipped = !view.flipped
                    })
                    DropdownMenuItem(text = { Text("Вставить фото как слой") }, onClick = {
                        menuOpen = false
                        importLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    DropdownMenuItem(text = { Text("Сохранить PNG в галерею") }, onClick = {
                        menuOpen = false
                        exportPng(context, doc)
                    })
                    DropdownMenuItem(text = { Text("Сохранить работу") }, onClick = {
                        menuOpen = false
                        scope.launch {
                            ProjectStore.save(context, doc)
                            Toast.makeText(context, "Работа сохранена", Toast.LENGTH_SHORT).show()
                        }
                    })
                    DropdownMenuItem(text = { Text("Жесты и подсказки") }, onClick = {
                        menuOpen = false
                        showHelp = true
                    })
                }
            }
        }

        // Холст с ползунками и выдвижными панелями
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            HudFrame(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                DocumentCanvas(doc = doc, view = view, editor = editor, modifier = Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VerticalSlider(
                    caption = "РАЗМЕР",
                    value = editor.size.toInt().toString(),
                    fraction = sizeToFraction(editor.size),
                    onFraction = { editor.size = fractionToSize(it) }
                )
                VerticalSlider(
                    caption = "НЕПРОЗР.",
                    value = "${(editor.opacity * 100).toInt()}%",
                    fraction = editor.opacity,
                    onFraction = { editor.opacity = it.coerceIn(0.02f, 1f) }
                )
            }

            if (sheet != Sheet.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .background(AvroraColors.Panel.copy(alpha = 0.97f))
                        .border(1.dp, AvroraColors.Violet)
                ) {
                    when (sheet) {
                        Sheet.TOOLS -> ToolsSheet(editor)
                        Sheet.COLOR -> ColorSheet(editor)
                        Sheet.LAYERS -> LayersSheet(doc, editor) {
                            importLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Sheet.NONE -> Unit
                    }
                }
            }
        }

        // Нижняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AvroraColors.Panel)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudTile("ИНСТР.", editor.tool.label, sheet == Sheet.TOOLS, { toggle(Sheet.TOOLS) }, Modifier.weight(1f))
            HudTile(
                "КИСТЬ",
                editor.lastDrawTool.label,
                editor.tool.isBrush && editor.tool != Tool.ERASER,
                { editor.selectTool(editor.lastDrawTool) },
                Modifier.weight(1f)
            )
            HudTile(
                "ЛАСТИК",
                "${editor.eraserSize.toInt()} px",
                editor.tool == Tool.ERASER,
                { editor.selectTool(Tool.ERASER) },
                Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(editor.color)
                    .border(
                        width = if (sheet == Sheet.COLOR) 3.dp else 2.dp,
                        color = if (sheet == Sheet.COLOR) AvroraColors.Fluorite else AvroraColors.Text,
                        shape = CircleShape
                    )
                    .clickable { toggle(Sheet.COLOR) }
            )
            HudTile(
                "СЛОИ",
                "${doc.activeIndex + 1} / ${doc.layers.size}",
                sheet == Sheet.LAYERS,
                { toggle(Sheet.LAYERS) },
                Modifier.weight(1f)
            )
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Понятно") } },
            title = { Text("Жесты") },
            text = {
                Text(
                    "• Один палец — рисовать\n" +
                        "• Два пальца — приблизить, отдалить, сдвинуть холст\n" +
                        "• Касание двумя пальцами — отменить шаг\n" +
                        "• Касание тремя пальцами — вернуть шаг\n" +
                        "• Удерживать палец на месте — пипетка, можно водить по холсту\n" +
                        "• Слева ползунки размера и непрозрачности\n" +
                        "• Кнопки КИСТЬ и ЛАСТИК — быстрое переключение\n" +
                        "• Работа сохраняется сама, когда вы выходите из приложения"
                )
            }
        )
    }
}

private fun exportPng(context: Context, doc: Document) {
    val bitmap = doc.flatten()
    val uri = saveToGallery(context, bitmap, "avrora_${System.currentTimeMillis()}")
    bitmap.recycle()
    val text = if (uri != null) "Сохранено в галерею: Pictures/AvroraPaint" else "Не удалось сохранить"
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

// Подсказки показываем один раз — при первом открытии редактора.
private fun firstLaunch(context: Context): Boolean {
    val prefs = context.getSharedPreferences("avrora_paint", Context.MODE_PRIVATE)
    val first = !prefs.getBoolean("help_shown", false)
    if (first) prefs.edit().putBoolean("help_shown", true).apply()
    return first
}
