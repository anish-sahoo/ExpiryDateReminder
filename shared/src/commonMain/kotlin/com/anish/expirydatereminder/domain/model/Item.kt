package com.anish.expirydatereminder.domain.model

import kotlinx.datetime.LocalDate

data class Item(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
    val categoryBuiltinKey: String?,
    val expiry: ExpiryDate,
    val imagePath: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun statusOn(today: LocalDate, soonThresholdDays: Int = ExpiryStatus.DEFAULT_SOON_DAYS): ExpiryStatus =
        ExpiryStatus.of(expiry, today, soonThresholdDays)

    fun daysUntilExpiry(today: LocalDate): Int =
        expiry.toLocalDate().toEpochDays().toInt() - today.toEpochDays().toInt()
}

/** Drives the list's urgency treatment, the single biggest gap in the pre-2.0 UI. */
enum class ExpiryStatus {
    EXPIRED,
    EXPIRING_SOON,
    OK,
    ;

    companion object {
        const val DEFAULT_SOON_DAYS = 14

        fun of(expiry: ExpiryDate, today: LocalDate, soonThresholdDays: Int = DEFAULT_SOON_DAYS): ExpiryStatus {
            val days = expiry.toLocalDate().toEpochDays() - today.toEpochDays()
            return when {
                days < 0 -> EXPIRED
                days <= soonThresholdDays -> EXPIRING_SOON
                else -> OK
            }
        }
    }
}

/** A new or edited item before it has an id. */
data class ItemDraft(
    val id: Long? = null,
    val name: String,
    val categoryId: Long,
    val expiry: ExpiryDate,
    val imagePath: String? = null,
    val notes: String? = null,
)
