package com.anish.expirydatereminder.domain.usecase

import com.anish.expirydatereminder.domain.model.Item

enum class ItemSort {
    /** Soonest expiry first. The legacy default. */
    BY_EXPIRY,

    /** Case-insensitive, unlike the legacy `Comparator.comparing(ItemModel::getItemName)`. */
    BY_NAME,
    ;

    fun apply(items: List<Item>): List<Item> = when (this) {
        BY_EXPIRY -> items.sortedWith(compareBy({ it.expiry.sortKey }, { it.name.lowercase() }))
        BY_NAME -> items.sortedWith(compareBy({ it.name.lowercase() }, { it.expiry.sortKey }))
    }
}

/**
 * Substring search across name, notes and category.
 *
 * Folds diacritics on both sides so `creme` matches `Crème` and `muesli` matches `müsli`,
 * which matters given the app ships in seven European languages. Deliberately not FTS:
 * a home inventory is a few hundred rows at most, and this stays instant while keeping
 * the behavior obvious.
 */
fun List<Item>.matching(query: String): List<Item> {
    val needle = query.trim().foldForSearch()
    if (needle.isEmpty()) return this
    return filter { item ->
        item.name.foldForSearch().contains(needle) ||
            item.notes
                .orEmpty()
                .foldForSearch()
                .contains(needle) ||
            item.categoryName.foldForSearch().contains(needle)
    }
}

/** Lowercases and strips the diacritics used across the shipping locales. */
internal fun String.foldForSearch(): String {
    val sb = StringBuilder(length)
    for (ch in lowercase()) {
        sb.append(
            when (ch) {
                'á', 'à', 'â', 'ä', 'ã', 'å', 'ą' -> 'a'
                'é', 'è', 'ê', 'ë', 'ę' -> 'e'
                'í', 'ì', 'î', 'ï' -> 'i'
                'ó', 'ò', 'ô', 'ö', 'õ', 'ø' -> 'o'
                'ú', 'ù', 'û', 'ü' -> 'u'
                'ç', 'ć' -> 'c'
                'ñ', 'ń' -> 'n'
                'ł' -> 'l'
                'ś', 'š' -> 's'
                'ź', 'ż', 'ž' -> 'z'
                'ß' -> 's'
                else -> ch
            },
        )
    }
    return sb.toString()
}
