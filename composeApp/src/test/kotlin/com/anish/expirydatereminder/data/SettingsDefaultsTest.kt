package com.anish.expirydatereminder.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.testing.inMemoryDatabase
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The schema's column defaults and [AppSettings]'s Kotlin defaults have to agree.
 *
 * They did not: the column said 1 (month first) while [AppSettings] said day first, so which
 * one the app fell back to depended on whether the row was read or defaulted. Nothing reaches
 * either now — the row is seeded from the device locale when the database is created — which
 * is precisely why the disagreement could sit there unnoticed. This is the guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsDefaultsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Inserts the row the way the schema would, with no explicit values. */
    private fun rowFromSchemaDefaults() = inMemoryDatabase(context).let { db ->
        db.settingsQueries.ensureSettings()
        db.settingsQueries.selectSettings().executeAsOne()
    }

    @Test
    fun `the schema's defaults match AppSettings, field for field`() {
        val schema = rowFromSchemaDefaults()
        val kotlin = AppSettings()

        assertEquals(kotlin.dateFormat.legacyValue.toLong(), schema.dateFormat)
        assertEquals(kotlin.notificationsEnabled, schema.notificationsEnabled)
        assertEquals(kotlin.reminderLeadDays.toLong(), schema.reminderLeadDays)
        assertEquals(kotlin.reminderHour.toLong(), schema.reminderHour)
    }

    @Test
    fun `the repository's fallback row also matches`() {
        // `current()` returns this when no row exists, so a third copy of the defaults would
        // be a third chance to disagree.
        val repository = SettingsRepositoryImpl(inMemoryDatabase(context), kotlinx.coroutines.Dispatchers.Unconfined)
        val fallback = kotlinx.coroutines.runBlocking { repository.current() }

        assertEquals(AppSettings(), fallback)
    }
}
