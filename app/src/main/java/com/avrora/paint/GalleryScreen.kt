package com.avrora.paint

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

// Стартовый экран: мои работы и создание нового холста.
@Composable
fun GalleryScreen(refreshKey: Int, onNew: (Template) -> Unit, onOpen: (ProjectInfo) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val projects by produceState(initialValue = emptyList<ProjectInfo>(), refreshKey, reload) {
        value = ProjectStore.list(context)
    }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ProjectInfo?>(null) }

    Box(Modifier.fillMaxSize().auroraBackground()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().systemBarsPadding()
        ) {
            item(span = { GridItemSpan(2) }) {
                Header(projects.size)
            }
            item {
                NewCard { showNew = true }
            }
            items(projects, key = { it.id }) { info ->
                ProjectCard(info, onOpen = { onOpen(info) }, onDelete = { toDelete = info })
            }
            if (projects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.82f).padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Здесь будут ваши работы.\nОни сохраняются сами.",
                            color = AvroraColors.Text.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showNew) {
        NewCanvasDialog(
            onDismiss = { showNew = false },
            onPick = {
                showNew = false
                onNew(it)
            }
        )
    }

    toDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { AvIcon(Ico.TRASH, AvroraColors.Pink, 28.dp) },
            title = { Text("Удалить работу?") },
            text = { Text("Восстановить её будет нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        ProjectStore.delete(context, info.id)
                        toDelete = null
                        reload++
                    }
                }) { Text("Удалить", color = AvroraColors.Pink) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun Header(count: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp, 120.dp)) {
                drawCircle(
                    Brush.radialGradient(listOf(AvroraColors.Violet.copy(alpha = 0.35f), Color.Transparent)),
                    radius = size.width / 2f
                )
            }
            Image(
                painter = painterResource(R.drawable.avrora_logo),
                contentDescription = "Avrora",
                modifier = Modifier.size(width = 130.dp, height = 97.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "AVRORA PAINT",
            color = AvroraColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = 6.sp
        )
        Text(
            if (count == 0) "НАЧНИТЕ ПЕРВУЮ РАБОТУ" else "МОИ РАБОТЫ · $count",
            color = AvroraColors.Violet,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun NewCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(shape)
            .background(ActiveFill)
            .border(1.5.dp, AccentBorder, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AvroraColors.Black.copy(alpha = 0.5f))
                .border(1.dp, AccentBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AvIcon(Ico.PLUS, AvroraColors.Fluorite, 30.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Новый холст", color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("шаблон или свой размер", color = AvroraColors.Text.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun ProjectCard(info: ProjectInfo, onOpen: () -> Unit, onDelete: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(initialValue = null, info.id, info.modified) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(info.thumbFile.absolutePath)?.asImageBitmap()
        }
    }
    val date = remember(info.modified) {
        SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(info.modified))
    }
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .glass(shape, alpha = 0.8f)
            .clickable(onClick = onOpen)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF14131F)),
            contentAlignment = Alignment.Center
        ) {
            thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AvroraColors.Black.copy(alpha = 0.7f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                AvIcon(Ico.TRASH, AvroraColors.Pink, 18.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(date, color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "${info.width}×${info.height} · слоёв ${info.layerCount}",
            color = AvroraColors.Violet,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun NewCanvasDialog(onDismiss: () -> Unit, onPick: (Template) -> Unit) {
    var w by remember { mutableStateOf("1080") }
    var h by remember { mutableStateOf("1350") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .glass(RoundedCornerShape(26.dp), alpha = 0.97f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SheetTitle("НОВЫЙ ХОЛСТ") }
                IconBtn(Ico.CLOSE) { onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
            Templates.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    row.forEach { t ->
                        TemplateCard(t, Modifier.weight(1f)) { onPick(t) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SectionLabel("СВОЙ РАЗМЕР · ОТ 64 ДО 2400")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = w,
                    onValueChange = { v -> w = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Ширина") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = h,
                    onValueChange = { v -> h = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Высота") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            SmallAction("Создать свой холст", Ico.PLUS) {
                val ww = (w.toIntOrNull() ?: 1080).coerceIn(64, 2400)
                val hh = (h.toIntOrNull() ?: 1350).coerceIn(64, 2400)
                onPick(customTemplate(ww, hh))
            }
        }
    }
}

// Карточка шаблона с превью пропорций и фона.
@Composable
private fun TemplateCard(t: Template, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(AvroraColors.Black.copy(alpha = 0.5f))
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val k = minOf(size.width / t.width, size.height / t.height)
            val rw = t.width * k
            val rh = t.height * k
            val tl = Offset((size.width - rw) / 2f, (size.height - rh) / 2f)
            if (t.top != null && t.bottom != null) {
                drawRect(
                    Brush.verticalGradient(listOf(Color(t.top), Color(t.bottom)), startY = tl.y, endY = tl.y + rh),
                    topLeft = tl,
                    size = Size(rw, rh)
                )
            } else {
                val cell = max(4f, rw / 8f)
                drawRect(Color(0xFFE8E8EE), topLeft = tl, size = Size(rw, rh))
                var y = 0
                while (y * cell < rh) {
                    var x = if (y % 2 == 0) 0 else 1
                    while (x * cell < rw) {
                        drawRect(
                            Color(0xFFC4C4D0),
                            topLeft = Offset(tl.x + x * cell, tl.y + y * cell),
                            size = Size(minOf(cell, rw - x * cell), minOf(cell, rh - y * cell))
                        )
                        x += 2
                    }
                    y++
                }
            }
            drawRect(AvroraColors.Fluorite.copy(alpha = 0.6f), topLeft = tl, size = Size(rw, rh), style = Stroke(width = 1.dp.toPx()))
        }
        Spacer(Modifier.height(8.dp))
        Text(t.name, color = AvroraColors.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(t.size, color = AvroraColors.Violet, fontSize = 10.sp)
    }
}
