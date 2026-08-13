package com.anish.expirydatereminder.testing

import com.anish.expirydatereminder.domain.Today
import kotlinx.datetime.LocalDate

/**
 * A fixed "today" for tests.
 *
 * Mid-month and mid-year on purpose: a date near either boundary makes arithmetic like
 * "five days from now" roll over, and a test written on the 28th quietly behaves
 * differently in February.
 */
val TEST_TODAY = LocalDate(2026, 6, 15)

fun fixedToday(date: LocalDate = TEST_TODAY) = Today { date }
