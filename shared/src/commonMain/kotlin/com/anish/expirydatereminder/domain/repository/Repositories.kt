package com.anish.expirydatereminder.domain.repository

import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.model.MigrationSummary
import kotlinx.coroutines.flow.Flow

interface ItemRepository {
    /** @param categoryId null means "no filter", replacing the legacy "All Items" sentinel. */
    fun observeItems(categoryId: Long?): Flow<List<Item>>

    suspend fun byId(id: Long): Item?

    suspend fun withName(name: String): List<Item>

    suspend fun add(draft: ItemDraft): Long

    suspend fun update(draft: ItemDraft)

    suspend fun setImagePath(id: Long, path: String?)

    suspend fun delete(id: Long)

    suspend fun deleteAll()

    suspend fun countExpiringBetween(fromSortKey: Int, toSortKey: Int): Int

    suspend fun expiringBetween(fromSortKey: Int, toSortKey: Int): List<Item>

    /** Drives the "this will remove N items" confirmation before deleting a category. */
    suspend fun countInCategory(categoryId: Long): Int

    suspend fun allImagePaths(): List<String>
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>

    suspend fun all(): List<Category>

    suspend fun byId(id: Long): Category?

    suspend fun byBuiltinKey(key: String): Category?

    /** @return null when a user category of that name already exists. */
    suspend fun add(name: String): Long?

    suspend fun rename(id: Long, name: String): Boolean

    /** Cascades to the category's items. @return false for built-ins, which cannot be deleted. */
    suspend fun delete(id: Long): Boolean

    suspend fun deleteAllUserCategories()
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun setDateFormat(format: DateFormat)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setReminderLeadDays(days: Int)

    suspend fun setReminderHour(hour: Int)

    suspend fun migrationSummary(): MigrationSummary?
}
