package com.avrora.paint

import android.os.Build
import androidx.compose.ui.graphics.BlendMode

// Все инструменты рисования.
enum class Tool(val label: String, val caption: String) {
    PEN("Перо", "чёткая линия"),
    SOFT("Мягкая", "размытый край"),
    AIRBRUSH("Аэрограф", "напыление"),
    MARKER("Маркер", "полупрозрачный"),
    ERASER("Ластик", "стирание"),
    FILL("Заливка", "замкнутая область"),
    GRADIENT("Градиент", "два цвета"),
    PICKER("Пипетка", "взять цвет"),
    LINE("Линия", "прямая"),
    RECT("Прямоуг.", "прямоугольник"),
    OVAL("Овал", "эллипс"),
    SELECT_RECT("Рамка", "выделить прямоугольник"),
    SELECT_LASSO("Лассо", "выделить от руки"),
    SELECT_WAND("Палочка", "выделить одинаковое");

    val isBrush: Boolean
        get() = this == PEN || this == SOFT || this == AIRBRUSH || this == MARKER || this == ERASER

    val isShape: Boolean
        get() = this == LINE || this == RECT || this == OVAL

    val isSelect: Boolean
        get() = this == SELECT_RECT || this == SELECT_LASSO || this == SELECT_WAND
}

// Как новое выделение сочетается со старым.
enum class SelectMode(val label: String) {
    ADD("Добавить"),
    SUBTRACT("Убрать"),
    REPLACE("Новое")
}

// Режимы наложения слоёв.
enum class LayerBlend(val label: String) {
    NORMAL("Обычный"),
    MULTIPLY("Умножение"),
    SCREEN("Экран"),
    OVERLAY("Перекрытие"),
    DARKEN("Затемнение"),
    LIGHTEN("Осветление"),
    ADD("Добавление")
}

// Режимы наложения работают на Android 10 и новее.
val blendModesSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

fun LayerBlend.composeMode(): BlendMode {
    if (!blendModesSupported) return BlendMode.SrcOver
    return when (this) {
        LayerBlend.NORMAL -> BlendMode.SrcOver
        LayerBlend.MULTIPLY -> BlendMode.Multiply
        LayerBlend.SCREEN -> BlendMode.Screen
        LayerBlend.OVERLAY -> BlendMode.Overlay
        LayerBlend.DARKEN -> BlendMode.Darken
        LayerBlend.LIGHTEN -> BlendMode.Lighten
        LayerBlend.ADD -> BlendMode.Plus
    }
}

// Вызывать только когда blendModesSupported == true.
fun LayerBlend.androidMode(): android.graphics.BlendMode? = when (this) {
    LayerBlend.NORMAL -> null
    LayerBlend.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
    LayerBlend.SCREEN -> android.graphics.BlendMode.SCREEN
    LayerBlend.OVERLAY -> android.graphics.BlendMode.OVERLAY
    LayerBlend.DARKEN -> android.graphics.BlendMode.DARKEN
    LayerBlend.LIGHTEN -> android.graphics.BlendMode.LIGHTEN
    LayerBlend.ADD -> android.graphics.BlendMode.PLUS
}
