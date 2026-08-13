package com.anish.expirydatereminder.domain.model

import kotlinx.datetime.LocalDate

/**
 * Day arithmetic for the reminder and widget windows.
 *
 * Goes through epoch days rather than a period API so it stays a pure integer offset: these
 * are calendar-day windows around "today", where landing on a real date matters more than
 * honoring month lengths. Shared rather than redeclared per caller, which is how the widget
 * and the reminder worker drifted apart in the first place.
 */
fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)

fun LocalDate.minusDays(days: Int): LocalDate = plusDays(-days)
