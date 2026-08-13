package com.anish.expirydatereminder.data

import com.anish.expirydatereminder.db.Category as CategoryRow
import com.anish.expirydatereminder.db.FindDuplicates
import com.anish.expirydatereminder.db.Search
import com.anish.expirydatereminder.db.SelectAll
import com.anish.expirydatereminder.db.SelectByCategory
import com.anish.expirydatereminder.db.SelectById
import com.anish.expirydatereminder.db.SelectExpiringBetween
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.Item

/**
 * SQLDelight generates a distinct result type per joined query, so each needs its own
 * conversion. They are mechanical; the shared shape is asserted by construction.
 */
internal fun SelectAll.toDomain() = Item(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryBuiltinKey = categoryBuiltinKey,
    expiry = ExpiryDate(expiryDay.toInt(), expiryMonth.toInt(), expiryYear.toInt()),
    imagePath = imagePath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun SelectByCategory.toDomain() = Item(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryBuiltinKey = categoryBuiltinKey,
    expiry = ExpiryDate(expiryDay.toInt(), expiryMonth.toInt(), expiryYear.toInt()),
    imagePath = imagePath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun SelectById.toDomain() = Item(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryBuiltinKey = categoryBuiltinKey,
    expiry = ExpiryDate(expiryDay.toInt(), expiryMonth.toInt(), expiryYear.toInt()),
    imagePath = imagePath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun FindDuplicates.toDomain() = Item(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryBuiltinKey = categoryBuiltinKey,
    expiry = ExpiryDate(expiryDay.toInt(), expiryMonth.toInt(), expiryYear.toInt()),
    imagePath = imagePath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun SelectExpiringBetween.toDomain() = Item(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryBuiltinKey = categoryBuiltinKey,
    expiry = ExpiryDate(expiryDay.toInt(), expiryMonth.toInt(), expiryYear.toInt()),
    imagePath = imagePath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CategoryRow.toDomain() = Category(
    id = id,
    name = name,
    builtinKey = builtinKey,
    isBuiltin = isBuiltin,
)

internal fun Search.toDomain() = Item(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryBuiltinKey = categoryBuiltinKey,
    expiry = ExpiryDate(expiryDay.toInt(), expiryMonth.toInt(), expiryYear.toInt()),
    imagePath = imagePath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
