package com.anish.expirydatereminder.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import com.anish.expirydatereminder.db.App_settings
import com.anish.expirydatereminder.db.EdrDatabase
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.MigrationSummary
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(private val db: EdrDatabase, private val io: CoroutineDispatcher) : SettingsRepository {
    private val queries get() = db.settingsQueries

    /**
     * Stands in for a settings row that is missing, which should never happen: the row is
     * created with the database.
     *
     * Every field is derived from [AppSettings] rather than restated, because it was restated
     * once and drifted — this said MONTH_FIRST while AppSettings said DAY_FIRST, so the format
     * the app fell back to depended on which of the two you happened to hit.
     */
    private val defaultRow =
        AppSettings().let { defaults ->
            App_settings(
                id = 0,
                dateFormat = defaults.dateFormat.legacyValue.toLong(),
                notificationsEnabled = defaults.notificationsEnabled,
                reminderLeadDays = defaults.reminderLeadDays.toLong(),
                reminderHour = defaults.reminderHour.toLong(),
            )
        }

    override fun observeSettings(): Flow<AppSettings> = queries
        .selectSettings()
        .asFlow()
        .mapToOneOrDefault(defaultRow, io)
        .map { it.toDomain() }

    override suspend fun current(): AppSettings = withContext(io) {
        (queries.selectSettings().executeAsOneOrNull() ?: defaultRow).toDomain()
    }

    override suspend fun setDateFormat(format: DateFormat): Unit = withContext(io) {
        queries.updateDateFormat(format.legacyValue.toLong())
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean): Unit = withContext(io) {
        queries.updateNotificationsEnabled(enabled)
    }

    override suspend fun setReminderLeadDays(days: Int): Unit = withContext(io) {
        queries.updateReminderLeadDays(days.toLong())
    }

    override suspend fun setReminderHour(hour: Int): Unit = withContext(io) {
        queries.updateReminderHour(hour.toLong())
    }

    override suspend fun migrationSummary(): MigrationSummary? = withContext(io) {
        queries.selectMigrationMeta().executeAsOneOrNull()?.let {
            MigrationSummary(
                migratedAt = it.migratedAt,
                itemCount = it.itemCount.toInt(),
                categoryCount = it.categoryCount.toInt(),
                orphanCount = it.orphanCount.toInt(),
                imageCount = it.imageCount.toInt(),
            )
        }
    }
}

private fun App_settings.toDomain() = AppSettings(
    dateFormat = DateFormat.fromLegacy(dateFormat.toInt()),
    notificationsEnabled = notificationsEnabled,
    reminderLeadDays = reminderLeadDays.toInt(),
    reminderHour = reminderHour.toInt(),
)
