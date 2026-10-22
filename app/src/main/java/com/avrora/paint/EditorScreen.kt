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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
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

private enum class Sheet { NONE, TOOLS, COLOR, LAYERS, FILTERS }

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

    fun startTransform() {
        sheet = Sheet.NONE
        if (doc.beginTransform()) {
            editor.flash("Один палец — сдвиг, два — масштаб и поворот")
        } else {
            editor.flash("Нечего трансформировать")
        }
    }

    fun paste() {
        sheet = Sheet.NONE
        if (editor.paste(doc)) editor.flash("Вставлено — поставьте на место")
    }

    BackHandler {
        when {
            doc.floating != null -> doc.cancelTransform()
            sheet != Sheet.NONE -> close()
            else -> onExit()
        }
    }

    // Приложение ушло в фон — завершаем трансформацию и сохраняем работу.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (doc.floating != null) doc.applyTransform()
        if (doc.dirty) scope.launch { ProjectStore.save(context, doc) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = decodeSampled(context, uri, max(doc.width, doc.height))
            if (bmp != null) {
                sheet = Sheet.NONE
                doc.importImage(bmp)
                bmp.recycle()
                editor.flash("Поставьте фото на место и нажмите «Готово»")
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
                IconBtn(Ico.BACK) {
                    if (doc.floating != null) doc.applyTransform()
                    onExit()
                }
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
                        if (editor.hasClipboard) {
                            MenuItem("Вставить", Ico.PASTE) {
                                menuOpen = false
                                paste()
                            }
                        }
                        MenuItem("Выделить всё", Ico.SELECT_ALL) {
                            menuOpen = false
                            doc.selectAll()
                        }
                        MenuItem("Трансформировать слой", Ico.TRANSFORM) {
                            menuOpen = false
                            startTransform()
                        }
                        MenuItem("Эффекты и коррекция", Ico.FX) {
                            menuOpen = false
                            open(Sheet.FILTERS)
                        }
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
                    visible = sheet == Sheet.NONE && doc.floating == null,
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

                // Полоса действий с выделением
                androidx.compose.animation.AnimatedVisibility(
                    visible = doc.floating == null && sheet == Sheet.NONE &&
                        (doc.hasSelection || editor.tool.isSelect),
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it }
                ) {
                    SelectionBar(doc, editor, onTransform = { startTransform() }, onPaste = { paste() })
                }

                FlashMessage(editor, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))

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
                            Sheet.TOOLS -> ToolsSheet(
                                editor,
                                onTransform = { startTransform() },
                                onFilters = { open(Sheet.FILTERS) },
                                onClose = { close() }
                            )
                            Sheet.FILTERS -> FiltersSheet(doc, editor) { close() }
                            Sheet.COLOR -> ColorSheet(editor) { close() }
                            Sheet.LAYERS -> LayersSheet(doc, editor, onImport = pickPhoto, onClose = { close() })
                            Sheet.NONE -> Unit
                        }
                    }
                }
            }

            // ---------- Нижняя панель ----------
            if (doc.floating != null) {
                TransformBar(doc, editor)
            } else Row(
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
    IconBtn(Ico.UNDO, enabled = doc.canUndo && doc.floating == null) {
        if (doc.undo()) editor.flash("↶  Отмена")
    }
    IconBtn(Ico.REDO, enabled = doc.canRedo && doc.floating == null) {
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
            delay(700L + editor.flashText.length * 30L)
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

// Полоса действий с выделением над холстом.
@Composable
private fun SelectionBar(doc: Document, editor: EditorState, onTransform: () -> Unit, onPaste: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(20.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Режим: новые участки добавляются, вычитаются или заменяют старые.
        ModeChip(Ico.SEL_ADD, "Добавить", editor.selectMode == SelectMode.ADD) { editor.selectMode = SelectMode.ADD }
        ModeChip(Ico.SEL_SUB, "Убрать", editor.selectMode == SelectMode.SUBTRACT) { editor.selectMode = SelectMode.SUBTRACT }
        ModeChip(Ico.SEL_NEW, "Новое", editor.selectMode == SelectMode.REPLACE) { editor.selectMode = SelectMode.REPLACE }
        if (editor.tool != Tool.SELECT_WAND) {
            ChipAction(Ico.WAND, "Палочка") { editor.selectTool(Tool.SELECT_WAND) }
        }
        UndoChip(doc)
        if (doc.hasSelection) {
            ChipAction(Ico.COPY, "Копия") { if (editor.copy(doc)) editor.flash("Скопировано") }
            ChipAction(Ico.CUT, "Вырезать") { if (editor.cut(doc)) editor.flash("Вырезано") }
        }
        if (editor.hasClipboard) {
            ChipAction(Ico.PASTE, "Вставить") { onPaste() }
        }
        if (doc.hasSelection) {
            ChipAction(Ico.TRANSFORM, "Двигать") { onTransform() }
            ChipAction(Ico.FILL, "Залить") { doc.fillActive(editor.color.toArgb()) }
            ChipAction(Ico.CLEAR, "Стереть") { doc.clearActive() }
            ChipAction(Ico.INVERT, "Инверсия") { doc.invertSelection() }
            ChipAction(Ico.DESELECT, "Снять") { doc.clearSelection() }
        } else {
            ChipAction(Ico.SELECT_ALL, "Всё") { doc.selectAll() }
        }
    }
}

// Переключатель режима выделения: выбранный подсвечен.
@Composable
private fun ModeChip(icon: Ico, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) AvroraColors.Fluorite else AvroraColors.Text.copy(alpha = 0.7f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ActiveFill else androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvIcon(icon, tint, 20.dp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp, maxLines = 1)
    }
}

// Отмена прямо из полосы выделения (обновляется вместе с историей).
@Composable
private fun UndoChip(doc: Document) {
    @Suppress("UNUSED_VARIABLE")
    val version = doc.historyVersion
    if (doc.canUndo) {
        ChipAction(Ico.UNDO, "Назад") { doc.undo() }
    }
}

@Composable
private fun ChipAction(icon: Ico, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvIcon(icon, AvroraColors.Text, 20.dp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = AvroraColors.Text.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1)
    }
}

// Нижняя панель в режиме трансформации.
@Composable
private fun TransformBar(doc: Document, editor: EditorState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarItem(Ico.CLOSE, "Отмена", false, Modifier.weight(1f)) {
            doc.cancelTransform()
            editor.flash("Отменено")
        }
        BarItem(Ico.FLIP, "Зеркало", false, Modifier.weight(1f)) { doc.flipFloating(true) }
        BarItem(Ico.FLIP_V, "Перевернуть", false, Modifier.weight(1f)) { doc.flipFloating(false) }
        BarItem(Ico.ROTATE, "90°", false, Modifier.weight(1f)) { doc.rotateFloating(90f) }
        BarItem(Ico.CHECK, "Готово", true, Modifier.weight(1f)) {
            doc.applyTransform()
            editor.flash("Готово")
        }
    }
}
