package com.anish.expirydatereminder.domain

import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.isLeapYear
import com.anish.expirydatereminder.domain.model.minusDays
import com.anish.expirydatereminder.domain.model.plusDays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ExpiryDateTest {

    @Test
    fun `sort key packs the date into one comparable integer`() {
        assertEquals(20270331, ExpiryDate(31, 3, 2027).sortKey)
        assertEquals(20270101, ExpiryDate(1, 1, 2027).sortKey)
    }

    @Test
    fun `sort key round-trips`() {
        // The reminder and widget queries hand sort keys to SQL and read them back, so a
        // lossy conversion would quietly shift dates.
        val date = ExpiryDate(29, 2, 2028)
        assertEquals(date, ExpiryDate.fromSortKey(date.sortKey))
    }

    @Test
    fun `orders by calendar date, not by field order`() {
        // A naive day-first comparison would put 31 January after 1 February.
        val january = ExpiryDate(31, 1, 2027)
        val february = ExpiryDate(1, 2, 2027)
        assertTrue(january < february)
    }

    @Test
    fun `converts to and from LocalDate without shifting`() {
        val date = ExpiryDate(31, 12, 2027)
        assertEquals(LocalDate(2027, 12, 31), date.toLocalDate())
        assertEquals(date, ExpiryDate.from(date.toLocalDate()))
    }

    @Test
    fun `leap years follow the hundred and four hundred year rules`() {
        assertTrue(isLeapYear(2028))
        assertTrue(isLeapYear(2000))
        assertTrue(!isLeapYear(1900))
        assertTrue(!isLeapYear(2027))
    }
}

class DateMathTest {

    @Test
    fun `adding days crosses month and year boundaries`() {
        assertEquals(LocalDate(2027, 1, 1), LocalDate(2026, 12, 31).plusDays(1))
        assertEquals(LocalDate(2027, 3, 3), LocalDate(2027, 2, 28).plusDays(3))
    }

    @Test
    fun `adding days accounts for leap days`() {
        // 2028 is a leap year, so the 28th plus one is the 29th rather than March.
        assertEquals(LocalDate(2028, 2, 29), LocalDate(2028, 2, 28).plusDays(1))
    }

    @Test
    fun `subtracting is the inverse of adding`() {
        val start = LocalDate(2027, 3, 15)
        assertEquals(start, start.plusDays(40).minusDays(40))
    }

    @Test
    fun `subtracting crosses backwards over a year boundary`() {
        assertEquals(LocalDate(2026, 12, 31), LocalDate(2027, 1, 1).minusDays(1))
    }
}
