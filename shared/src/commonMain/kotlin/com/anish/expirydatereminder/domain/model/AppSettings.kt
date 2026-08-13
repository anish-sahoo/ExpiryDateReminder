package com.anish.expirydatereminder.domain.model

/**
 * How dates are rendered.
 *
 * Values 1 and 2 match the legacy `dateFormatTable.format` encoding exactly, so imported
 * settings need no remapping. Everything from 3 upwards is new in 2.0 and covers the
 * conventions the pre-2.0 app ignored: ISO, dotted European, and East Asian year-first.
 *
 * [dayMonthOrder] is what the OCR parser consults to disambiguate `03/04/2027`.
 */
enum class DateFormat(val legacyValue: Int, val dayMonthOrder: DayMonthOrder) {
    /** 08/31/2027 — United States. */
    MONTH_FIRST(1, DayMonthOrder.MONTH_FIRST),

    /** 31/08/2027 — UK, Ireland, much of Latin America, India. */
    DAY_FIRST(2, DayMonthOrder.DAY_FIRST),

    /** 2027-08-31 — ISO 8601, and the default in Sweden and much of IT. */
    ISO(3, DayMonthOrder.DAY_FIRST),

    /** 31.08.2027 — Germany, Austria, Switzerland, Russia, Poland, Finland. */
    DAY_FIRST_DOTTED(4, DayMonthOrder.DAY_FIRST),

    /** 2027/08/31 — Japan, China, Korea, Taiwan. */
    YEAR_FIRST_SLASH(5, DayMonthOrder.DAY_FIRST),

    /** 31 Aug 2027 — unambiguous, useful when a user simply wants no confusion. */
    DAY_MONTH_NAME(6, DayMonthOrder.DAY_FIRST),
    ;

    companion object {
        fun fromLegacy(value: Int): DateFormat = entries.firstOrNull { it.legacyValue == value } ?: DAY_FIRST
    }
}

/** Which component leads when both could be a month. */
enum class DayMonthOrder { DAY_FIRST, MONTH_FIRST }

/** The three components of a calendar date, in whatever order a format arranges them. */
enum class DatePart { DAY, MONTH, YEAR }

/**
 * Field order and separator for each format.
 *
 * The entry form uses this so its inputs appear in the order the user actually reads
 * dates in; a setting that only changed display while the form stayed DD/MM/YYYY would be
 * half a feature.
 */
val DateFormat.parts: List<DatePart>
    get() =
        when (this) {
            DateFormat.MONTH_FIRST -> listOf(DatePart.MONTH, DatePart.DAY, DatePart.YEAR)
            DateFormat.DAY_FIRST, DateFormat.DAY_FIRST_DOTTED, DateFormat.DAY_MONTH_NAME ->
                listOf(DatePart.DAY, DatePart.MONTH, DatePart.YEAR)
            DateFormat.ISO, DateFormat.YEAR_FIRST_SLASH ->
                listOf(DatePart.YEAR, DatePart.MONTH, DatePart.DAY)
        }

val DateFormat.separator: String
    get() =
        when (this) {
            DateFormat.ISO -> "-"
            DateFormat.DAY_FIRST_DOTTED -> "."
            DateFormat.DAY_MONTH_NAME -> " "
            else -> "/"
        }

/** Human-readable pattern, e.g. `MM/DD/YYYY`, shown beside the worked example. */
val DateFormat.pattern: String
    get() {
        val tokens =
            parts.map {
                when (it) {
                    DatePart.DAY -> "DD"
                    DatePart.MONTH -> if (this == DateFormat.DAY_MONTH_NAME) "MMM" else "MM"
                    DatePart.YEAR -> "YYYY"
                }
            }
        return tokens.joinToString(separator)
    }

data class AppSettings(
    val dateFormat: DateFormat = DateFormat.DAY_FIRST,
    val notificationsEnabled: Boolean = true,
    val reminderLeadDays: Int = ExpiryStatus.DEFAULT_SOON_DAYS,
    val reminderHour: Int = DEFAULT_REMINDER_HOUR,
) {
    companion object {
        /** Matches the 07:00 anchor the legacy AlarmManager schedule used. */
        const val DEFAULT_REMINDER_HOUR = 7
    }
}

data class MigrationSummary(
    val migratedAt: Long,
    val itemCount: Int,
    val categoryCount: Int,
    val orphanCount: Int,
    val imageCount: Int,
)
