package com.avrora.paint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AvroraPaintTheme {
                EditorScreen()
            }
        }
    }
}

@Composable
fun EditorScreen() {
    val state = rememberDrawingState()
    val palette = listOf(Color.Black, AvroraColors.Fluorite, AvroraColors.Violet, AvroraColors.Pink)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AvroraColors.Black)
            .statusBarsPadding()
    ) {
        // Шапка
        Text(
            text = "AVRORA PAINT",
            color = AvroraColors.Fluorite,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        // Кнопки отмены и очистки
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Button(onClick = { state.undo() }) { Text("Undo") }
            OutlinedButton(onClick = { state.clear() }) { Text("Clear") }
        }

        // Палитра цветов
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            palette.forEach { color ->
                val selected = state.brushColor == color
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) AvroraColors.Text else AvroraColors.Panel,
                            shape = CircleShape
                        )
                        .clickable { state.brushColor = color }
                )
            }
        }

        // Толщина кисти
        Column(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Text("Толщина: ${state.brushWidth.toInt()}", color = AvroraColors.Text)
            Slider(
                value = state.brushWidth,
                onValueChange = { state.brushWidth = it },
                valueRange = 2f..40f
            )
        }

        // Холст занимает оставшееся место
        DrawingCanvas(
            state = state,
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        )
    }
}
