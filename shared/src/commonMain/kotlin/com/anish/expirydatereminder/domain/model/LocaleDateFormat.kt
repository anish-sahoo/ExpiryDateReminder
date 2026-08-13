package com.anish.expirydatereminder.domain.model

/**
 * The date format a fresh install should start on, given the device's country.
 *
 * A first-run default, never an override: once someone has been through Settings their choice
 * is what counts, and this is not consulted again.
 *
 * Country rather than language, because date convention follows where you are rather than what
 * you speak — Swiss German writes 31.08.2027 while German German does too, but English in the
 * US and the UK disagree entirely.
 */
fun dateFormatForCountry(country: String): DateFormat = when (country.uppercase()) {
    "US" -> DateFormat.MONTH_FIRST

    // Dotted day-first: German-speaking Europe, the Nordics and much of central Europe.
    "DE", "AT", "CH", "RU", "PL", "FI", "CZ", "SK", "NO", "DK", "EE", "LV" -> DateFormat.DAY_FIRST_DOTTED

    // Year first, slash separated, across East Asia.
    "JP", "CN", "TW", "KR" -> DateFormat.YEAR_FIRST_SLASH

    // Sweden and Lithuania write ISO natively.
    "SE", "LT" -> DateFormat.ISO

    // Day first covers the UK, Ireland, India, Australia, most of Latin America and the rest
    // of Europe, and is the safest fallback for anywhere unlisted.
    else -> DateFormat.DAY_FIRST
}
