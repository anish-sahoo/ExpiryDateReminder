package com.anish.expirydatereminder.domain

import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.ExpiryStatus
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class ItemDaysUntilExpiryTest {

    private val today = LocalDate(2027, 3, 15)

    @Test
    fun `counts forwards, backwards and zero on the day itself`() {
        // This number is what the list badge and the widget both render, so an off-by-one
        // here shows up as "-1d" on something that expires today.
        assertEquals(0, testItem(expiry = ExpiryDate(15, 3, 2027)).daysUntilExpiry(today))
        assertEquals(1, testItem(expiry = ExpiryDate(16, 3, 2027)).daysUntilExpiry(today))
        assertEquals(-1, testItem(expiry = ExpiryDate(14, 3, 2027)).daysUntilExpiry(today))
    }

    @Test
    fun `counts across a year boundary`() {
        val item = testItem(expiry = ExpiryDate(1, 1, 2028))
        assertEquals(292, item.daysUntilExpiry(today))
    }
}

class ExpiryStatusThresholdTest {

    private val today = LocalDate(2027, 3, 15)

    @Test
    fun `the soon threshold is inclusive at both ends`() {
        // The boundary decides which colored group an item lands in, so both edges are
        // pinned rather than left to a "roughly two weeks" assumption.
        val lead = 14
        assertEquals(ExpiryStatus.EXPIRED, ExpiryStatus.of(ExpiryDate(14, 3, 2027), today, lead))
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryStatus.of(ExpiryDate(15, 3, 2027), today, lead))
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryStatus.of(ExpiryDate(29, 3, 2027), today, lead))
        assertEquals(ExpiryStatus.OK, ExpiryStatus.of(ExpiryDate(30, 3, 2027), today, lead))
    }

    @Test
    fun `honors a custom lead time from settings`() {
        // Someone who sets a one-day lead should see almost everything as OK.
        assertEquals(ExpiryStatus.OK, ExpiryStatus.of(ExpiryDate(20, 3, 2027), today, soonThresholdDays = 1))
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryStatus.of(ExpiryDate(16, 3, 2027), today, soonThresholdDays = 1))
    }

    @Test
    fun `statusOn agrees with the companion`() {
        val item = testItem(expiry = ExpiryDate(20, 3, 2027))
        assertEquals(ExpiryStatus.of(item.expiry, today, 14), item.statusOn(today, 14))
    }
}
