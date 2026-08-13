package com.anish.expirydatereminder.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.model.BuiltinCategory
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CategoryRepositoryImpl(private val db: EdrDatabase, private val io: CoroutineDispatcher) : CategoryRepository {
    private val queries get() = db.categoryQueries

    override fun observeCategories(): Flow<List<Category>> = queries
        .selectAll()
        .asFlow()
        .mapToList(io)
        .map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<Category> = withContext(io) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun byId(id: Long): Category? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun byBuiltinKey(key: String): Category? = withContext(io) {
        queries.selectByBuiltinKey(key).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun add(name: String): Long? = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        queries.transactionWithResult {
            if (queries.countByName(trimmed).executeAsOne() > 0) {
                null
            } else {
                queries.insert(name = trimmed, builtinKey = null, isBuiltin = false)
                queries.lastInsertRowId().executeAsOne()
            }
        }
    }

    override suspend fun rename(id: Long, name: String): Boolean = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        queries.transactionWithResult {
            if (queries.countByName(trimmed).executeAsOne() > 0) {
                false
            } else {
                queries.rename(name = trimmed, id = id)
                true
            }
        }
    }

    override suspend fun delete(id: Long): Boolean = withContext(io) {
        val category = queries.selectById(id).executeAsOneOrNull() ?: return@withContext false
        if (category.isBuiltin) return@withContext false
        // Items cascade via the foreign key, which requires PRAGMA foreign_keys = ON.
        queries.deleteById(id)
        true
    }

    override suspend fun deleteAllUserCategories(): Unit = withContext(io) {
        queries.deleteAllUserCategories()
    }
}

/**
 * Inserts the built-in categories on a fresh install. Idempotent: `builtinKey` is UNIQUE
 * and the importer reuses this same set, so running it twice is harmless.
 */
internal fun EdrDatabase.seedBuiltinCategories() {
    val existing =
        categoryQueries
            .selectAll()
            .executeAsList()
            .mapNotNull { it.builtinKey }
            .toSet()
    BuiltinCategory.entries
        .filter { it.key !in existing }
        .forEach { categoryQueries.insert(name = it.legacyName, builtinKey = it.key, isBuiltin = true) }
}
