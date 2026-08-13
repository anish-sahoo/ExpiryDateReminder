package com.anish.expirydatereminder.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * A calendar date with no time component.
 *
 * Deliberately not an [kotlin.time.Instant]: an item expiring on 31 March expires on
 * 31 March everywhere, and storing an instant would shift that for anyone who changes
 * timezone. The legacy schema stored the same three integers, so migration is lossless.
 */
data class ExpiryDate(val day: Int, val month: Int, val year: Int) : Comparable<ExpiryDate> {
    /** Sortable and range-queryable as a single integer, e.g. 2027-03-31 -> 20270331. */
    val sortKey: Int get() = year * 10_000 + month * 100 + day

    fun toLocalDate(): LocalDate = LocalDate(year, month, day)

    override fun compareTo(other: ExpiryDate): Int = sortKey.compareTo(other.sortKey)

    companion object {
        const val MIN_YEAR = 1000
        const val MAX_YEAR = 9999

        fun from(date: LocalDate): ExpiryDate = ExpiryDate(date.day, date.month.number, date.year)

        fun fromSortKey(key: Int): ExpiryDate = ExpiryDate(
            day = key % 100,
            month = (key / 100) % 100,
            year =
            key / 10_000,
        )
    }
}

/** Days in [month] of [year], accounting for leap years. */
fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
}

fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
