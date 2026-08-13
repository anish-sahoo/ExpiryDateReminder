package com.anish.expirydatereminder.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anish.expirydatereminder.MainActivity
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.model.minusDays
import com.anish.expirydatereminder.domain.model.plusDays
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.logging.Log
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Daily check for items approaching expiry.
 *
 * Deliberately a self-rescheduling one-shot rather than a PeriodicWorkRequest: periodic
 * work has a 15-minute floor and no guaranteed firing time, so a reminder anchored to
 * 07:00 would drift through the day. Each run enqueues the next one.
 *
 * Rescheduling on completion also means the schedule survives reboot without the manual
 * BOOT_COMPLETED receiver the pre-2.0 app lacked entirely (its AlarmManager schedule died
 * on reboot and only came back if the user reopened the app).
 */
class ExpiryReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val items: ItemRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = settings.current()
        // Always reschedule, even when reminders are off, so re-enabling needs no extra
        // wiring and a missed run cannot break the chain permanently.
        ExpiryReminderScheduler.scheduleNext(applicationContext, config.reminderHour)

        if (!config.notificationsEnabled) return Result.success()
        if (!hasNotificationPermission()) return Result.success()

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        // The lower bound matters: the pre-2.0 receiver counted every item where
        // `today >= expiry - 14 days` with no floor, so things that expired years ago
        // kept inflating the number forever.
        val due = items.expiringBetween(
            fromSortKey = ExpiryDate.from(today.minusDays(config.reminderLeadDays)).sortKey,
            toSortKey = ExpiryDate.from(today.plusDays(config.reminderLeadDays)).sortKey,
        )
        if (due.isNotEmpty()) notify(due, today, config.reminderLeadDays)
        return Result.success()
    }

    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notify(due: List<Item>, today: LocalDate, leadDays: Int) {
        val copy = NotificationCopy.build(applicationContext, due, today, leadDays)
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // Re-checked here rather than relying on doWork's earlier check. The permission can
        // be revoked between the two, and stating the requirement at the call site is also
        // what lets lint verify it.
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission was revoked before the reminder could post")
            return
        }
        // SecurityException is caught rather than assumed away: the permission check above
        // and the post are two separate moments, and the user can revoke in between.
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to post the reminder notification", e)
        }
    }

    companion object {
        // Reused from the pre-2.0 app on purpose: changing a channel id discards the
        // sound and importance settings existing users have already chosen.
        const val TAG = "ExpiryReminder"
        const val CHANNEL_ID = "edr_channel_1"
        const val NOTIFICATION_ID = 13
    }
}

object ExpiryReminderScheduler {

    private const val WORK_NAME = "expiry_reminder"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ExpiryReminderWorker.CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        manager.createNotificationChannel(channel)
    }

    /** REPLACE rather than KEEP so a changed reminder hour takes effect immediately. */
    fun scheduleNext(context: Context, hourOfDay: Int) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
            .setInitialDelay(millisUntilNext(hourOfDay), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Runs the reminder check immediately, under its own unique name so it cannot disturb
     * the real 07:00 schedule.
     *
     * Exists because a scheduled worker is otherwise almost impossible to exercise by hand:
     * `cmd jobscheduler run` will not resolve WorkManager's namespaced job ids, and setting
     * the device clock forward needs root. Debug builds only.
     */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("expiry_reminder_now", ExistingWorkPolicy.REPLACE, request)
    }

    /** Enqueues only if nothing is pending, so app launches don't reset the schedule. */
    fun ensureScheduled(context: Context, hourOfDay: Int) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
            .setInitialDelay(millisUntilNext(hourOfDay), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    internal fun millisUntilNext(hourOfDay: Int, now: Long = System.currentTimeMillis()): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now
    }
}
