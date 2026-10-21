package com.avrora.paint

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Шаблон — это готовый размер холста и фон с градиентом.
data class Template(
    val name: String,
    val size: String,
    val width: Int,
    val height: Int,
    val top: Int,
    val bottom: Int
) {
    fun createDocument(): Document {
        val doc = Document(width, height)
        val bg = doc.addLayer("Фон")
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        Canvas(bg.bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        doc.addLayer("Слой 1")
        return doc
    }
}

val Templates = listOf(
    Template("Квадрат", "1080 × 1080", 1080, 1080, AvroraColors.Black.toArgb(), Color(0xFF2A1450).toArgb()),
    Template("Сторис", "1080 × 1920", 1080, 1920, AvroraColors.Black.toArgb(), Color(0xFF0F3A46).toArgb()),
    Template("Обои", "1080 × 2400", 1080, 2400, Color(0xFF12121C).toArgb(), Color(0xFF3B1F7A).toArgb()),
    Template("Чистый лист", "1200 × 1600", 1200, 1600, Color.White.toArgb(), Color.White.toArgb())
)
