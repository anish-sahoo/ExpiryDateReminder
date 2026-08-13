package com.anish.expirydatereminder.di

import com.anish.expirydatereminder.camera.GeminiNanoExtractor
import com.anish.expirydatereminder.camera.MlKitOcrEngine
import com.anish.expirydatereminder.camera.OcrEngine
import com.anish.expirydatereminder.camera.ScanAvailability
import com.anish.expirydatereminder.camera.ScanCoordinator
import com.anish.expirydatereminder.notifications.ExpiryReminderWorker
import com.anish.expirydatereminder.ui.items.ItemDetailViewModel
import com.anish.expirydatereminder.ui.items.ItemEditViewModel
import com.anish.expirydatereminder.ui.items.ItemListViewModel
import com.anish.expirydatereminder.ui.settings.SettingsViewModel
import com.anish.expirydatereminder.widget.WidgetRefresher
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    single { ScanAvailability(get()) }
    single { WidgetRefresher(androidContext(), get(), get()) }
    single { MlKitOcrEngine() } bind OcrEngine::class
    single { GeminiNanoExtractor() }
    single { ScanCoordinator(ocr = get(), extractor = get(), settings = get(), io = get(IoDispatcher)) }

    workerOf(::ExpiryReminderWorker)

    viewModelOf(::ItemListViewModel)
    viewModelOf(::ItemEditViewModel)
    viewModelOf(::ItemDetailViewModel)
    viewModelOf(::SettingsViewModel)
}
