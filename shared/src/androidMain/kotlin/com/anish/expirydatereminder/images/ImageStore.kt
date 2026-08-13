package com.anish.expirydatereminder.images

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Item photos live in `filesDir/images/`.
 *
 * The pre-2.0 app kept them in `cacheDir`, which Android is free to evict under storage
 * pressure, and derived each filename from the item's name, date and category. That meant
 * editing an item orphaned its photo, and any name containing `/` produced a broken path.
 * Filenames here are opaque and the path is stored on the item row instead.
 */
class ImageStore(private val context: Context) {
    private val root: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun fileFor(relativePath: String): File = File(context.filesDir, relativePath)

    fun exists(relativePath: String): Boolean = fileFor(relativePath).isFile

    /** @return the item-relative path to store on the row, or null if writing failed. */
    fun write(itemId: Long, source: InputStream): String? = runCatching {
        val target = File(root, fileName(itemId))
        target.outputStream().use { out -> source.copyTo(out) }
        "$DIRECTORY/${target.name}"
    }.getOrNull()

    fun write(itemId: Long, bytes: ByteArray): String? = write(itemId, bytes.inputStream())

    fun copyFrom(itemId: Long, source: File): String? = runCatching {
        val target = File(root, fileName(itemId))
        source.copyTo(target, overwrite = true)
        "$DIRECTORY/${target.name}"
    }.getOrNull()

    fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        runCatching { fileFor(relativePath).delete() }
    }

    /** Removes files no longer referenced by any item row. */
    fun pruneOrphans(referenced: Collection<String>) {
        val keep = referenced.map { File(context.filesDir, it).name }.toSet()
        root.listFiles()?.forEach { file -> if (file.name !in keep) file.delete() }
    }

    private fun fileName(itemId: Long) = "item_$itemId.jpg"

    companion object {
        const val DIRECTORY = "images"
    }
}
