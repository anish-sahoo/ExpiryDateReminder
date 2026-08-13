package com.anish.expirydatereminder.ui.items

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anish.expirydatereminder.camera.ScanCoordinator
import com.anish.expirydatereminder.domain.model.BuiltinCategory
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.domain.usecase.CheckForDuplicate
import com.anish.expirydatereminder.domain.usecase.DraftError
import com.anish.expirydatereminder.domain.usecase.DraftField
import com.anish.expirydatereminder.domain.usecase.DuplicateCheck
import com.anish.expirydatereminder.domain.usecase.validateDraft
import com.anish.expirydatereminder.images.ImageStore
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class ItemEditUiState(
    val itemId: Long? = null,
    val name: String = "",
    val day: String = "",
    val month: String = "",
    val year: String = "",
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val imagePath: String? = null,
    val notes: String = "",
    val dateFormat: DateFormat = DateFormat.DAY_FIRST,
    val errors: Map<DraftField, DraftError> = emptyMap(),
    val duplicate: DuplicateCheck = DuplicateCheck.None,
    val scanning: Boolean = false,
    val scanFoundNothing: Boolean = false,
    val scanAlternatives: List<ExpiryDate> = emptyList(),
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = itemId != null
}

class ItemEditViewModel(
    private val items: ItemRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val scanCoordinator: ScanCoordinator,
    private val imageStore: ImageStore,
) : ViewModel() {

    private val checkForDuplicate = CheckForDuplicate(items)

    private val _state = MutableStateFlow(ItemEditUiState())
    val state: StateFlow<ItemEditUiState> = _state.asStateFlow()

    /**
     * Starts a fresh form.
     *
     * This view model outlives the sheet — it is scoped to the activity, not to a back stack
     * entry — so without this, reopening shows whatever the last save left behind. The reset
     * is synchronous rather than inside the coroutine so the blank form is in place before
     * anything renders.
     *
     * It is not, on its own, enough to stop a stale `saved` from closing the sheet: an effect
     * reading `saved` sees the composition's snapshot, not the live flow, so it fires before
     * this reset reaches it. [consumeSaved] is what actually closes that hole.
     */
    fun load(itemId: Long?) {
        _state.value = ItemEditUiState()
        viewModelScope.launch {
            val available = categories.all()
            val format = settings.current().dateFormat
            val existing = itemId?.let { items.byId(it) }
            _state.update {
                if (existing == null) {
                    // Default to Uncategorized: picking a category should be a deliberate
                    // act, not something the user inherits from list ordering.
                    val fallback = available.firstOrNull { c -> c.builtinKey == BuiltinCategory.UNCATEGORIZED.key }
                        ?: available.firstOrNull()
                    it.copy(categories = available, categoryId = it.categoryId ?: fallback?.id, dateFormat = format)
                } else {
                    it.copy(
                        itemId = existing.id,
                        name = existing.name,
                        day = existing.expiry.day.toString(),
                        month = existing.expiry.month.toString(),
                        year = existing.expiry.year.toString(),
                        categoryId = existing.categoryId,
                        imagePath = existing.imagePath,
                        notes = existing.notes.orEmpty(),
                        categories = available,
                        dateFormat = format,
                    )
                }
            }
        }
    }

    /**
     * Acknowledges the save, so the flag cannot fire twice.
     *
     * `saved` is a one-shot signal that happens to be carried in the state, and the screen
     * reacts to it by toasting and closing. Left set, it fires again the next time the sheet
     * opens — and because the effect reads the composition's snapshot rather than the live
     * flow, clearing it in [load] alone is too late: the sheet closes in the frame it opened.
     * The consumer clearing it the moment it acts on it is what makes that impossible.
     */
    fun consumeSaved() = _state.update { it.copy(saved = false) }

    fun setName(value: String) = _state.update { it.copy(name = value, errors = it.errors - DraftField.NAME) }
    fun setDay(value: String) = _state.update { it.copy(day = value.digits(2), errors = it.errors - DraftField.DAY) }
    fun setMonth(value: String) = _state.update {
        it.copy(month = value.digits(2), errors = it.errors - DraftField.MONTH)
    }
    fun setYear(value: String) = _state.update { it.copy(year = value.digits(4), errors = it.errors - DraftField.YEAR) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun setExpiry(date: ExpiryDate) = _state.update {
        it.copy(
            day = date.day.toString(),
            month = date.month.toString(),
            year = date.year.toString(),
            errors = it.errors - DraftField.DAY - DraftField.MONTH - DraftField.YEAR,
            scanAlternatives = emptyList(),
        )
    }

    /**
     * Prefills the form from a camera frame. Never saves anything and never blocks: a
     * failed scan leaves the fields exactly as the user left them.
     */
    fun scan(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, scanFoundNothing = false) }
            val outcome = scanCoordinator.scan(bitmap)
            _state.update { current ->
                current.copy(
                    scanning = false,
                    scanFoundNothing = outcome.foundNothing,
                    scanAlternatives = outcome.alternatives,
                    name = if (current.name.isBlank()) outcome.suggestedName ?: current.name else current.name,
                    day = outcome.prefill?.day?.toString() ?: current.day,
                    month = outcome.prefill?.month?.toString() ?: current.month,
                    year = outcome.prefill?.year?.toString() ?: current.year,
                )
            }
        }
    }

    fun attachPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            val id = _state.value.itemId ?: PENDING_PHOTO_ID
            val bytes = java.io.ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, out)
                out.toByteArray()
            }
            imageStore.write(id, bytes)?.let { path -> _state.update { it.copy(imagePath = path) } }
        }
    }

    fun removePhoto() {
        val path = _state.value.imagePath ?: return
        imageStore.delete(path)
        _state.update { it.copy(imagePath = null) }
    }

    fun save(force: Boolean = false) {
        val current = _state.value
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val validation = validateDraft(
            name = current.name,
            day = current.day,
            month = current.month,
            year = current.year,
            currentYear = today.year,
        )
        val categoryId = current.categoryId
        val expiry = validation.expiry
        if (!validation.isValid || expiry == null || categoryId == null) {
            _state.update { it.copy(errors = validation.errors) }
            return
        }

        val draft = ItemDraft(
            id = current.itemId,
            name = current.name.trim(),
            categoryId = categoryId,
            expiry = expiry,
            imagePath = current.imagePath,
            notes = current.notes,
        )

        viewModelScope.launch {
            if (!force) {
                val duplicate = checkForDuplicate(draft)
                if (duplicate != DuplicateCheck.None) {
                    _state.update { it.copy(duplicate = duplicate, errors = emptyMap()) }
                    return@launch
                }
            }
            if (draft.id == null) items.add(draft) else items.update(draft)
            _state.update { it.copy(saved = true, duplicate = DuplicateCheck.None) }
        }
    }

    fun dismissDuplicate() = _state.update { it.copy(duplicate = DuplicateCheck.None) }

    fun dismissScanHint() = _state.update { it.copy(scanFoundNothing = false) }

    private fun String.digits(max: Int) = filter { it.isDigit() }.take(max)

    private companion object {
        const val PHOTO_QUALITY = 90

        /** Photos captured before the row exists are keyed on 0 and renamed on save. */
        const val PENDING_PHOTO_ID = 0L
    }
}
