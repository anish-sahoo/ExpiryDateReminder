package com.anish.expirydatereminder.parser

import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.DayMonthOrder
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.lengthOfMonth

/**
 * A date found in OCR output, with the evidence that produced it.
 *
 * [confidence] is 0.0..1.0. The UI only auto-fills above
 * [DateParser.AUTOFILL_CONFIDENCE_THRESHOLD]; below that the candidates are offered as
 * chips instead. Prefilling nothing is always better than prefilling a wrong date the
 * user does not notice.
 */
data class DateCandidate(
    val date: ExpiryDate,
    val confidence: Double,
    val rawText: String,
    val evidence: Evidence,
    val dayMonthAmbiguous: Boolean,
) {
    enum class Evidence {
        /** An expiry keyword sat immediately before the date. */
        EXPIRY_KEYWORD,

        /** No keyword, but the date is plausible and unambiguous. */
        UNANCHORED,

        /** A manufacture or lot keyword sat before the date; kept only to be excluded. */
        NEGATIVE_KEYWORD,
    }
}

data class ParseResult(val candidates: List<DateCandidate>, val best: DateCandidate?) {
    val shouldAutofill: Boolean
        get() = best != null && best.confidence >= DateParser.AUTOFILL_CONFIDENCE_THRESHOLD

    val isAmbiguous: Boolean
        get() = candidates.size > 1 || best?.dayMonthAmbiguous == true
}

/**
 * Extracts expiry dates from OCR text.
 *
 * Pure Kotlin with no platform dependencies, so the whole thing is JVM-unit-testable
 * without an emulator. Deliberately conservative: it would rather return nothing than
 * return a confident wrong answer, because the user sees the prefilled value in an
 * editable field and a plausible-but-wrong date is the only genuinely harmful outcome.
 */
class DateParser(
    private val currentYear: Int,
    /** Disambiguates 03/04/2027. Falls back to the user's own display preference. */
    private val preferredFormat: DateFormat = DateFormat.DAY_FIRST,
) {
    /** Long enough for the longest marker plus the separator that follows it. */
    private val contextWindow = ExpiryKeywords.longestKeywordLength + KEYWORD_WINDOW_SLACK

    fun parse(ocrText: String): ParseResult {
        val text = ExpiryKeywords.normalize(ocrText)
        val candidates =
            buildList {
                addAll(findNumericDates(text))
                addAll(findMonthNameDates(text))
                addAll(findMonthYearOnly(text))
            }.filter { it.evidence != DateCandidate.Evidence.NEGATIVE_KEYWORD }
                .distinctBy { it.date.sortKey }
                .sortedByDescending { it.confidence }

        return ParseResult(candidates = candidates, best = candidates.firstOrNull())
    }

    // ---- pattern matching -------------------------------------------------------

    private fun findNumericDates(text: String): List<DateCandidate> = NUMERIC_DATE
        .findAll(text)
        .mapNotNull { match ->
            val (a, b, rawYear) = match.destructured
            val first = a.toIntOrNull() ?: return@mapNotNull null
            val second = b.toIntOrNull() ?: return@mapNotNull null
            val year = expandYear(rawYear.toIntOrNull() ?: return@mapNotNull null)

            val (day, month, ambiguous) = disambiguate(first, second) ?: return@mapNotNull null
            build(day, month, year, match, text, ambiguous)
        }.toList()

    private fun findMonthNameDates(text: String): List<DateCandidate> = MONTH_NAME_DATE
        .findAll(text)
        .mapNotNull { match ->
            val (rawDay, monthWord, rawYear) = match.destructured
            val month = ExpiryKeywords.MONTH_NAMES[monthWord] ?: return@mapNotNull null
            val year = expandYear(rawYear.toIntOrNull() ?: return@mapNotNull null)
            val day = rawDay.toIntOrNull() ?: return@mapNotNull null
            build(day, month, year, match, text, ambiguous = false)
        }.toList()

    /** `EXP 03/2027`, `MHD 03.27`, `BEST BEFORE MAR 2027` -> last day of that month. */
    private fun findMonthYearOnly(text: String): List<DateCandidate> {
        val numeric =
            MONTH_YEAR.findAll(text).mapNotNull { match ->
                val (rawMonth, rawYear) = match.destructured
                val month = rawMonth.toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
                val year = expandYear(rawYear.toIntOrNull() ?: return@mapNotNull null)
                build(lengthOfMonth(year, month), month, year, match, text, ambiguous = false)
            }
        val named =
            MONTH_NAME_YEAR.findAll(text).mapNotNull { match ->
                val (monthWord, rawYear) = match.destructured
                val month = ExpiryKeywords.MONTH_NAMES[monthWord] ?: return@mapNotNull null
                val year = expandYear(rawYear.toIntOrNull() ?: return@mapNotNull null)
                build(lengthOfMonth(year, month), month, year, match, text, ambiguous = false)
            }
        return (numeric + named).toList()
    }

    // ---- scoring ----------------------------------------------------------------

    private fun build(
        day: Int,
        month: Int,
        year: Int,
        match: MatchResult,
        text: String,
        ambiguous: Boolean,
    ): DateCandidate? {
        if (month !in 1..12) return null
        if (day !in 1..lengthOfMonth(year, month)) return null
        val date = ExpiryDate(day, month, year)
        if (!isPlausible(date)) return null

        val evidence = classifyContext(text, match.range.first)
        var confidence =
            when (evidence) {
                DateCandidate.Evidence.EXPIRY_KEYWORD -> ANCHORED_CONFIDENCE
                DateCandidate.Evidence.UNANCHORED -> UNANCHORED_CONFIDENCE
                DateCandidate.Evidence.NEGATIVE_KEYWORD -> 0.0
            }
        if (ambiguous) confidence -= AMBIGUITY_PENALTY
        // A date already in the past is far more likely a misread or a manufacture date.
        if (date.year < currentYear) confidence -= PAST_YEAR_PENALTY

        return DateCandidate(
            date = date,
            confidence = confidence.coerceIn(0.0, 1.0),
            rawText = match.value.trim(),
            evidence = evidence,
            dayMonthAmbiguous = ambiguous,
        )
    }

    /**
     * Looks backwards from the date for the nearest keyword. Negative markers win ties
     * so that `MFG 03/25 EXP 03/27` does not treat the manufacture date as an expiry.
     */
    private fun classifyContext(text: String, dateStart: Int): DateCandidate.Evidence {
        val windowStart = (dateStart - contextWindow).coerceAtLeast(0)
        val before = text.substring(windowStart, dateStart)

        val negative =
            (ExpiryKeywords.MANUFACTURE + ExpiryKeywords.LOT)
                .mapNotNull { kw -> before.lastIndexOf(kw).takeIf { it >= 0 }?.let { it + kw.length } }
                .maxOrNull()
        val positive =
            ExpiryKeywords.EXPIRY
                .mapNotNull { kw -> before.lastIndexOf(kw).takeIf { it >= 0 }?.let { it + kw.length } }
                .maxOrNull()

        return when {
            positive != null && (negative == null || positive > negative) -> DateCandidate.Evidence.EXPIRY_KEYWORD
            negative != null -> DateCandidate.Evidence.NEGATIVE_KEYWORD
            else -> DateCandidate.Evidence.UNANCHORED
        }
    }

    /** Rejects OCR misreads like a date 60 years out, and anything absurdly old. */
    private fun isPlausible(date: ExpiryDate): Boolean =
        date.year in (currentYear - MAX_YEARS_PAST)..(currentYear + MAX_YEARS_FUTURE)

    private fun disambiguate(first: Int, second: Int): Triple<Int, Int, Boolean>? = when {
        // Only one ordering yields a valid month.
        first > 12 && second in 1..12 -> Triple(first, second, false)
        second > 12 && first in 1..12 -> Triple(second, first, false)
        // Both plausible as months: fall back to the user's preference, and flag it.
        first in 1..12 && second in 1..12 ->
            when (preferredFormat.dayMonthOrder) {
                DayMonthOrder.DAY_FIRST -> Triple(first, second, first != second)
                DayMonthOrder.MONTH_FIRST -> Triple(second, first, first != second)
            }
        else -> null
    }

    private fun expandYear(raw: Int): Int = when {
        raw >= 100 -> raw
        // A two-digit year maps into the century window around now, so 27 -> 2027
        // and 99 does not become 2099 when the current year is 2026.
        else -> {
            val century = (currentYear / 100) * 100
            val candidate = century + raw
            if (candidate < currentYear - TWO_DIGIT_LOOKBACK) candidate + 100 else candidate
        }
    }

    companion object {
        /** Below this, the form offers chips instead of filling the field. */
        const val AUTOFILL_CONFIDENCE_THRESHOLD = 0.7

        private const val ANCHORED_CONFIDENCE = 0.95
        private const val UNANCHORED_CONFIDENCE = 0.55
        private const val AMBIGUITY_PENALTY = 0.2
        private const val PAST_YEAR_PENALTY = 0.3
        private const val KEYWORD_WINDOW_SLACK = 4
        private const val MAX_YEARS_FUTURE = 15
        private const val MAX_YEARS_PAST = 5
        private const val TWO_DIGIT_LOOKBACK = 10

        private val NUMERIC_DATE = Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})\b""")
        private val MONTH_YEAR = Regex("""\b(\d{1,2})[./\-](\d{2,4})\b""")
        private val MONTH_NAME_DATE = Regex("""\b(\d{1,2})[\s.\-]*([A-Z]{3,12})[\s.\-]*(\d{2,4})\b""")
        private val MONTH_NAME_YEAR = Regex("""\b([A-Z]{3,12})[\s.\-]+(\d{2,4})\b""")
    }
}
