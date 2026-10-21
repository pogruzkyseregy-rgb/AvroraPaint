package com.avrora.paint

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Шаблон: размер холста и фон. top/bottom == null — прозрачный фон.
data class Template(
    val name: String,
    val size: String,
    val width: Int,
    val height: Int,
    val top: Int?,
    val bottom: Int?
) {
    fun createDocument(): Document {
        val doc = Document(width, height)
        if (top != null && bottom != null) {
            val bg = doc.addLayer("Фон", record = false)
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            }
            Canvas(bg.bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        doc.addLayer("Слой 1", record = false)
        doc.dirty = false
        return doc
    }
}

private val White = Color.White.toArgb()

val Templates = listOf(
    Template("Квадрат", "1080 × 1080", 1080, 1080, AvroraColors.Black.toArgb(), Color(0xFF2A1450).toArgb()),
    Template("Сторис", "1080 × 1920", 1080, 1920, AvroraColors.Black.toArgb(), Color(0xFF0F3A46).toArgb()),
    Template("Обои", "1080 × 2400", 1080, 2400, Color(0xFF12121C).toArgb(), Color(0xFF3B1F7A).toArgb()),
    Template("Альбом", "1920 × 1080", 1920, 1080, AvroraColors.Black.toArgb(), Color(0xFF241046).toArgb()),
    Template("Белый лист", "1200 × 1600", 1200, 1600, White, White),
    Template("Прозрачный", "1080 × 1080", 1080, 1080, null, null)
)

fun customTemplate(w: Int, h: Int): Template = Template("Свой размер", "$w × $h", w, h, White, White)
