package com.anish.expirydatereminder.domain

import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.DatePart
import com.anish.expirydatereminder.domain.model.DayMonthOrder
import com.anish.expirydatereminder.domain.model.parts
import com.anish.expirydatereminder.domain.model.pattern
import com.anish.expirydatereminder.domain.model.separator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DateFormatTest {

    @Test
    fun `the two legacy formats keep their stored encoding`() {
        // These integers are already in migrated users' databases. Changing them would
        // silently reinterpret everyone's date preference.
        assertEquals(1, DateFormat.MONTH_FIRST.legacyValue)
        assertEquals(2, DateFormat.DAY_FIRST.legacyValue)
    }

    @Test
    fun `legacy values round-trip`() {
        DateFormat.entries.forEach { format ->
            assertEquals(format, DateFormat.fromLegacy(format.legacyValue))
        }
    }

    @Test
    fun `an unknown legacy value falls back rather than throwing`() {
        // A database written by a build we do not know about must not crash the import.
        assertEquals(DateFormat.DAY_FIRST, DateFormat.fromLegacy(99))
        assertEquals(DateFormat.DAY_FIRST, DateFormat.fromLegacy(0))
    }

    @Test
    fun `every format lists exactly the three date parts once`() {
        // The entry form builds its fields from this list, so a duplicate or missing part
        // would produce a form that cannot express a date.
        DateFormat.entries.forEach { format ->
            assertEquals(DatePart.entries.toSet(), format.parts.toSet(), "for $format")
            assertEquals(3, format.parts.size, "for $format")
        }
    }

    @Test
    fun `only the US format leads with the month`() {
        assertEquals(DayMonthOrder.MONTH_FIRST, DateFormat.MONTH_FIRST.dayMonthOrder)
        DateFormat.entries.filter { it != DateFormat.MONTH_FIRST }.forEach {
            assertEquals(DayMonthOrder.DAY_FIRST, it.dayMonthOrder, "for $it")
        }
    }

    @Test
    fun `field order matches the way each format is read`() {
        assertEquals(listOf(DatePart.MONTH, DatePart.DAY, DatePart.YEAR), DateFormat.MONTH_FIRST.parts)
        assertEquals(listOf(DatePart.DAY, DatePart.MONTH, DatePart.YEAR), DateFormat.DAY_FIRST.parts)
        assertEquals(listOf(DatePart.YEAR, DatePart.MONTH, DatePart.DAY), DateFormat.ISO.parts)
    }

    @Test
    fun `separators match the conventions each format belongs to`() {
        assertEquals("-", DateFormat.ISO.separator)
        assertEquals(".", DateFormat.DAY_FIRST_DOTTED.separator)
        assertEquals("/", DateFormat.MONTH_FIRST.separator)
    }

    @Test
    fun `every pattern is non-empty and describes its own parts`() {
        DateFormat.entries.forEach { format ->
            assertTrue(format.pattern.isNotBlank(), "for $format")
            assertTrue(format.pattern.contains("Y"), "for $format")
        }
    }
}
