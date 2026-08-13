package com.anish.expirydatereminder.migration

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.model.BuiltinCategory
import com.anish.expirydatereminder.testing.inMemoryDatabase
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pre-2.0 import.
 *
 * This is the one piece of code that can lose a real user's data, so it is tested against an
 * actual legacy-shaped SQLite file rather than a stub: the importer opens it with raw SQL and
 * depends on the exact column order and naming the old app used, which no fake would reproduce
 * faithfully.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LegacyImporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: EdrDatabase

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        legacyFile().delete()
        File(legacyFile().path + ".bak").delete()
    }

    @After
    fun tearDown() {
        legacyFile().delete()
        File(legacyFile().path + ".bak").delete()
    }

    private fun legacyFile(): File = context.getDatabasePath("itemsDatabase")

    private fun importer(now: Long = 1_700_000_000_000) = LegacyImporter(
        context = context,
        database = database,
        io = Dispatchers.Unconfined,
        now = { now },
    )

    /**
     * Recreates the pre-2.0 schema exactly, including the two details most likely to be
     * mishandled: `date` holds a day-of-month despite its name, and `notificationTable.setting`
     * uses 1 = on / 2 = off rather than a boolean.
     */
    private fun writeLegacyDatabase(
        categories: List<Pair<String, Int>> = emptyList(),
        items: List<LegacyRow> = emptyList(),
        dateFormat: Int? = null,
        notificationSetting: Int? = null,
    ) {
        legacyFile().parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(legacyFile(), null).use { db ->
            db.execSQL("CREATE TABLE categoriesTable (id INTEGER PRIMARY KEY, category TEXT, type INTEGER)")
            db.execSQL(
                "CREATE TABLE itemsTable (id INTEGER PRIMARY KEY, itemName TEXT, month INTEGER, " +
                    "year INTEGER, date INTEGER, category TEXT)",
            )
            categories.forEachIndexed { index, (name, type) ->
                db.execSQL(
                    "INSERT INTO categoriesTable VALUES (?, ?, ?)",
                    arrayOf<Any>(index + 1, name, type),
                )
            }
            items.forEachIndexed { index, row ->
                db.execSQL(
                    "INSERT INTO itemsTable VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(index + 1, row.name, row.month, row.year, row.day, row.category),
                )
            }
            if (dateFormat != null) {
                db.execSQL("CREATE TABLE dateFormatTable (format INTEGER)")
                db.execSQL("INSERT INTO dateFormatTable VALUES (?)", arrayOf<Any>(dateFormat))
            }
            if (notificationSetting != null) {
                db.execSQL("CREATE TABLE notificationTable (setting INTEGER)")
                db.execSQL("INSERT INTO notificationTable VALUES (?)", arrayOf<Any>(notificationSetting))
            }
        }
    }

    private data class LegacyRow(val name: String, val day: Int, val month: Int, val year: Int, val category: String)

    private fun itemNames() = database.itemQueries.selectAll().executeAsList().map { it.name }

    @Test
    fun `a clean install has nothing to import`() = runTest {
        assertIs<LegacyImporter.Result.NothingToImport>(importer().importIfNeeded())
    }

    @Test
    fun `imports items and preserves their dates`() = runTest {
        writeLegacyDatabase(
            items = listOf(LegacyRow("Milk", day = 3, month = 4, year = 2027, category = "Grocery")),
        )

        val result = importer().importIfNeeded()

        assertIs<LegacyImporter.Result.Imported>(result)
        assertEquals(1, result.summary.itemCount)
        val imported = database.itemQueries.selectAll().executeAsList().single()
        assertEquals("Milk", imported.name)
        assertEquals(3L, imported.expiryDay)
        assertEquals(4L, imported.expiryMonth)
        assertEquals(2027L, imported.expiryYear)
    }

    @Test
    fun `maps legacy category names onto the built-in categories`() = runTest {
        writeLegacyDatabase(
            items = listOf(LegacyRow("Peas", 1, 1, 2027, category = "Frozen Items")),
        )

        importer().importIfNeeded()

        val row = database.itemQueries.selectAll().executeAsList().single()
        assertEquals(BuiltinCategory.FROZEN.key, row.categoryBuiltinKey)
    }

    @Test
    fun `All Items becomes Uncategorized rather than an orphan`() = runTest {
        // "All Items" was both a real row and the show-everything sentinel, so items really
        // could be filed under it. Counting those as orphans would misreport the import.
        writeLegacyDatabase(
            items = listOf(LegacyRow("Odd one", 1, 1, 2027, category = BuiltinCategory.LEGACY_ALL_ITEMS)),
        )

        val result = importer().importIfNeeded()

        assertIs<LegacyImporter.Result.Imported>(result)
        assertEquals(0, result.summary.orphanCount)
        val row = database.itemQueries.selectAll().executeAsList().single()
        assertEquals(BuiltinCategory.UNCATEGORIZED.key, row.categoryBuiltinKey)
    }

    @Test
    fun `an unknown category lands in Uncategorized and is counted`() = runTest {
        // Dropping the item would be data loss, so it is kept and reported instead.
        writeLegacyDatabase(
            items = listOf(LegacyRow("Mystery", 1, 1, 2027, category = "Nonexistent")),
        )

        val result = importer().importIfNeeded()

        assertIs<LegacyImporter.Result.Imported>(result)
        assertEquals(1, result.summary.orphanCount)
        assertEquals(listOf("Mystery"), itemNames())
    }

    @Test
    fun `custom categories come across`() = runTest {
        writeLegacyDatabase(
            categories = listOf("Garage" to 0),
            items = listOf(LegacyRow("Antifreeze", 1, 1, 2027, category = "Garage")),
        )

        importer().importIfNeeded()

        val row = database.itemQueries.selectAll().executeAsList().single()
        assertEquals("Garage", row.categoryName)
        assertNull(row.categoryBuiltinKey)
    }

    @Test
    fun `the date format setting carries across unchanged`() = runTest {
        writeLegacyDatabase(dateFormat = 1)

        importer().importIfNeeded()

        assertEquals(1L, database.settingsQueries.selectSettings().executeAsOne().dateFormat)
    }

    @Test
    fun `the notification setting is inverted, because the legacy encoding was 1 on 2 off`() = runTest {
        // Getting this backwards would silently flip every migrating user's preference.
        writeLegacyDatabase(notificationSetting = 2)
        importer().importIfNeeded()
        assertEquals(false, database.settingsQueries.selectSettings().executeAsOne().notificationsEnabled)

        setUp()
        writeLegacyDatabase(notificationSetting = 1)
        importer().importIfNeeded()
        assertEquals(true, database.settingsQueries.selectSettings().executeAsOne().notificationsEnabled)
    }

    @Test
    fun `a committed import writes the meta row, which is the only valid guard`() = runTest {
        writeLegacyDatabase(items = listOf(LegacyRow("Milk", 1, 1, 2027, "Grocery")))

        importer().importIfNeeded()

        val meta = database.settingsQueries.selectMigrationMeta().executeAsOneOrNull()
        assertNotNull(meta)
        assertEquals(1L, meta.itemCount)
    }

    @Test
    fun `the legacy file is renamed aside only after the commit`() = runTest {
        writeLegacyDatabase(items = listOf(LegacyRow("Milk", 1, 1, 2027, "Grocery")))

        importer().importIfNeeded()

        assertTrue(!legacyFile().exists(), "the original must be moved so it is not re-imported")
        assertTrue(File(legacyFile().path + ".bak").exists(), "but kept, in case the import was wrong")
    }

    @Test
    fun `running twice imports once`() = runTest {
        writeLegacyDatabase(items = listOf(LegacyRow("Milk", 1, 1, 2027, "Grocery")))

        assertIs<LegacyImporter.Result.Imported>(importer().importIfNeeded())
        assertIs<LegacyImporter.Result.AlreadyImported>(importer().importIfNeeded())
        assertEquals(1, itemNames().size)
    }

    @Test
    fun `alreadyImported reflects the meta row, not the file`() = runTest {
        writeLegacyDatabase(items = listOf(LegacyRow("Milk", 1, 1, 2027, "Grocery")))
        assertTrue(!importer().alreadyImported())

        importer().importIfNeeded()

        assertTrue(importer().alreadyImported())
    }

    @Test
    fun `a legacy database missing the settings tables still imports its items`() = runTest {
        // Those tables only exist if the user ever opened the relevant screen.
        writeLegacyDatabase(items = listOf(LegacyRow("Milk", 1, 1, 2027, "Grocery")))

        val result = importer().importIfNeeded()

        assertIs<LegacyImporter.Result.Imported>(result)
        assertEquals(listOf("Milk"), itemNames())
    }

    @Test
    fun `a corrupt legacy file fails without touching the new database`() = runTest {
        legacyFile().parentFile?.mkdirs()
        legacyFile().writeText("this is not a database")

        val result = importer().importIfNeeded()

        assertIs<LegacyImporter.Result.Failed>(result)
        assertTrue(itemNames().isEmpty())
        assertNull(database.settingsQueries.selectMigrationMeta().executeAsOneOrNull())
        // Left in place, so Settings can offer a retry that has something to retry.
        assertTrue(legacyFile().exists())
    }

    @Test
    fun `an empty legacy database still counts as imported, so it is not retried forever`() = runTest {
        writeLegacyDatabase()

        val result = importer().importIfNeeded()

        assertIs<LegacyImporter.Result.Imported>(result)
        assertEquals(0, result.summary.itemCount)
        assertNotNull(database.settingsQueries.selectMigrationMeta().executeAsOneOrNull())
    }

    @Test
    fun `hasLegacyData tracks the file`() {
        assertTrue(!importer().hasLegacyData())
        writeLegacyDatabase()
        assertTrue(importer().hasLegacyData())
    }
}
