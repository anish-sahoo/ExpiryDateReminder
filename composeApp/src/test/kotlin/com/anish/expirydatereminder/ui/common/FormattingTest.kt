package com.anish.expirydatereminder.ui.common

import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.ExpiryDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DateFormattingTest {

    private val date = ExpiryDate(day = 3, month = 4, year = 2027)

    @Test
    fun `renders each format the way its region writes dates`() {
        assertEquals("04/03/2027", date.format(DateFormat.MONTH_FIRST))
        assertEquals("03/04/2027", date.format(DateFormat.DAY_FIRST))
        assertEquals("2027-04-03", date.format(DateFormat.ISO))
        assertEquals("03.04.2027", date.format(DateFormat.DAY_FIRST_DOTTED))
        assertEquals("2027/04/03", date.format(DateFormat.YEAR_FIRST_SLASH))
        assertEquals("3 Apr 2027", date.format(DateFormat.DAY_MONTH_NAME))
    }

    @Test
    fun `pads single digits everywhere except the named-month form`() {
        // "03 Apr" reads as filler; "03/04" reads as a date. Hence the one exception.
        assertTrue(date.format(DateFormat.ISO).contains("-04-03"))
        assertTrue(date.format(DateFormat.DAY_MONTH_NAME).startsWith("3 "))
    }

    @Test
    fun `every month maps to a name`() {
        // An off-by-one in the abbreviation table would render December as an index error.
        (1..12).forEach { month ->
            val rendered = ExpiryDate(1, month, 2027).format(DateFormat.DAY_MONTH_NAME)
            assertTrue(rendered.any { it.isLetter() }, "month $month produced '$rendered'")
        }
        assertEquals("1 Jan 2027", ExpiryDate(1, 1, 2027).format(DateFormat.DAY_MONTH_NAME))
        assertEquals("1 Dec 2027", ExpiryDate(1, 12, 2027).format(DateFormat.DAY_MONTH_NAME))
    }

    @Test
    fun `the settings sample uses the same code as the list`() {
        // Otherwise the picker could advertise a format the app does not actually produce.
        DateFormat.entries.forEach { format ->
            assertEquals(ExpiryDate(31, 8, 2027).format(format), format.sample())
        }
    }
}

/**
 * The badge on every list row. It has to stay short enough not to crowd the item name, and
 * the sign has to be unambiguous — "-4d" and "+4d" mean opposite things to the user.
 */
class CompactRelativeTest {

    @Test
    fun `today is a word, not a zero`() {
        assertEquals("today", compactRelative(0))
    }

    @Test
    fun `days keep their sign`() {
        assertEquals("+3d", compactRelative(3))
        assertEquals("-4d", compactRelative(-4))
    }

    @Test
    fun `switches unit as the distance grows`() {
        assertEquals("+13d", compactRelative(13))
        assertEquals("+2w", compactRelative(14))
        assertEquals("+2mo", compactRelative(61))
        assertEquals("+1y", compactRelative(365))
    }

    @Test
    fun `is symmetric about zero`() {
        listOf(3, 14, 61, 400).forEach { days ->
            val ahead = compactRelative(days)
            val behind = compactRelative(-days)
            assertEquals("+${behind.removePrefix("-")}", ahead, "asymmetric at $days days")
        }
    }

    @Test
    fun `never exceeds five characters, whatever the distance`() {
        // The badge is a fixed-width pill; anything longer is truncated on screen.
        listOf(0, 1, -1, 13, 60, 364, 3650, -3650, 100_000).forEach { days ->
            val text = compactRelative(days)
            assertTrue(text.length <= 5, "'$text' is too long for the badge, at $days days")
        }
    }
}
