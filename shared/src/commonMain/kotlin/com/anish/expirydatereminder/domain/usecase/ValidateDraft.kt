package com.anish.expirydatereminder.domain.usecase

import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.lengthOfMonth

/**
 * Field-level validation for the add/edit form.
 *
 * The legacy AddItemDialog validated in a cascade, showing one Toast at a time and
 * clearing the offending field. This returns every problem at once so the form can mark
 * fields inline as the user types.
 */
enum class DraftField { NAME, DAY, MONTH, YEAR }

enum class DraftError {
    NAME_BLANK,
    MONTH_REQUIRED,
    MONTH_OUT_OF_RANGE,
    DAY_OUT_OF_RANGE_FOR_MONTH,
    YEAR_OUT_OF_RANGE,
}

data class DraftValidation(val errors: Map<DraftField, DraftError> = emptyMap(), val expiry: ExpiryDate? = null) {
    val isValid: Boolean get() = errors.isEmpty() && expiry != null
}

/**
 * @param day blank defaults to the last day of the month, matching legacy behavior.
 * @param year blank defaults to [currentYear], matching legacy behavior.
 */
fun validateDraft(name: String, day: String, month: String, year: String, currentYear: Int): DraftValidation {
    val errors = mutableMapOf<DraftField, DraftError>()

    if (name.isBlank()) errors[DraftField.NAME] = DraftError.NAME_BLANK

    val monthValue = month.trim().toIntOrNull()
    when {
        month.isBlank() -> errors[DraftField.MONTH] = DraftError.MONTH_REQUIRED
        monthValue == null || monthValue !in 1..12 -> errors[DraftField.MONTH] = DraftError.MONTH_OUT_OF_RANGE
    }

    val yearValue = if (year.isBlank()) currentYear else year.trim().toIntOrNull()
    if (yearValue == null || yearValue !in ExpiryDate.MIN_YEAR..ExpiryDate.MAX_YEAR) {
        errors[DraftField.YEAR] = DraftError.YEAR_OUT_OF_RANGE
    }

    var dayValue: Int? = null
    if (monthValue != null &&
        monthValue in 1..12 &&
        yearValue != null &&
        yearValue in ExpiryDate.MIN_YEAR..ExpiryDate.MAX_YEAR
    ) {
        val maxDay = lengthOfMonth(yearValue, monthValue)
        dayValue = if (day.isBlank()) maxDay else day.trim().toIntOrNull()
        if (dayValue == null || dayValue !in 1..maxDay) {
            errors[DraftField.DAY] = DraftError.DAY_OUT_OF_RANGE_FOR_MONTH
            dayValue = null
        }
    }

    val expiry =
        if (errors.isEmpty() && dayValue != null && monthValue != null && yearValue != null) {
            ExpiryDate(dayValue, monthValue, yearValue)
        } else {
            null
        }

    return DraftValidation(errors, expiry)
}
