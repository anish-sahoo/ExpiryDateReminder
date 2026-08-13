package com.anish.expirydatereminder.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.BuiltinCategory
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.ExpiryDate

/**
 * Built-in category names live in the database as English text. Resolving them through
 * string resources here is what lets them appear translated without rewriting user data.
 * User-created categories render their stored name verbatim.
 */
@Composable
fun categoryDisplayName(builtinKey: String?, storedName: String): String {
    val resource = when (BuiltinCategory.fromKey(builtinKey)) {
        BuiltinCategory.GROCERY -> R.string.category_grocery
        BuiltinCategory.FROZEN -> R.string.category_frozen
        BuiltinCategory.SNACKS -> R.string.category_snacks
        BuiltinCategory.MEDICINE -> R.string.category_medicine
        BuiltinCategory.IMPORTANT_DATES -> R.string.category_important_dates
        BuiltinCategory.UNCATEGORIZED -> R.string.category_uncategorized
        null -> return storedName
    }
    return stringResource(resource)
}

private val MONTH_ABBREVIATIONS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** Honors the user's explicit choice, defaulting from locale on a fresh install. */
fun ExpiryDate.format(dateFormat: DateFormat): String {
    val d = day.toString().padStart(2, '0')
    val m = month.toString().padStart(2, '0')
    return when (dateFormat) {
        DateFormat.MONTH_FIRST -> "$m/$d/$year"
        DateFormat.DAY_FIRST -> "$d/$m/$year"
        DateFormat.ISO -> "$year-$m-$d"
        DateFormat.DAY_FIRST_DOTTED -> "$d.$m.$year"
        DateFormat.YEAR_FIRST_SLASH -> "$year/$m/$d"
        DateFormat.DAY_MONTH_NAME -> "$day ${MONTH_ABBREVIATIONS[month - 1]} $year"
    }
}

/** A worked example, so the settings picker shows what each option actually looks like. */
fun DateFormat.sample(): String = ExpiryDate(31, 8, 2027).format(this)

/**
 * Relative phrasing beside the absolute date. The pre-2.0 list showed only
 * "<date> : <name>", which gave no sense of urgency at all.
 */
@Composable
fun relativeExpiry(daysUntil: Int): String = when {
    daysUntil == 0 -> stringResource(R.string.expiry_today)
    daysUntil == 1 -> stringResource(R.string.expiry_tomorrow)
    daysUntil > 0 -> pluralStringResource(R.plurals.expiry_in_days, daysUntil, daysUntil)
    else -> pluralStringResource(R.plurals.expiry_days_ago, -daysUntil, -daysUntil)
}

/**
 * Compact countdown for the list, e.g. `-4d`, `today`, `+3d`, `+8mo`.
 *
 * Deliberately terse: the list shows one of these per row, so a full sentence would crowd
 * out the item name. The detail screen still spells it out in words.
 */
fun compactRelative(daysUntil: Int): String = when {
    daysUntil == 0 -> "today"
    daysUntil in -DAY_LIMIT..DAY_LIMIT -> if (daysUntil < 0) "${daysUntil}d" else "+${daysUntil}d"
    daysUntil in -WEEK_LIMIT..WEEK_LIMIT -> {
        val weeks = daysUntil / 7
        if (weeks < 0) "${weeks}w" else "+${weeks}w"
    }
    daysUntil in -YEAR_LIMIT..YEAR_LIMIT -> {
        val months = daysUntil / 30
        if (months < 0) "${months}mo" else "+${months}mo"
    }
    else -> {
        val years = daysUntil / 365
        if (years < 0) "${years}y" else "+${years}y"
    }
}

private const val DAY_LIMIT = 13
private const val WEEK_LIMIT = 60
private const val YEAR_LIMIT = 364
