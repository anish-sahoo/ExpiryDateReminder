package com.anish.expirydatereminder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.MigrationSummary
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.migration.LegacyImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(val settings: AppSettings = AppSettings(), val categories: List<Category> = emptyList())

/** How many items a category deletion would take with it. */
data class PendingCategoryDelete(val category: Category, val itemCount: Int)

data class SettingsMessages(
    val categoryExists: Boolean = false,
    val importRunning: Boolean = false,
    val importFailed: Boolean = false,
    val importSummary: MigrationSummary? = null,
    val canRetryImport: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val categories: CategoryRepository,
    private val items: ItemRepository,
    private val imageStore: ImageStore,
    private val importer: LegacyImporter,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(settings.observeSettings(), categories.observeCategories()) { appSettings, categoryList ->
            SettingsUiState(appSettings, categoryList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    private val _messages = MutableStateFlow(SettingsMessages())
    val messages: StateFlow<SettingsMessages> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            // Offered only when there is genuinely something left to import, so the
            // action never appears as a dead end.
            val retryable = !importer.alreadyImported() && importer.hasLegacyData()
            _messages.update { it.copy(canRetryImport = retryable) }
        }
    }

    fun setDateFormat(format: DateFormat) = viewModelScope.launch { settings.setDateFormat(format) }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setNotificationsEnabled(enabled)
    }

    fun setReminderLeadDays(days: Int) = viewModelScope.launch { settings.setReminderLeadDays(days) }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val created = categories.add(name)
            _messages.update { it.copy(categoryExists = created == null) }
        }
    }

    private val _pendingDelete = MutableStateFlow<PendingCategoryDelete?>(null)
    val pendingDelete: StateFlow<PendingCategoryDelete?> = _pendingDelete.asStateFlow()

    /** Looks up the item count first, so the confirmation can state the real cost. */
    fun askToDeleteCategory(category: Category) {
        viewModelScope.launch {
            _pendingDelete.value = PendingCategoryDelete(category, items.countInCategory(category.id))
        }
    }

    fun cancelCategoryDelete() {
        _pendingDelete.value = null
    }

    fun confirmCategoryDelete() {
        val pending = _pendingDelete.value ?: return
        _pendingDelete.value = null
        deleteCategory(pending.category.id)
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categories.delete(id)
            // Items cascade in SQL; their photos are files, so they need sweeping up
            // separately or they linger on disk forever.
            imageStore.pruneOrphans(items.allImagePaths())
        }
    }

    fun deleteAllItems() {
        viewModelScope.launch {
            items.deleteAll()
            imageStore.pruneOrphans(emptyList())
        }
    }

    fun deleteAllUserCategories() {
        viewModelScope.launch {
            categories.deleteAllUserCategories()
            imageStore.pruneOrphans(items.allImagePaths())
        }
    }

    /** Re-runs the legacy import after a failure. The old database is still intact. */
    fun retryImport() {
        viewModelScope.launch {
            _messages.update { it.copy(importRunning = true, importFailed = false) }
            when (val result = importer.importIfNeeded()) {
                is LegacyImporter.Result.Imported ->
                    _messages.update {
                        it.copy(
                            importRunning = false,
                            importSummary = result.summary,
                            canRetryImport = false,
                        )
                    }

                is LegacyImporter.Result.Failed ->
                    _messages.update { it.copy(importRunning = false, importFailed = true) }

                else -> _messages.update { it.copy(importRunning = false, canRetryImport = false) }
            }
        }
    }

    fun clearMessages() = _messages.update {
        it.copy(categoryExists = false, importFailed = false, importSummary = null)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
