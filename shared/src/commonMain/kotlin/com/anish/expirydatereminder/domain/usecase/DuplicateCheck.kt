package com.anish.expirydatereminder.domain.usecase

import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.repository.ItemRepository

/**
 * Replaces the legacy `SearchResult` enum. Same four outcomes, but as a sealed type so
 * the exact conflicting item travels with the result instead of being re-derived by the UI.
 */
sealed interface DuplicateCheck {
    data object None : DuplicateCheck

    data class Exact(val existing: Item) : DuplicateCheck

    data class SameNameDifferentDate(val existing: Item) : DuplicateCheck

    data class SameNameDifferentCategory(val existing: Item) : DuplicateCheck
}

class CheckForDuplicate(private val items: ItemRepository) {
    suspend operator fun invoke(draft: ItemDraft): DuplicateCheck {
        // An edit never conflicts with the row it is editing.
        val candidates = items.withName(draft.name).filter { it.id != draft.id }
        if (candidates.isEmpty()) return DuplicateCheck.None

        candidates
            .firstOrNull { it.categoryId == draft.categoryId && it.expiry == draft.expiry }
            ?.let { return DuplicateCheck.Exact(it) }

        candidates
            .firstOrNull { it.categoryId == draft.categoryId }
            ?.let { return DuplicateCheck.SameNameDifferentDate(it) }

        return DuplicateCheck.SameNameDifferentCategory(candidates.first())
    }
}
