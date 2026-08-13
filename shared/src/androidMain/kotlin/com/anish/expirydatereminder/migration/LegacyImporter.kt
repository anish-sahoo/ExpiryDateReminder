package com.anish.expirydatereminder.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.anish.expirydatereminder.data.seedBuiltinCategories
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.model.BuiltinCategory
import com.anish.expirydatereminder.domain.model.MigrationSummary
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * One-time import of data written by the pre-2.0 app.
 *
 * ## Why this is written so defensively
 *
 * The legacy database is a single file named `itemsDatabase` (no extension) holding four
 * tables, written by four separate SQLiteOpenHelper subclasses that all declared
 * `DB_VERSION = 8`. Each one's `onUpgrade` does `DROP TABLE IF EXISTS <its own table>`,
 * so anything that opens that file as a SQLiteOpenHelper at a version other than 8
 * destroys user data one table at a time.
 *
 * This importer therefore:
 *  - copies the legacy file (and its WAL sidecars) to scratch space and reads the copy,
 *    so the original cannot be mutated under any failure mode;
 *  - writes everything inside one transaction, so a partial import is impossible;
 *  - treats the `migration_meta` row, written inside that same transaction, as the only
 *    "already migrated" signal. File existence is NOT a valid guard, because SQLDelight
 *    creates the 2.0 database file on open, before any data lands in it;
 *  - renames the legacy file aside only after a successful commit, leaving it fully
 *    intact and re-importable if anything went wrong.
 */
class LegacyImporter(
    private val context: Context,
    private val database: EdrDatabase,
    private val io: CoroutineDispatcher,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    sealed interface Result {
        /** No legacy database present; this is a clean install. */
        data object NothingToImport : Result

        /** A `migration_meta` row already exists. */
        data object AlreadyImported : Result

        data class Imported(val summary: MigrationSummary) : Result

        /** The legacy file is untouched and the import can be retried. */
        data class Failed(val cause: Throwable) : Result
    }

    fun legacyDatabaseFile(): File = context.getDatabasePath(LEGACY_DB_NAME)

    fun hasLegacyData(): Boolean = legacyDatabaseFile().exists()

    suspend fun alreadyImported(): Boolean = withContext(io) {
        database.settingsQueries.selectMigrationMeta().executeAsOneOrNull() != null
    }

    /**
     * Idempotent. Safe to call on every launch, and safe to call again after a failure,
     * which is what the Settings "retry import" action does.
     */
    suspend fun importIfNeeded(): Result = withContext(io) {
        if (alreadyImported()) return@withContext Result.AlreadyImported

        val legacyFile = legacyDatabaseFile()
        // Re-checked on every launch rather than cached, so a device-to-device restore
        // that lands the legacy file *after* the first launch is still picked up.
        if (!legacyFile.exists()) return@withContext Result.NothingToImport

        var scratch: File? = null
        try {
            scratch = copyToScratch(legacyFile)
            val legacy = readLegacy(scratch)
            val summary = writeTransactionally(legacy)
            renameLegacyAside(legacyFile)
            Result.Imported(summary)
        } catch (t: Throwable) {
            // The original file was never opened for writing and is never renamed on this
            // path, so the user's data is still fully present and re-importable.
            Log.e(TAG, "Legacy import failed; legacy database left intact", t)
            Result.Failed(t)
        } finally {
            scratch?.parentFile?.deleteRecursively()
        }
    }

    // ---- step 1: work from a copy ------------------------------------------------

    /**
     * Android enables write-ahead logging by default for SQLiteOpenHelper databases, so
     * `-wal` and `-shm` sidecars usually exist and may hold committed transactions that
     * are not yet in the main file. Copying all three and opening the copy read-write
     * lets SQLite recover and checkpoint normally.
     *
     * Opening the *original* read-only would risk SQLITE_READONLY_RECOVERY, or worse,
     * silently reading a database missing its most recent writes.
     */
    private fun copyToScratch(legacyFile: File): File {
        val dir =
            File(context.cacheDir, SCRATCH_DIR).apply {
                deleteRecursively()
                mkdirs()
            }
        val target = File(dir, LEGACY_DB_NAME)
        legacyFile.copyTo(target, overwrite = true)
        for (suffix in WAL_SUFFIXES) {
            val sidecar = File(legacyFile.parentFile, legacyFile.name + suffix)
            if (sidecar.exists()) sidecar.copyTo(File(dir, target.name + suffix), overwrite = true)
        }
        return target
    }

    // ---- step 2: read ------------------------------------------------------------

    private data class LegacyData(
        val categories: List<LegacyCategory>,
        val items: List<LegacyItem>,
        val dateFormat: Int?,
        val notificationSetting: Int?,
    )

    private data class LegacyCategory(val id: Long, val name: String, val type: Int)

    private data class LegacyItem(
        val id: Long,
        val name: String,
        val day: Int,
        val month: Int,
        val year: Int,
        val category: String,
    )

    private fun readLegacy(file: File): LegacyData {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        return db.use {
            LegacyData(
                categories = it.readCategories(),
                items = it.readItems(),
                dateFormat = it.readSingleInt("dateFormatTable", "format"),
                notificationSetting = it.readSingleInt("notificationTable", "setting"),
            )
        }
    }

    private fun SQLiteDatabase.readCategories(): List<LegacyCategory> =
        queryOrEmpty("SELECT id, category, type FROM categoriesTable") { c ->
            LegacyCategory(id = c.getLong(0), name = c.getString(1) ?: "", type = c.getInt(2))
        }

    private fun SQLiteDatabase.readItems(): List<LegacyItem> =
        // Column order in the legacy CREATE is (id, itemName, month, year, date, category);
        // `date` is a day-of-month, not a timestamp, despite the name.
        queryOrEmpty("SELECT id, itemName, month, year, date, category FROM itemsTable") { c ->
            LegacyItem(
                id = c.getLong(0),
                name = c.getString(1) ?: "",
                month = c.getInt(2),
                year = c.getInt(3),
                day = c.getInt(4),
                category = c.getString(5) ?: "",
            )
        }

    private fun SQLiteDatabase.readSingleInt(table: String, column: String): Int? =
        queryOrEmpty("SELECT $column FROM $table LIMIT 1") { it.getInt(0) }.firstOrNull()

    /** A missing table means that helper never ran; treat it as "no data", not an error. */
    private fun <T> SQLiteDatabase.queryOrEmpty(sql: String, map: (android.database.Cursor) -> T): List<T> = try {
        rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(map(cursor))
            }
        }
    } catch (e: android.database.SQLException) {
        Log.w(TAG, "Legacy query failed, treating as empty: $sql", e)
        emptyList()
    }

    // ---- step 3: write, all or nothing -------------------------------------------

    private fun writeTransactionally(legacy: LegacyData): MigrationSummary {
        var orphanCount = 0
        var itemCount = 0
        var categoryCount = 0

        database.transaction {
            database.seedBuiltinCategories()
            database.settingsQueries.ensureSettings()

            // Legacy category name -> 2.0 category id.
            val idByName = mutableMapOf<String, Long>()
            database.categoryQueries.selectAll().executeAsList().forEach { row ->
                BuiltinCategory.fromKey(row.builtinKey)?.let { idByName[it.legacyName.lowercase()] = row.id }
            }

            for (legacyCategory in legacy.categories) {
                val name = legacyCategory.name.trim()
                if (name.isEmpty()) continue
                // "All Items" was both a real row and the "no filter" sentinel. In 2.0 the
                // filter is a null category id, so the row is intentionally dropped.
                if (name.equals(BuiltinCategory.LEGACY_ALL_ITEMS, ignoreCase = true)) continue
                if (idByName.containsKey(name.lowercase())) continue

                database.categoryQueries.insert(name = name, builtinKey = null, isBuiltin = false)
                val newId = database.categoryQueries.lastInsertRowId().executeAsOne()
                idByName[name.lowercase()] = newId
                categoryCount++
            }

            val fallbackId =
                requireNotNull(idByName[BuiltinCategory.UNCATEGORIZED.legacyName.lowercase()]) {
                    "Uncategorized fallback category missing after seeding"
                }

            val uncategorizedId = idByName[BuiltinCategory.UNCATEGORIZED.legacyName.lowercase()]

            val timestamp = now()
            for (item in legacy.items) {
                if (item.name.isBlank()) continue
                val legacyCategory = item.category.trim()

                // "All Items" was the pre-2.0 "show everything" sentinel as well as a real
                // row, so items could genuinely be filed under it. Those belong in
                // Uncategorized, and this is an expected mapping rather than an orphan.
                val resolved =
                    if (legacyCategory.equals(BuiltinCategory.LEGACY_ALL_ITEMS, ignoreCase = true)) {
                        uncategorizedId
                    } else {
                        idByName[legacyCategory.lowercase()]
                    }
                if (resolved == null) orphanCount++
                database.itemQueries.insert(
                    name = item.name,
                    categoryId = resolved ?: fallbackId,
                    expiryDay = item.day.toLong(),
                    expiryMonth = item.month.toLong(),
                    expiryYear = item.year.toLong(),
                    imagePath = null,
                    // The pre-2.0 app had no notes field.
                    notes = null,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
                itemCount++
            }

            legacy.dateFormat?.let { database.settingsQueries.updateDateFormat(it.toLong()) }
            // Legacy encoding was 1 = enabled, 2 = disabled. Inverting this by accident
            // would silently flip every existing user's notification preference.
            legacy.notificationSetting?.let {
                database.settingsQueries.updateNotificationsEnabled(it == LEGACY_NOTIFICATIONS_ENABLED)
            }

            database.settingsQueries.insertMigrationMeta(
                migratedAt = timestamp,
                itemCount = itemCount.toLong(),
                categoryCount = categoryCount.toLong(),
                orphanCount = orphanCount.toLong(),
                imageCount = 0,
            )
        }

        return MigrationSummary(
            migratedAt = now(),
            itemCount = itemCount,
            categoryCount = categoryCount,
            orphanCount = orphanCount,
            imageCount = 0,
        )
    }

    // ---- step 4: retire the legacy file ------------------------------------------

    /**
     * Only reached after a successful commit. A failure here is cosmetic: the data is
     * already imported and `migration_meta` prevents a re-run, so a lingering legacy file
     * is harmless.
     */
    private fun renameLegacyAside(legacyFile: File) {
        runCatching {
            legacyFile.renameTo(File(legacyFile.parentFile, "$LEGACY_DB_NAME$BACKUP_SUFFIX"))
            for (suffix in WAL_SUFFIXES) {
                File(legacyFile.parentFile, legacyFile.name + suffix).delete()
            }
        }.onFailure { Log.w(TAG, "Could not rename legacy database aside", it) }
    }

    companion object {
        const val LEGACY_DB_NAME = "itemsDatabase"
        const val BACKUP_SUFFIX = ".bak"
        private const val SCRATCH_DIR = "legacy-import"
        private const val LEGACY_NOTIFICATIONS_ENABLED = 1
        private val WAL_SUFFIXES = listOf("-wal", "-shm", "-journal")
        private const val TAG = "LegacyImporter"
    }
}
