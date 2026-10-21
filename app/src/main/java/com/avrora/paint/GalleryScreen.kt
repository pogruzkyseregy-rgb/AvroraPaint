package com.avrora.paint

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AvroraColors.Black)
            .systemBarsPadding()
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AVRORA PAINT", color = AvroraColors.Fluorite, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("МОИ РАБОТЫ · ${projects.size}", color = AvroraColors.Violet, fontSize = 11.sp)
            }
            HudTile("+ НОВЫЙ", "создать холст", true, { showNew = true }, Modifier.width(130.dp))
        }
        Spacer(Modifier.height(12.dp))

        HudFrame(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (projects.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Здесь пока пусто.\nНажмите «+ НОВЫЙ», чтобы начать рисовать.",
                        color = AvroraColors.Text,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.id }) { info ->
                        ProjectCard(info, onOpen = { onOpen(info) }, onDelete = { toDelete = info })
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
private fun ProjectCard(info: ProjectInfo, onOpen: () -> Unit, onDelete: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(initialValue = null, info.id, info.modified) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(info.thumbFile.absolutePath)?.asImageBitmap()
        }
    }
    val date = remember(info.modified) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(info.modified))
    }

    Column(
        modifier = Modifier
            .background(AvroraColors.Panel)
            .border(1.dp, AvroraColors.Violet)
            .clickable(onClick = onOpen)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF1A1A26)),
            contentAlignment = Alignment.Center
        ) {
            thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
                    .background(AvroraColors.Black.copy(alpha = 0.75f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = AvroraColors.Pink, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${info.width}×${info.height} · слоёв: ${info.layerCount}", color = AvroraColors.Text, fontSize = 11.sp)
        Text(date, color = AvroraColors.Violet, fontSize = 10.sp)
    }
}

@Composable
private fun NewCanvasDialog(onDismiss: () -> Unit, onPick: (Template) -> Unit) {
    var w by remember { mutableStateOf("1080") }
    var h by remember { mutableStateOf("1350") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text("Новый холст") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Templates.forEach { t ->
                    HudTile(
                        title = t.name,
                        caption = t.size,
                        selected = false,
                        onClick = { onPick(t) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    )
                }
                SectionLabel("СВОЙ РАЗМЕР (64–2400)")
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
                Spacer(Modifier.height(8.dp))
                SmallAction("СОЗДАТЬ СВОЙ") {
                    val ww = (w.toIntOrNull() ?: 1080).coerceIn(64, 2400)
                    val hh = (h.toIntOrNull() ?: 1350).coerceIn(64, 2400)
                    onPick(customTemplate(ww, hh))
                }
            }
        }
    )
}
