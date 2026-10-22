package com.avrora.paint

import android.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

// Настройки цветокоррекции. Значения по умолчанию ничего не меняют.
data class FilterParams(
    val brightness: Float = 0f,   // -1..1
    val contrast: Float = 1f,     // 0..2
    val saturation: Float = 1f,   // 0..2
    val hue: Float = 0f,          // -180..180 градусов
    val invert: Boolean = false
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 1f && saturation == 1f && hue == 0f && !invert

    fun toColorMatrix(): ColorMatrix {
        val result = ColorMatrix()

        // Насыщенность
        result.postConcat(ColorMatrix().apply { setSaturation(saturation) })

        // Оттенок: поворот цвета вокруг серой оси
        if (hue != 0f) {
            val rad = Math.toRadians(hue.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val lr = 0.213f
            val lg = 0.715f
            val lb = 0.072f
            result.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
                        lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
                        lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        // Контраст и яркость
        val k = contrast
        val offset = 128f * (1f - k) + brightness * 255f
        result.postConcat(
            ColorMatrix(
                floatArrayOf(
                    k, 0f, 0f, 0f, offset,
                    0f, k, 0f, 0f, offset,
                    0f, 0f, k, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        // Инверсия
        if (invert) {
            result.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return result
    }

    // Та же матрица для предпросмотра в Compose.
    fun toComposeMatrix(): androidx.compose.ui.graphics.ColorMatrix =
        androidx.compose.ui.graphics.ColorMatrix(toColorMatrix().array.copyOf())
}
