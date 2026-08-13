package com.anish.expirydatereminder.migration

import android.content.Context
import android.util.Log
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.images.ImageStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Moves pre-2.0 item photos out of `cacheDir` and onto item rows.
 *
 * Runs *after* [LegacyImporter] and deliberately outside its transaction. Filesystem
 * operations cannot participate in a SQLite transaction, so rather than pretend
 * otherwise this is best-effort and repeatable: an item whose photo cannot be copied ends
 * up with `imagePath = null`, exactly like an item that never had one. A photo failure
 * must never cost the user their data.
 */
class LegacyImageMigrator(
    private val context: Context,
    private val database: EdrDatabase,
    private val imageStore: ImageStore,
    private val io: CoroutineDispatcher,
) {
    suspend fun migrate(): Int = withContext(io) {
        val legacyDir = File(context.cacheDir, LEGACY_IMAGE_DIR)
        if (!legacyDir.isDirectory) return@withContext 0

        var migrated = 0
        val items = database.itemQueries.selectAll().executeAsList()
        for (item in items) {
            if (item.imagePath != null) continue
            val source =
                File(
                    legacyDir,
                    legacyFileName(
                        item.name,
                        item.expiryDay.toInt(),
                        item.expiryMonth.toInt(),
                        item.expiryYear.toInt(),
                        item.categoryName,
                    ),
                )
            if (!source.isFile) continue

            val stored = imageStore.copyFrom(item.id, source)
            if (stored == null) {
                Log.w(TAG, "Could not copy legacy image for item ${item.id}")
                continue
            }
            database.itemQueries.updateImagePath(
                imagePath = stored,
                updatedAt = System.currentTimeMillis(),
                id = item.id,
            )
            migrated++
        }
        migrated
    }

    companion object {
        private const val LEGACY_IMAGE_DIR = "images"
        private const val TAG = "LegacyImageMigrator"

        /**
         * Reproduces `FileUtils.getImageFileName` from the pre-2.0 app byte for byte,
         * including the unpadded integers. Do not "tidy" this: it is a lookup key for
         * files already sitting on users' devices.
         */
        fun legacyFileName(name: String, day: Int, month: Int, year: Int, category: String): String =
            "image_$name.$day.$month.$year.$category.jpg"
    }
}
