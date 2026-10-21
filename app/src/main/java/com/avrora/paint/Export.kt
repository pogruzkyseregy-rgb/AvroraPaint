package com.avrora.paint

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

// Сохраняем PNG в галерею: папка Pictures/AvroraPaint.
// Возвращает адрес файла в MediaStore или null, если не получилось.
fun saveToGallery(context: Context, bitmap: Bitmap, baseName: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AvroraPaint")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "AvroraPaint"
            )
            dir.mkdirs()
            put(MediaStore.Images.Media.DATA, File(dir, "$baseName.png").absolutePath)
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

    val ok = resolver.openOutputStream(uri)?.use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    } ?: false

    if (!ok) {
        resolver.delete(uri, null, null)
        return null
    }

    // На Android 10 и новее файл сначала «в ожидании», теперь делаем его видимым.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
    }
    return uri
}
