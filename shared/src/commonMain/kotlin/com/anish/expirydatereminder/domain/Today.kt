package com.anish.expirydatereminder.domain

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * What day it is, as a dependency rather than a fact of the environment.
 *
 * Every screen in this app is about how far away a date is, so "today" is an input to
 * almost every assertion. Reading it from the system clock inside the code under test makes
 * those assertions depend on when and where they run: a test that seeds an item five days
 * out passes in one timezone and fails in another, and one written around a month boundary
 * fails for a week a year later.
 *
 * The date rather than the instant, deliberately. An instant still needs a timezone to
 * become a date, and that is the half of the problem that is easy to forget.
 */
fun interface Today {
    operator fun invoke(): LocalDate
}

/** The real one. Everything outside tests uses this. */
class SystemToday(
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : Today {
    override fun invoke(): LocalDate = clock.todayIn(zone)
}
