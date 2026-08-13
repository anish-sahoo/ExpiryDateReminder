package com.anish.expirydatereminder.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.model.dateFormatForCountry

/**
 * Builds the 2.0 database.
 *
 * Note the filename: [DATABASE_NAME] is deliberately NOT the legacy `itemsDatabase`.
 * The legacy file is only ever read by [com.anish.expirydatereminder.migration.LegacyImporter],
 * from a copy, and is never adopted in place.
 */
object DriverFactory {
    const val DATABASE_NAME = "edr_v2.db"

    fun create(context: Context): SqlDriver = AndroidSqliteDriver(
        schema = EdrDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
        callback =
        object : AndroidSqliteDriver.Callback(EdrDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                // SQLite disables foreign keys by default on every connection. Without
                // this the ON DELETE CASCADE from item -> category is decorative and
                // deleting a category would silently orphan its items.
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    /**
     * Populates a freshly created database. Safe to call on every launch: seeding checks for
     * existing `builtinKey` values and the settings insert is INSERT OR IGNORE.
     *
     * The starting date format comes from the device's country, so someone in the US opens the
     * app on 08/31/2027 rather than 31/08/2027. It applies to the first run only — OR IGNORE
     * leaves an existing row alone, and a migrating user's legacy choice overwrites it moments
     * later when the importer runs.
     */
    fun initialize(context: Context, database: EdrDatabase) {
        val country = context.resources.configuration.locales[0].country
        database.transaction {
            database.seedBuiltinCategories()
            database.settingsQueries.ensureSettingsWithDateFormat(
                dateFormatForCountry(country).legacyValue.toLong(),
            )
        }
    }
}
