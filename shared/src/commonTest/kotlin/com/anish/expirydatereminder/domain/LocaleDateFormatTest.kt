package com.anish.expirydatereminder.domain

import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.dateFormatForCountry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The date format a fresh install starts on.
 *
 * Worth pinning because it was written and then never called: every new install got the same
 * format regardless of country, so a US user saw 31/08/2027 until they found the setting.
 */
class LocaleDateFormatTest {

    @Test
    fun `the US gets month first, and it is the only country that does`() {
        assertEquals(DateFormat.MONTH_FIRST, dateFormatForCountry("US"))
        listOf("GB", "IE", "IN", "AU", "FR", "BR").forEach {
            assertEquals(DateFormat.DAY_FIRST, dateFormatForCountry(it), "for $it")
        }
    }

    @Test
    fun `German-speaking Europe and the Nordics get the dotted form`() {
        listOf("DE", "AT", "CH", "PL", "FI", "CZ", "NO", "DK").forEach {
            assertEquals(DateFormat.DAY_FIRST_DOTTED, dateFormatForCountry(it), "for $it")
        }
    }

    @Test
    fun `East Asia gets year first`() {
        listOf("JP", "CN", "TW", "KR").forEach {
            assertEquals(DateFormat.YEAR_FIRST_SLASH, dateFormatForCountry(it), "for $it")
        }
    }

    @Test
    fun `Sweden and Lithuania get ISO, which is what they write natively`() {
        assertEquals(DateFormat.ISO, dateFormatForCountry("SE"))
        assertEquals(DateFormat.ISO, dateFormatForCountry("LT"))
    }

    @Test
    fun `an unknown or empty country falls back to day first rather than failing`() {
        // Locale.getCountry() returns "" when the device has no region set, which is a real
        // configuration, not a broken one.
        assertEquals(DateFormat.DAY_FIRST, dateFormatForCountry(""))
        assertEquals(DateFormat.DAY_FIRST, dateFormatForCountry("ZZ"))
    }

    @Test
    fun `country matching is case-insensitive`() {
        // Locale.getCountry() is upper case, but nothing in the type system says so.
        assertEquals(DateFormat.MONTH_FIRST, dateFormatForCountry("us"))
        assertEquals(DateFormat.DAY_FIRST_DOTTED, dateFormatForCountry("de"))
    }
}
