package com.anish.expirydatereminder.di

import com.anish.expirydatereminder.data.CategoryRepositoryImpl
import com.anish.expirydatereminder.data.DriverFactory
import com.anish.expirydatereminder.data.ItemRepositoryImpl
import com.anish.expirydatereminder.data.SettingsRepositoryImpl
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.migration.LegacyImageMigrator
import com.anish.expirydatereminder.migration.LegacyImporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Injected rather than referenced directly so tests can substitute a test dispatcher. */
val IoDispatcher = named("io")

val sharedModule =
    module {
        single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }

        single {
            EdrDatabase(DriverFactory.create(get())).also { DriverFactory.initialize(get(), it) }
        }

        single { ImageStore(get()) }

        single { LegacyImporter(context = get(), database = get(), io = get(IoDispatcher)) }
        single { LegacyImageMigrator(context = get(), database = get(), imageStore = get(), io = get(IoDispatcher)) }

        single<ItemRepository> {
            ItemRepositoryImpl(db = get(), io = get(IoDispatcher), now = { System.currentTimeMillis() })
        }
        single<CategoryRepository> { CategoryRepositoryImpl(db = get(), io = get(IoDispatcher)) }
        single<SettingsRepository> { SettingsRepositoryImpl(db = get(), io = get(IoDispatcher)) }
    }
