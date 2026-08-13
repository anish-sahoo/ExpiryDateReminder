package com.anish.expirydatereminder.domain

import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.usecase.CheckForDuplicate
import com.anish.expirydatereminder.domain.usecase.DuplicateCheck
import com.anish.expirydatereminder.testing.FakeItemRepository
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * The four outcomes the add form branches on. Getting these wrong either nags the user
 * about a non-duplicate or silently lets a real one through.
 */
class DuplicateCheckTest {

    private val existing = testItem(
        id = 1,
        name = "Whole Milk",
        expiry = ExpiryDate(10, 4, 2027),
        categoryId = 1,
    )

    private fun draft(
        name: String = "Whole Milk",
        expiry: ExpiryDate = ExpiryDate(10, 4, 2027),
        categoryId: Long = 1,
        id: Long? = null,
    ) = ItemDraft(id = id, name = name, categoryId = categoryId, expiry = expiry)

    @Test
    fun `no match at all is not a duplicate`() = runTest {
        val check = CheckForDuplicate(FakeItemRepository(listOf(existing)))
        assertEquals(DuplicateCheck.None, check(draft(name = "Oat Milk")))
    }

    @Test
    fun `same name, category and date is an exact duplicate`() = runTest {
        val check = CheckForDuplicate(FakeItemRepository(listOf(existing)))
        val result = check(draft())
        assertIs<DuplicateCheck.Exact>(result)
        assertEquals(existing.id, result.existing.id)
    }

    @Test
    fun `same name and category but a different date is flagged separately`() = runTest {
        // Restocking the same product is legitimate, so this is a softer warning than Exact.
        val check = CheckForDuplicate(FakeItemRepository(listOf(existing)))
        val result = check(draft(expiry = ExpiryDate(20, 5, 2027)))
        assertIs<DuplicateCheck.SameNameDifferentDate>(result)
    }

    @Test
    fun `same name in another category is flagged separately again`() = runTest {
        val check = CheckForDuplicate(FakeItemRepository(listOf(existing)))
        val result = check(draft(categoryId = 2))
        assertIs<DuplicateCheck.SameNameDifferentCategory>(result)
    }

    @Test
    fun `editing an item never conflicts with itself`() = runTest {
        // Without the id filter, saving an unchanged edit would accuse the user of
        // duplicating the row they are editing.
        val check = CheckForDuplicate(FakeItemRepository(listOf(existing)))
        assertEquals(DuplicateCheck.None, check(draft(id = existing.id)))
    }

    @Test
    fun `name matching ignores case`() = runTest {
        val check = CheckForDuplicate(FakeItemRepository(listOf(existing)))
        assertIs<DuplicateCheck.Exact>(check(draft(name = "whole milk")))
    }

    @Test
    fun `an exact match wins over a weaker one, whatever order they are stored in`() = runTest {
        val weaker = testItem(id = 2, name = "Whole Milk", expiry = ExpiryDate(1, 1, 2028), categoryId = 1)
        val check = CheckForDuplicate(FakeItemRepository(listOf(weaker, existing)))
        val result = check(draft())
        assertIs<DuplicateCheck.Exact>(result)
        assertEquals(existing.id, result.existing.id)
    }
}
