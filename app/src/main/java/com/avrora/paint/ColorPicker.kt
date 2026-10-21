package com.avrora.paint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Выбор цвета по оттенку, насыщенности и яркости.
@Composable
fun ColorPicker(initial: Color, onChange: (Color) -> Unit) {
    val start = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var bright by remember { mutableFloatStateOf(start[2]) }

    fun emit() {
        onChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bright))))
    }

    val preview = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bright)))

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(preview)
                .border(1.dp, AvroraColors.Text)
        )
        Text("Оттенок: ${hue.toInt()}°", color = AvroraColors.Text, fontSize = 12.sp)
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                emit()
            },
            valueRange = 0f..360f
        )
        Text("Насыщенность: ${(sat * 100).toInt()}%", color = AvroraColors.Text, fontSize = 12.sp)
        Slider(
            value = sat,
            onValueChange = {
                sat = it
                emit()
            },
            valueRange = 0f..1f
        )
        Text("Яркость: ${(bright * 100).toInt()}%", color = AvroraColors.Text, fontSize = 12.sp)
        Slider(
            value = bright,
            onValueChange = {
                bright = it
                emit()
            },
            valueRange = 0f..1f
        )
    }
}
