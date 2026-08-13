package com.anish.expirydatereminder.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.repository.ItemRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ItemRepositoryImpl(
    private val db: EdrDatabase,
    private val io: CoroutineDispatcher,
    private val now: () -> Long,
) : ItemRepository {
    private val queries get() = db.itemQueries

    override fun observeItems(categoryId: Long?): Flow<List<Item>> = if (categoryId == null) {
        queries
            .selectAll()
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
    } else {
        queries
            .selectByCategory(categoryId)
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun byId(id: Long): Item? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun withName(name: String): List<Item> = withContext(io) {
        queries.findDuplicates(name).executeAsList().map { it.toDomain() }
    }

    override suspend fun add(draft: ItemDraft): Long = withContext(io) {
        val timestamp = now()
        queries.transactionWithResult {
            queries.insert(
                name = draft.name.trim(),
                categoryId = draft.categoryId,
                expiryDay = draft.expiry.day.toLong(),
                expiryMonth = draft.expiry.month.toLong(),
                expiryYear = draft.expiry.year.toLong(),
                imagePath = draft.imagePath,
                notes = draft.notes?.takeIf { it.isNotBlank() },
                createdAt = timestamp,
                updatedAt = timestamp,
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    override suspend fun update(draft: ItemDraft): Unit = withContext(io) {
        val id = requireNotNull(draft.id) { "update() requires an existing item id" }
        queries.update(
            name = draft.name.trim(),
            categoryId = draft.categoryId,
            expiryDay = draft.expiry.day.toLong(),
            expiryMonth = draft.expiry.month.toLong(),
            expiryYear = draft.expiry.year.toLong(),
            imagePath = draft.imagePath,
            notes = draft.notes?.takeIf { it.isNotBlank() },
            updatedAt = now(),
            id = id,
        )
    }

    override suspend fun setImagePath(id: Long, path: String?): Unit = withContext(io) {
        queries.updateImagePath(imagePath = path, updatedAt = now(), id = id)
    }

    override suspend fun delete(id: Long): Unit = withContext(io) { queries.deleteById(id) }

    override suspend fun deleteAll(): Unit = withContext(io) { queries.deleteAll() }

    override suspend fun countExpiringBetween(fromSortKey: Int, toSortKey: Int): Int = withContext(io) {
        queries.countExpiringBetween(fromSortKey.toLong(), toSortKey.toLong()).executeAsOne().toInt()
    }

    override suspend fun expiringBetween(fromSortKey: Int, toSortKey: Int): List<Item> = withContext(io) {
        queries
            .selectExpiringBetween(fromSortKey.toLong(), toSortKey.toLong())
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun countInCategory(categoryId: Long): Int = withContext(io) {
        queries.countByCategory(categoryId).executeAsOne().toInt()
    }

    override suspend fun allImagePaths(): List<String> = withContext(io) {
        queries.selectImagePaths().executeAsList().filterNotNull()
    }
}
