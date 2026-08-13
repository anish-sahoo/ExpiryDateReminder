package com.anish.expirydatereminder

import android.app.Application
import androidx.work.Configuration
import com.anish.expirydatereminder.di.appModule
import com.anish.expirydatereminder.di.sharedModule
import com.anish.expirydatereminder.logging.AndroidLogger
import com.anish.expirydatereminder.logging.Log
import com.anish.expirydatereminder.migration.LegacyImageMigrator
import com.anish.expirydatereminder.migration.LegacyImporter
import com.anish.expirydatereminder.notifications.ExpiryReminderScheduler
import com.anish.expirydatereminder.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class EdrApplication :
    Application(),
    Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()

        // Installed first so anything below can report failures. DEBUG/INFO are dropped
        // in release builds; WARN and ERROR always survive.
        Log.install(AndroidLogger(debugBuild = BuildConfig.DEBUG))
        Log.i(TAG, "Starting ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        startKoin {
            androidContext(this@EdrApplication)
            workManagerFactory()
            modules(sharedModule, appModule)
        }

        ExpiryReminderScheduler.createChannel(this)
        // KEEP, so opening the app doesn't push the next reminder back a day.
        ExpiryReminderScheduler.ensureScheduled(this, hourOfDay = DEFAULT_REMINDER_HOUR)

        migrateLegacyData()

        // Started here rather than from a screen: the widget has to stay current whether or
        // not the app is in the foreground.
        get<WidgetRefresher>().start(applicationScope)
    }

    /**
     * Runs the one-time import of pre-2.0 data.
     *
     * Checked on every launch rather than once, so a device-to-device restore that lands
     * the legacy database *after* first launch is still picked up. The importer itself is
     * idempotent and transactional; on failure it leaves the old database untouched and
     * Settings offers a retry.
     */
    private fun migrateLegacyData() {
        val importer: LegacyImporter = get()
        val imageMigrator: LegacyImageMigrator = get()
        applicationScope.launch {
            when (val result = importer.importIfNeeded()) {
                is LegacyImporter.Result.Imported -> {
                    Log.i(
                        TAG,
                        "Imported ${result.summary.itemCount} items, " +
                            "${result.summary.categoryCount} categories, " +
                            "${result.summary.orphanCount} orphaned",
                    )
                    // Photos are copied outside the database transaction on purpose:
                    // filesystem work cannot be rolled back, and a failed photo copy must
                    // never cost the user their items.
                    runCatching { imageMigrator.migrate() }
                }

                is LegacyImporter.Result.Failed ->
                    Log.e(TAG, "Legacy import failed; old database left intact", result.cause)

                LegacyImporter.Result.AlreadyImported -> Log.d(TAG, "Legacy data already imported")
                LegacyImporter.Result.NothingToImport -> Log.d(TAG, "No legacy database present")
            }
        }
    }

    private companion object {
        const val TAG = "App"
        const val DEFAULT_REMINDER_HOUR = 7
    }
}
