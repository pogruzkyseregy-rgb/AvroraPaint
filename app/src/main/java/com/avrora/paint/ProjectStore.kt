package com.avrora.paint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ProjectInfo(
    val id: String,
    val dir: File,
    val modified: Long,
    val width: Int,
    val height: Int,
    val layerCount: Int
) {
    val thumbFile: File get() = File(dir, "thumb.png")
}

// Хранилище работ: каждая работа — папка со слоями в PNG и файлом meta.json.
object ProjectStore {

    private val lock = Mutex()

    private class LayerCopy(
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val blend: String,
        val alphaLock: Boolean,
        val bitmap: Bitmap
    )

    private fun root(context: Context): File = File(context.filesDir, "projects").also { it.mkdirs() }

    // Вызывать с главного потока: снимаем копию, а пишем на диск в фоне.
    suspend fun save(context: Context, doc: Document) {
        val copies = doc.layers.map {
            LayerCopy(it.name, it.visible, it.opacity, it.blend.name, it.alphaLock, it.bitmap.copy(Bitmap.Config.ARGB_8888, false))
        }
        val thumb = doc.thumbnail(480)
        val active = doc.activeIndex
        val w = doc.width
        val h = doc.height
        val id = doc.id
        doc.dirty = false

        withContext(Dispatchers.IO) {
            lock.withLock {
                val dir = File(root(context), id).also { it.mkdirs() }
                dir.listFiles()?.filter { it.name.startsWith("layer_") }?.forEach { it.delete() }
                val arr = JSONArray()
                copies.forEachIndexed { i, c ->
                    val file = "layer_$i.png"
                    FileOutputStream(File(dir, file)).use { c.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    c.bitmap.recycle()
                    arr.put(
                        JSONObject()
                            .put("name", c.name)
                            .put("visible", c.visible)
                            .put("opacity", c.opacity.toDouble())
                            .put("blend", c.blend)
                            .put("alphaLock", c.alphaLock)
                            .put("file", file)
                    )
                }
                FileOutputStream(File(dir, "thumb.png")).use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
                thumb.recycle()
                val meta = JSONObject()
                    .put("width", w)
                    .put("height", h)
                    .put("active", active)
                    .put("modified", System.currentTimeMillis())
                    .put("layers", arr)
                val tmp = File(dir, "meta.tmp")
                tmp.writeText(meta.toString())
                tmp.renameTo(File(dir, "meta.json"))
            }
        }
    }

    suspend fun list(context: Context): List<ProjectInfo> = withContext(Dispatchers.IO) {
        val dirs = root(context).listFiles() ?: return@withContext emptyList()
        dirs.mapNotNull { dir ->
            val metaFile = File(dir, "meta.json")
            if (!metaFile.exists()) return@mapNotNull null
            runCatching {
                val m = JSONObject(metaFile.readText())
                ProjectInfo(
                    id = dir.name,
                    dir = dir,
                    modified = m.optLong("modified", dir.lastModified()),
                    width = m.getInt("width"),
                    height = m.getInt("height"),
                    layerCount = m.getJSONArray("layers").length()
                )
            }.getOrNull()
        }.sortedByDescending { it.modified }
    }

    suspend fun load(context: Context, id: String): Document? {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(root(context), id)
                val m = JSONObject(File(dir, "meta.json").readText())
                val arr = m.getJSONArray("layers")
                val opts = BitmapFactory.Options().apply {
                    inMutable = true
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val layers = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val bmp = BitmapFactory.decodeFile(File(dir, o.getString("file")).absolutePath, opts)
                        ?: error("layer $i")
                    LayerCopy(
                        o.optString("name", "Слой ${i + 1}"),
                        o.optBoolean("visible", true),
                        o.optDouble("opacity", 1.0).toFloat(),
                        o.optString("blend", "NORMAL"),
                        o.optBoolean("alphaLock", false),
                        bmp
                    )
                }
                m to layers
            }.getOrNull()
        } ?: return null

        val (m, layers) = loaded
        if (layers.isEmpty()) return null
        val doc = Document(m.getInt("width"), m.getInt("height"), id)
        for (c in layers) {
            val layer = doc.addLayerFromBitmap(c.name, c.bitmap)
            layer.visible = c.visible
            layer.opacity = c.opacity
            layer.blend = runCatching { LayerBlend.valueOf(c.blend) }.getOrDefault(LayerBlend.NORMAL)
            layer.alphaLock = c.alphaLock
        }
        doc.setActive(m.optInt("active", layers.lastIndex))
        doc.dirty = false
        return doc
    }

    suspend fun delete(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            lock.withLock { File(root(context), id).deleteRecursively() }
        }
    }
}
