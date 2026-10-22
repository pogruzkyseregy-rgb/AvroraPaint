package com.avrora.paint

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
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
    // Последняя открытая панель — чтобы она красиво уезжала при закрытии.
    var lastSheet by remember { mutableStateOf(Sheet.TOOLS) }
    var menuOpen by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(firstLaunch(context)) }

    fun open(s: Sheet) {
        if (sheet == s) {
            sheet = Sheet.NONE
        } else {
            sheet = s
            lastSheet = s
        }
    }

    fun close() {
        sheet = Sheet.NONE
    }

    BackHandler {
        if (sheet != Sheet.NONE) close() else onExit()
    }

    // Приложение ушло в фон — сохраняем работу.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (doc.dirty) scope.launch { ProjectStore.save(context, doc) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = decodeSampled(context, uri, max(doc.width, doc.height))
            if (bmp != null) {
                doc.importImage(bmp)
                bmp.recycle()
                editor.flash("Фото добавлено новым слоем")
            } else {
                Toast.makeText(context, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickPhoto = {
        importLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(modifier = Modifier.fillMaxSize().auroraBackground()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // ---------- Верхняя панель ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .glass(RoundedCornerShape(22.dp))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Ico.BACK) { onExit() }
                Image(
                    painter = painterResource(R.drawable.avrora_logo),
                    contentDescription = "Avrora",
                    modifier = Modifier.size(width = 34.dp, height = 25.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "AVRORA PAINT",
                        color = AvroraColors.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp
                    )
                    val flip = if (view.flipped) " · отражено" else ""
                    Text(
                        "${doc.width}×${doc.height}$flip",
                        color = AvroraColors.Violet,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                UndoRedo(doc, editor)
                Box {
                    IconBtn(Ico.MORE) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem("Вписать холст в экран", Ico.FIT) {
                            menuOpen = false
                            view.fit(doc)
                        }
                        MenuItem(if (view.flipped) "Убрать отражение" else "Отразить холст", Ico.FLIP) {
                            menuOpen = false
                            view.flipped = !view.flipped
                        }
                        MenuItem("Вставить фото как слой", Ico.PHOTO) {
                            menuOpen = false
                            pickPhoto()
                        }
                        MenuItem("Сохранить PNG в галерею", Ico.EXPORT) {
                            menuOpen = false
                            if (exportPng(context, doc)) editor.flash("Сохранено в галерею") else editor.flash("Не удалось сохранить")
                        }
                        MenuItem("Сохранить работу", Ico.SAVE) {
                            menuOpen = false
                            scope.launch {
                                ProjectStore.save(context, doc)
                                editor.flash("Работа сохранена")
                            }
                        }
                        MenuItem("Жесты и подсказки", Ico.HELP) {
                            menuOpen = false
                            showHelp = true
                        }
                    }
                }
            }

            // ---------- Холст ----------
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                DocumentCanvas(
                    doc = doc,
                    view = view,
                    editor = editor,
                    modifier = Modifier.fillMaxSize(),
                    onTouch = { if (sheet != Sheet.NONE) close() }
                )

                // Ползунки размера и непрозрачности
                androidx.compose.animation.AnimatedVisibility(
                    visible = sheet == Sheet.NONE,
                    modifier = Modifier.align(Alignment.CenterStart),
                    enter = fadeIn() + slideInHorizontally { -it },
                    exit = fadeOut() + slideOutHorizontally { -it }
                ) {
                    Column(
                        modifier = Modifier.padding(start = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VerticalSlider(
                            caption = "РАЗМЕР",
                            value = editor.size.toInt().toString(),
                            fraction = sizeToFraction(editor.size),
                            onFraction = { editor.size = fractionToSize(it) },
                            onActive = { editor.sizePreview = it }
                        )
                        VerticalSlider(
                            caption = "НЕПРОЗР.",
                            value = "${(editor.opacity * 100).toInt()}%",
                            fraction = editor.opacity,
                            onFraction = { editor.opacity = it.coerceIn(0.02f, 1f) }
                        )
                    }
                }

                FlashMessage(editor, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))

                // Выдвижные панели
                androidx.compose.animation.AnimatedVisibility(
                    visible = sheet != Sheet.NONE,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    val shown = if (sheet != Sheet.NONE) sheet else lastSheet
                    Box(Modifier.padding(horizontal = 6.dp)) {
                        when (shown) {
                            Sheet.TOOLS -> ToolsSheet(editor) { close() }
                            Sheet.COLOR -> ColorSheet(editor) { close() }
                            Sheet.LAYERS -> LayersSheet(doc, editor, onImport = pickPhoto, onClose = { close() })
                            Sheet.NONE -> Unit
                        }
                    }
                }
            }

            // ---------- Нижняя панель ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .glass(RoundedCornerShape(26.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarItem(editor.tool.icon(), editor.tool.label, sheet == Sheet.TOOLS, Modifier.weight(1f)) {
                    open(Sheet.TOOLS)
                }
                val brushActive = editor.tool.isBrush && editor.tool != Tool.ERASER
                BarItem(editor.lastDrawTool.icon(), "Кисть", brushActive, Modifier.weight(1f)) {
                    // Повторное нажатие на активную кисть открывает выбор кистей.
                    if (brushActive) open(Sheet.TOOLS) else editor.selectTool(editor.lastDrawTool)
                }
                ColorOrb(
                    color = editor.color,
                    second = if (editor.tool == Tool.GRADIENT) editor.secondColor else null,
                    selected = sheet == Sheet.COLOR
                ) { open(Sheet.COLOR) }
                BarItem(Ico.ERASER, "Ластик", editor.tool == Tool.ERASER, Modifier.weight(1f)) {
                    editor.selectTool(Tool.ERASER)
                }
                BarItem(Ico.LAYERS, "Слои ${doc.activeIndex + 1}/${doc.layers.size}", sheet == Sheet.LAYERS, Modifier.weight(1f)) {
                    open(Sheet.LAYERS)
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Понятно", color = AvroraColors.Fluorite) } },
            icon = {
                Image(
                    painter = painterResource(R.drawable.avrora_logo),
                    contentDescription = null,
                    modifier = Modifier.size(width = 64.dp, height = 48.dp)
                )
            },
            title = { Text("Как рисовать") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpLine(Ico.PEN, "Один палец — рисовать")
                    HelpLine(Ico.FIT, "Два пальца — приблизить и сдвинуть холст")
                    HelpLine(Ico.UNDO, "Касание двумя пальцами — отменить")
                    HelpLine(Ico.REDO, "Касание тремя пальцами — вернуть")
                    HelpLine(Ico.PICKER, "Удержать палец — пипетка")
                    HelpLine(Ico.LAYERS, "Слева — размер и непрозрачность кисти")
                    HelpLine(Ico.SAVE, "Работа сохраняется сама")
                }
            }
        )
    }
}

@Composable
private fun UndoRedo(doc: Document, editor: EditorState) {
    // Обновляется только когда меняется история, а не на каждое движение пальца.
    @Suppress("UNUSED_VARIABLE")
    val version = doc.historyVersion
    IconBtn(Ico.UNDO, enabled = doc.canUndo) {
        if (doc.undo()) editor.flash("↶  Отмена")
    }
    IconBtn(Ico.REDO, enabled = doc.canRedo) {
        if (doc.redo()) editor.flash("↷  Повтор")
    }
}

@Composable
private fun MenuItem(text: String, icon: Ico, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = AvroraColors.Text) },
        leadingIcon = { AvIcon(icon, AvroraColors.Fluorite, 20.dp) },
        onClick = onClick
    )
}

@Composable
private fun HelpLine(icon: Ico, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvIcon(icon, AvroraColors.Fluorite, 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp)
    }
}

// Короткое уведомление, которое само исчезает.
@Composable
private fun FlashMessage(editor: EditorState, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(editor.flashId) {
        if (editor.flashId > 0) {
            visible = true
            delay(800)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .glass(RoundedCornerShape(20.dp), alpha = 0.92f)
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(editor.flashText, color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun exportPng(context: Context, doc: Document): Boolean {
    val bitmap = doc.flatten()
    val uri = saveToGallery(context, bitmap, "avrora_${System.currentTimeMillis()}")
    bitmap.recycle()
    return uri != null
}

// Подсказки показываем один раз — при первом открытии редактора.
private fun firstLaunch(context: Context): Boolean {
    val prefs = context.getSharedPreferences("avrora_paint", Context.MODE_PRIVATE)
    val first = !prefs.getBoolean("help_shown_v2", false)
    if (first) prefs.edit().putBoolean("help_shown_v2", true).apply()
    return first
}
