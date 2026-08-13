package com.anish.expirydatereminder.ui.items

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anish.expirydatereminder.domain.Today
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.ExpiryStatus
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.logging.Log
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class ItemDetailUiState(
    val item: Item? = null,
    val settings: AppSettings = AppSettings(),
    val today: LocalDate,
    val deleted: Boolean = false,
) {
    val status: ExpiryStatus
        get() = item?.statusOn(today, settings.reminderLeadDays) ?: ExpiryStatus.OK
}

class ItemDetailViewModel(
    private val items: ItemRepository,
    private val settings: SettingsRepository,
    private val imageStore: ImageStore,
    private val today: Today,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemDetailUiState(today = today()))
    val state: StateFlow<ItemDetailUiState> = _state.asStateFlow()

    fun load(itemId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(item = items.byId(itemId), settings = settings.current()) }
        }
    }

    /**
     * Compresses and stores the capture, then records the path on the row.
     *
     * The photo is written before the row is updated, so a failed write leaves the item
     * pointing at its previous photo rather than at a path with nothing behind it.
     */
    fun attachPhoto(bitmap: Bitmap) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, out)
                out.toByteArray()
            }
            val stored = imageStore.write(item.id, bytes)
            if (stored == null) {
                Log.w(TAG, "Could not store photo for item ${item.id}")
                return@launch
            }
            items.setImagePath(item.id, stored)
            _state.update { it.copy(item = items.byId(item.id)) }
        }
    }

    fun removePhoto() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            imageStore.delete(item.imagePath)
            items.setImagePath(item.id, null)
            _state.update { it.copy(item = items.byId(item.id)) }
        }
    }

    fun delete() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            imageStore.delete(item.imagePath)
            items.delete(item.id)
            _state.update { it.copy(deleted = true) }
        }
    }

    private companion object {
        const val PHOTO_QUALITY = 90
        const val TAG = "ItemDetail"
    }
}
