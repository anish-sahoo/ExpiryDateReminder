package com.anish.expirydatereminder.domain.model

/**
 * Built-in categories are identified by a stable key rather than by their name, so the
 * displayed label can be localized without touching stored data.
 *
 * [legacyName] is the exact English string the pre-2.0 app wrote into `categoriesTable`,
 * and is what the importer matches on. It must not be changed.
 */
enum class BuiltinCategory(val key: String, val legacyName: String) {
    GROCERY("grocery", "Grocery"),
    FROZEN("frozen", "Frozen Items"),
    SNACKS("snacks", "Snacks"),
    MEDICINE("medicine", "Medicine"),
    IMPORTANT_DATES("important_dates", "Important Dates"),

    /**
     * Recipient for items whose category string matched nothing during import. Dropping
     * those items would be data loss, so they land here instead.
     */
    UNCATEGORIZED("uncategorized", "Uncategorized"),
    ;

    companion object {
        /**
         * The legacy "All Items" row was both a real category and the "show everything"
         * sentinel. In 2.0 it is a filter only, represented by a null category id, so it
         * is deliberately absent from this enum and skipped during import.
         */
        const val LEGACY_ALL_ITEMS = "All Items"

        fun fromKey(key: String?): BuiltinCategory? = key?.let { k -> entries.firstOrNull { it.key == k } }
    }
}

data class Category(val id: Long, val name: String, val builtinKey: String?, val isBuiltin: Boolean)
