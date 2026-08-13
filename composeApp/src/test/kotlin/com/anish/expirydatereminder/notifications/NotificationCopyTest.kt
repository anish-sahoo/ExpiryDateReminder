package com.anish.expirydatereminder.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.plusDays
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reminder's wording.
 *
 * Robolectric rather than fakes because the whole point of this class is reading plurals out
 * of real resources, and a plural that is missing for one of the seven shipping languages
 * would only show up here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationCopyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate(2027, 3, 15)

    private fun itemsExpiring(vararg dayOffsets: Int) = dayOffsets.mapIndexed { index, offset ->
        testItem(
            id = index + 1L,
            name = "Item ${index + 1}",
            expiry = ExpiryDate.from(today.plusDays(offset)),
        )
    }

    @Test
    fun `anything already expired sets the tone, even alongside future items`() {
        assertEquals(
            NotificationCopy.Tone.ALREADY_EXPIRED,
            NotificationCopy.toneFor(itemsExpiring(-2, 5), today),
        )
    }

    @Test
    fun `something due today outranks something due later`() {
        assertEquals(NotificationCopy.Tone.DUE_TODAY, NotificationCopy.toneFor(itemsExpiring(0, 5), today))
    }

    @Test
    fun `everything in the future is the gentlest tone`() {
        assertEquals(NotificationCopy.Tone.DUE_SOON, NotificationCopy.toneFor(itemsExpiring(3, 5), today))
    }

    @Test
    fun `a single item is named, because that reads as attentive`() {
        val item = testItem(id = 1, name = "Greek Yoghurt", expiry = ExpiryDate(14, 3, 2027))
        val copy = NotificationCopy.build(context, listOf(item), today, leadDays = 14)
        assertTrue(copy.body.contains("Greek Yoghurt"), "expected the name in: ${copy.body}")
    }

    @Test
    fun `several items fall back to impersonal wording`() {
        // Naming one of five would look arbitrary.
        val copy = NotificationCopy.build(context, itemsExpiring(-1, -2, -3), today, leadDays = 14)
        assertTrue(!copy.body.contains("Item 1"), "expected no single name in: ${copy.body}")
    }

    @Test
    fun `the count reflects the tone, not the whole list`() {
        // Three expired plus two upcoming should say three, not five: the title is about
        // what needs acting on now.
        val copy = NotificationCopy.build(context, itemsExpiring(-1, -2, -3, 5, 6), today, leadDays = 14)
        assertTrue(copy.title.contains("3"), "expected a count of 3 in: ${copy.title}")
    }

    @Test
    fun `wording is stable within a day and changes between days`() {
        // Seeded by the date so a worker that runs twice does not show two messages, but a
        // daily reminder does not read identically forever.
        val items = itemsExpiring(-1, -2)
        val first = NotificationCopy.build(context, items, today, leadDays = 14)
        val again = NotificationCopy.build(context, items, today, leadDays = 14)
        assertEquals(first, again)

        val laterTitles = (1..20).map {
            NotificationCopy.build(context, items, today.plusDays(it), 14).title
        }
        assertTrue(laterTitles.toSet().size > 1, "wording never varied across 20 days")
    }

    @Test
    fun `never produces empty text`() {
        // An empty title renders as a blank notification row rather than an error.
        listOf(itemsExpiring(-1), itemsExpiring(0), itemsExpiring(4)).forEach { items ->
            val copy = NotificationCopy.build(context, items, today, leadDays = 14)
            assertTrue(copy.title.isNotBlank())
            assertTrue(copy.body.isNotBlank())
        }
    }

    @Test
    fun `an empty list still produces usable text rather than crashing`() {
        // The worker guards against this, but a count of zero must not reach getQuantityString
        // as a negative or blow up on firstOrNull.
        val copy = NotificationCopy.build(context, emptyList(), today, leadDays = 14)
        assertTrue(copy.title.isNotBlank())
        assertTrue(copy.body.isNotBlank())
    }
}
