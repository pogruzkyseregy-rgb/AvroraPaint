package com.avrora.paint

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра Авроры: чёрный, флюорит-голубой, фиолетовый.
object AvroraColors {
    val Black = Color(0xFF07070B)
    val Panel = Color(0xFF12121C)
    val Fluorite = Color(0xFF4FD8FF)
    val Violet = Color(0xFF9B5CFF)
    val Pink = Color(0xFFFF4FD8)
    val Text = Color(0xFFE6E6F0)
}

private val AvroraScheme = darkColorScheme(
    background = AvroraColors.Black,
    surface = AvroraColors.Panel,
    primary = AvroraColors.Fluorite,
    secondary = AvroraColors.Violet,
    onPrimary = AvroraColors.Black,
    onBackground = AvroraColors.Text,
    onSurface = AvroraColors.Text
)

@Composable
fun AvroraPaintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AvroraScheme,
        content = content
    )
}
