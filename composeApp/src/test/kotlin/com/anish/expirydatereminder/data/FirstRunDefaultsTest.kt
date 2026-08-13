package com.anish.expirydatereminder.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.testing.inMemoryDatabase
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a brand new database starts with.
 *
 * The interesting property is not that the locale default is applied, but that it is applied
 * *once*: a second launch, or a legacy import arriving afterwards, must not have their values
 * quietly replaced by whatever the device's region happens to be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirstRunDefaultsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun EdrDatabase.storedDateFormat(): DateFormat =
        DateFormat.fromLegacy(settingsQueries.selectSettings().executeAsOne().dateFormat.toInt())

    @Test
    @Config(qualifiers = "en-rUS")
    fun `a fresh install in the US starts on month first`() {
        val db = inMemoryDatabase(context)
        DriverFactory.initialize(context, db)
        assertEquals(DateFormat.MONTH_FIRST, db.storedDateFormat())
    }

    @Test
    @Config(qualifiers = "en-rGB")
    fun `a fresh install in the UK starts on day first`() {
        val db = inMemoryDatabase(context)
        DriverFactory.initialize(context, db)
        assertEquals(DateFormat.DAY_FIRST, db.storedDateFormat())
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `a fresh install in Germany starts on the dotted form`() {
        val db = inMemoryDatabase(context)
        DriverFactory.initialize(context, db)
        assertEquals(DateFormat.DAY_FIRST_DOTTED, db.storedDateFormat())
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `a choice already made survives the next launch`() {
        // initialize runs on every launch, so this is the case that matters: an INSERT that
        // was not OR IGNORE would reset the user's setting every time they opened the app.
        val db = inMemoryDatabase(context)
        DriverFactory.initialize(context, db)
        db.settingsQueries.updateDateFormat(DateFormat.ISO.legacyValue.toLong())

        DriverFactory.initialize(context, db)

        assertEquals(DateFormat.ISO, db.storedDateFormat())
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `the built-in categories are seeded exactly once`() {
        val db = inMemoryDatabase(context)
        DriverFactory.initialize(context, db)
        val afterFirst = db.categoryQueries.selectAll().executeAsList().size

        DriverFactory.initialize(context, db)

        assertEquals(afterFirst, db.categoryQueries.selectAll().executeAsList().size)
    }

    @Test
    fun `the device locale is what drives the default`() {
        // Guards the wiring rather than the mapping: reading the wrong locale field, or
        // reading none at all, is how this ended up unwired in the first place.
        val country = Locale.getDefault().country
        val db = inMemoryDatabase(context)
        DriverFactory.initialize(context, db)
        assertEquals(
            com.anish.expirydatereminder.domain.model.dateFormatForCountry(country),
            db.storedDateFormat(),
        )
    }
}
