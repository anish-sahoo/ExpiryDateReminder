package com.anish.expirydatereminder.domain

import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.usecase.ItemSort
import com.anish.expirydatereminder.domain.usecase.matching
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Search is the feature European users are most likely to break, because it is the only
 * place the app compares text they typed against text it stored.
 */
class SearchTest {

    private val items = listOf(
        testItem(id = 1, name = "Crème fraîche", notes = "top shelf"),
        testItem(id = 2, name = "Müsli", categoryName = "Grocery"),
        testItem(id = 3, name = "Ibuprofen", notes = "Bathroom cabinet", categoryName = "Medicine"),
        testItem(id = 4, name = "Żurek", notes = null),
    )

    @Test
    fun `matches without diacritics, in both directions`() {
        assertEquals(listOf(1L), items.matching("creme").map { it.id })
        assertEquals(listOf(1L), items.matching("crème").map { it.id })
        assertEquals(listOf(2L), items.matching("musli").map { it.id })
        assertEquals(listOf(4L), items.matching("zurek").map { it.id })
    }

    @Test
    fun `matches on notes and on category, not just the name`() {
        assertEquals(listOf(3L), items.matching("cabinet").map { it.id })
        assertEquals(listOf(3L), items.matching("medicine").map { it.id })
    }

    @Test
    fun `ignores case and surrounding whitespace`() {
        assertEquals(listOf(3L), items.matching("  IBUPROFEN  ").map { it.id })
    }

    @Test
    fun `an empty or whitespace-only query returns everything`() {
        // The search field starts empty, so this is the common case, not an edge case.
        assertEquals(items, items.matching(""))
        assertEquals(items, items.matching("   "))
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        assertTrue(items.matching("zzzz").isEmpty())
    }

    @Test
    fun `an item with no notes does not match on its absent notes`() {
        assertTrue(items.matching("null").isEmpty())
    }
}

class ItemSortOrderTest {

    private val items = listOf(
        testItem(id = 1, name = "banana", expiry = ExpiryDate(1, 12, 2027)),
        testItem(id = 2, name = "Apple", expiry = ExpiryDate(1, 12, 2027)),
        testItem(id = 3, name = "cherry", expiry = ExpiryDate(1, 1, 2027)),
    )

    @Test
    fun `by expiry puts the soonest first`() {
        assertEquals(listOf(3L, 2L, 1L), ItemSort.BY_EXPIRY.apply(items).map { it.id })
    }

    @Test
    fun `by expiry breaks ties on name, so the order is stable`() {
        // Two items on the same date must not swap places between recompositions.
        val sameDate = ItemSort.BY_EXPIRY.apply(items).filter { it.expiry == ExpiryDate(1, 12, 2027) }
        assertEquals(listOf("Apple", "banana"), sameDate.map { it.name })
    }

    @Test
    fun `by name is case-insensitive, unlike the legacy comparator`() {
        // The pre-2.0 comparator was case-sensitive, so "banana" sorted before "Apple".
        assertEquals(listOf("Apple", "banana", "cherry"), ItemSort.BY_NAME.apply(items).map { it.name })
    }

    @Test
    fun `by name breaks ties on expiry`() {
        val duplicates = listOf(
            testItem(id = 1, name = "Milk", expiry = ExpiryDate(5, 1, 2027)),
            testItem(id = 2, name = "milk", expiry = ExpiryDate(1, 1, 2027)),
        )
        assertEquals(listOf(2L, 1L), ItemSort.BY_NAME.apply(duplicates).map { it.id })
    }

    @Test
    fun `sorting an empty list is not an error`() {
        assertTrue(ItemSort.BY_NAME.apply(emptyList()).isEmpty())
    }
}
