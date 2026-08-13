package com.anish.expirydatereminder.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anish.expirydatereminder.domain.Today
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.domain.usecase.ItemSort
import com.anish.expirydatereminder.domain.usecase.matching
import com.anish.expirydatereminder.images.ImageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class ItemListUiState(
    val loading: Boolean = true,
    val items: List<Item> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val query: String = "",
    val sort: ItemSort = ItemSort.BY_EXPIRY,
    val settings: AppSettings = AppSettings(),
    val today: LocalDate,
) {
    val isEmptyOverall: Boolean get() = !loading && items.isEmpty() && query.isBlank() && selectedCategoryId == null
    val hasNoMatches: Boolean get() = !loading && items.isEmpty() && (query.isNotBlank() || selectedCategoryId != null)
}

/** Emitted once and consumed by the UI, so an undo snackbar cannot reappear on rotation. */
sealed interface ItemListEvent {
    data class Deleted(val item: Item) : ItemListEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ItemListViewModel(
    private val items: ItemRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    private val imageStore: ImageStore,
    private val today: Today,
) : ViewModel() {

    private val filter = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(ItemSort.BY_EXPIRY)

    private val _events = MutableStateFlow<ItemListEvent?>(null)
    val events: StateFlow<ItemListEvent?> = _events

    val state: StateFlow<ItemListUiState> = combine(
        filter.flatMapLatest { items.observeItems(it) },
        categories.observeCategories(),
        settings.observeSettings(),
        query,
        sort,
    ) { itemList, categoryList, appSettings, currentQuery, currentSort ->
        ItemListUiState(
            loading = false,
            items = currentSort.apply(itemList.matching(currentQuery)),
            categories = categoryList,
            selectedCategoryId = filter.value,
            query = currentQuery,
            sort = currentSort,
            settings = appSettings,
            today = today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ItemListUiState(today = today()))

    fun selectCategory(categoryId: Long?) {
        filter.value = categoryId
    }

    fun search(text: String) {
        query.value = text
    }

    fun toggleSort() {
        sort.update { if (it == ItemSort.BY_EXPIRY) ItemSort.BY_NAME else ItemSort.BY_EXPIRY }
    }

    /**
     * Deletes immediately and surfaces an undo, rather than blocking on a confirmation
     * dialog the way the pre-2.0 long-press flow did. The photo is kept until the undo
     * window closes so a restore is lossless.
     */
    fun delete(item: Item) {
        viewModelScope.launch {
            items.delete(item.id)
            _events.value = ItemListEvent.Deleted(item)
        }
    }

    fun undoDelete(item: Item) {
        viewModelScope.launch {
            items.add(
                ItemDraft(
                    name = item.name,
                    categoryId = item.categoryId,
                    expiry = item.expiry,
                    imagePath = item.imagePath,
                ),
            )
            _events.value = null
        }
    }

    /** Called once the undo snackbar is gone for good. */
    fun confirmDelete(item: Item) {
        viewModelScope.launch {
            imageStore.delete(item.imagePath)
            _events.value = null
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
