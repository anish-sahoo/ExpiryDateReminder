package com.anish.expirydatereminder.domain

import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.ExpiryStatus
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.model.lengthOfMonth
import com.anish.expirydatereminder.domain.usecase.DraftError
import com.anish.expirydatereminder.domain.usecase.DraftField
import com.anish.expirydatereminder.domain.usecase.ItemSort
import com.anish.expirydatereminder.domain.usecase.matching
import com.anish.expirydatereminder.domain.usecase.validateDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ValidateDraftTest {
    @Test
    fun `blank name is rejected`() {
        val result = validateDraft(name = "  ", day = "1", month = "3", year = "2027", currentYear = YEAR)
        assertEquals(DraftError.NAME_BLANK, result.errors[DraftField.NAME])
    }

    @Test
    fun `month is required and bounded`() {
        assertEquals(
            DraftError.MONTH_REQUIRED,
            validateDraft("Milk", "1", "", "2027", YEAR).errors[DraftField.MONTH],
        )
        assertEquals(
            DraftError.MONTH_OUT_OF_RANGE,
            validateDraft("Milk", "1", "13", "2027", YEAR).errors[DraftField.MONTH],
        )
    }

    @Test
    fun `blank day defaults to the last day of the month, as the legacy dialog did`() {
        val result = validateDraft("Milk", day = "", month = "2", year = "2027", currentYear = YEAR)
        assertTrue(result.isValid)
        assertEquals(28, result.expiry?.day)
    }

    @Test
    fun `blank year defaults to the current year`() {
        val result = validateDraft("Milk", day = "5", month = "3", year = "", currentYear = YEAR)
        assertTrue(result.isValid)
        assertEquals(YEAR, result.expiry?.year)
    }

    @Test
    fun `day is validated against the actual month length including leap years`() {
        assertTrue(validateDraft("Milk", "29", "2", "2028", YEAR).isValid)
        assertEquals(
            DraftError.DAY_OUT_OF_RANGE_FOR_MONTH,
            validateDraft("Milk", "29", "2", "2027", YEAR).errors[DraftField.DAY],
        )
        assertEquals(
            DraftError.DAY_OUT_OF_RANGE_FOR_MONTH,
            validateDraft("Milk", "31", "4", "2027", YEAR).errors[DraftField.DAY],
        )
    }

    @Test
    fun `year must be four digits`() {
        assertEquals(
            DraftError.YEAR_OUT_OF_RANGE,
            validateDraft("Milk", "1", "3", "27", YEAR).errors[DraftField.YEAR],
        )
    }

    @Test
    fun `reports every problem at once rather than one at a time`() {
        val result = validateDraft(name = "", day = "1", month = "99", year = "5", currentYear = YEAR)
        assertTrue(result.errors.size >= 3)
        assertNull(result.expiry)
    }

    private companion object {
        const val YEAR = 2026
    }
}

class LengthOfMonthTest {
    @Test
    fun `handles leap years including the hundred and four hundred year rules`() {
        assertEquals(29, lengthOfMonth(2024, 2))
        assertEquals(28, lengthOfMonth(2026, 2))
        assertEquals(28, lengthOfMonth(1900, 2))
        assertEquals(29, lengthOfMonth(2000, 2))
    }
}

class ExpiryStatusTest {
    private val today = LocalDate(2026, 8, 6)

    @Test
    fun `classifies expired, expiring soon and fine`() {
        assertEquals(ExpiryStatus.EXPIRED, ExpiryStatus.of(ExpiryDate(5, 8, 2026), today))
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryStatus.of(ExpiryDate(6, 8, 2026), today))
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryStatus.of(ExpiryDate(20, 8, 2026), today))
        assertEquals(ExpiryStatus.OK, ExpiryStatus.of(ExpiryDate(21, 8, 2026), today))
    }
}

class ItemSortTest {
    private fun item(id: Long, name: String, date: ExpiryDate) = Item(
        id = id,
        name = name,
        categoryId = 1,
        categoryName = "Grocery",
        categoryBuiltinKey = "grocery",
        expiry = date,
        imagePath = null,
        notes = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private val items =
        listOf(
            item(1, "banana", ExpiryDate(1, 12, 2026)),
            item(2, "Apple", ExpiryDate(15, 3, 2027)),
            item(3, "cherry", ExpiryDate(1, 1, 2026)),
        )

    @Test
    fun `sorts chronologically across year, month and day`() {
        val sorted = ItemSort.BY_EXPIRY.apply(items).map { it.id }
        assertEquals(listOf(3L, 1L, 2L), sorted)
    }

    @Test
    fun `sorts by name case-insensitively, unlike the legacy comparator`() {
        val sorted = ItemSort.BY_NAME.apply(items).map { it.name }
        assertEquals(listOf("Apple", "banana", "cherry"), sorted)
    }

    @Test
    fun `search matches case-insensitively and ignores surrounding whitespace`() {
        assertEquals(1, items.matching("  APP ").size)
        assertEquals(3, items.matching("").size)
        assertTrue(items.matching("zzz").isEmpty())
    }
}
