package com.anish.expirydatereminder.notifications

import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.plusDays
import com.anish.expirydatereminder.testing.FakeItemRepository
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test

/**
 * Which items a reminder would mention.
 *
 * The worker itself needs WorkManager and a notification manager, so this covers the query
 * that decides its content — the part that can silently include or drop the wrong items.
 */
class ReminderWindowTest {

    private val today = LocalDate(2027, 3, 15)

    private suspend fun due(leadDays: Int, items: List<com.anish.expirydatereminder.domain.model.Item>) =
        FakeItemRepository(items).expiringBetween(
            fromSortKey = ExpiryDate.from(today.minusDaysForWindow(leadDays)).sortKey,
            toSortKey = ExpiryDate.from(today.plusDays(leadDays)).sortKey,
        )

    private fun LocalDate.minusDaysForWindow(days: Int) = plusDays(-days)

    @Test
    fun `includes items inside the lead window in both directions`() = runTest {
        val items = listOf(
            testItem(id = 1, name = "Just expired", expiry = ExpiryDate.from(today.plusDays(-1))),
            testItem(id = 2, name = "Due today", expiry = ExpiryDate.from(today)),
            testItem(id = 3, name = "Due soon", expiry = ExpiryDate.from(today.plusDays(10))),
        )
        assertEquals(setOf(1L, 2L, 3L), due(14, items).map { it.id }.toSet())
    }

    @Test
    fun `excludes items beyond the lead window`() = runTest {
        val items = listOf(testItem(id = 1, expiry = ExpiryDate.from(today.plusDays(20))))
        assertTrue(due(14, items).isEmpty())
    }

    @Test
    fun `has a floor, so things expired years ago stop being counted`() = runTest {
        // The pre-2.0 receiver had no lower bound, so ancient items inflated the count
        // forever and the notification became noise people learned to ignore.
        val items = listOf(testItem(id = 1, expiry = ExpiryDate.from(today.plusDays(-400))))
        assertTrue(due(14, items).isEmpty())
    }

    @Test
    fun `a shorter lead time narrows both ends of the window`() = runTest {
        val items = listOf(
            testItem(id = 1, expiry = ExpiryDate.from(today.plusDays(-5))),
            testItem(id = 2, expiry = ExpiryDate.from(today.plusDays(5))),
            testItem(id = 3, expiry = ExpiryDate.from(today)),
        )
        assertEquals(setOf(3L), due(1, items).map { it.id }.toSet())
    }

    @Test
    fun `the window is inclusive at both edges`() = runTest {
        val items = listOf(
            testItem(id = 1, expiry = ExpiryDate.from(today.plusDays(-14))),
            testItem(id = 2, expiry = ExpiryDate.from(today.plusDays(14))),
        )
        assertEquals(setOf(1L, 2L), due(14, items).map { it.id }.toSet())
    }

    @Test
    fun `results come back soonest first, so the named item is the most urgent`() = runTest {
        val items = listOf(
            testItem(id = 1, expiry = ExpiryDate.from(today.plusDays(5))),
            testItem(id = 2, expiry = ExpiryDate.from(today.plusDays(-2))),
        )
        assertEquals(listOf(2L, 1L), due(14, items).map { it.id })
    }
}

class ReminderSettingsTest {

    @Test
    fun `the default reminder hour matches the legacy alarm`() {
        // Migrated users should keep getting their reminder at the time they are used to.
        assertEquals(7, AppSettings.DEFAULT_REMINDER_HOUR)
        assertEquals(7, AppSettings().reminderHour)
    }

    @Test
    fun `reminders are on by default, and the lead time matches the urgency threshold`() {
        // If these two ever disagreed, the list would color an item "expiring soon" that
        // the notification had not mentioned.
        val settings = AppSettings()
        assertTrue(settings.notificationsEnabled)
        assertEquals(
            com.anish.expirydatereminder.domain.model.ExpiryStatus.DEFAULT_SOON_DAYS,
            settings.reminderLeadDays,
        )
    }
}
