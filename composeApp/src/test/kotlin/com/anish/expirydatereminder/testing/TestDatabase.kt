package com.anish.expirydatereminder.testing

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.anish.expirydatereminder.db.EdrDatabase

/**
 * A real schema, held in memory.
 *
 * Passing a null name to [AndroidSqliteDriver] makes SQLite create a private in-memory
 * database that disappears with the connection, so tests get isolation for free and never
 * touch the app's own file.
 *
 * Used where the code under test genuinely depends on SQL behavior — the legacy importer's
 * transaction, foreign keys cascading, the seeded built-in categories. Everywhere else the
 * fakes are faster and read better.
 */
fun inMemoryDatabase(context: Context): EdrDatabase =
    EdrDatabase(AndroidSqliteDriver(EdrDatabase.Schema, context, name = null))
