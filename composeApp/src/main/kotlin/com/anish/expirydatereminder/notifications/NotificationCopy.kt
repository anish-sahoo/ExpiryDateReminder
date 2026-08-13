package com.anish.expirydatereminder.notifications

import android.content.Context
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.Item
import kotlin.random.Random
import kotlinx.datetime.LocalDate

/**
 * Picks the wording for the reminder notification.
 *
 * Two things make this feel less robotic than a single fixed sentence. First the tone
 * follows what is actually happening: something already expired reads differently from
 * something due next week. Second there are several phrasings per situation, chosen at
 * random.
 *
 * The random choice is seeded by the day, so a worker that runs twice in one day does not
 * show two different messages, and the wording changes day to day rather than per call.
 */
object NotificationCopy {

    /** Which situation the user is actually in. Drives tone before variety does. */
    enum class Tone { ALREADY_EXPIRED, DUE_TODAY, DUE_SOON }

    private val expiredTitles = intArrayOf(
        R.plurals.notif_expired_title_1,
        R.plurals.notif_expired_title_2,
        R.plurals.notif_expired_title_3,
    )
    private val expiredBodies = intArrayOf(
        R.string.notif_expired_body_1,
        R.string.notif_expired_body_2,
        R.string.notif_expired_body_3,
    )

    private val todayTitles = intArrayOf(
        R.plurals.notif_today_title_1,
        R.plurals.notif_today_title_2,
        R.plurals.notif_today_title_3,
    )
    private val todayBodies = intArrayOf(
        R.string.notif_today_body_1,
        R.string.notif_today_body_2,
        R.string.notif_today_body_3,
    )

    private val soonTitles = intArrayOf(
        R.plurals.notif_soon_title_1,
        R.plurals.notif_soon_title_2,
        R.plurals.notif_soon_title_3,
        R.plurals.notif_soon_title_4,
    )
    private val soonBodies = intArrayOf(
        R.string.notif_soon_body_1,
        R.string.notif_soon_body_2,
        R.string.notif_soon_body_3,
        R.string.notif_soon_body_4,
    )

    data class Copy(val title: String, val body: String)

    fun toneFor(items: List<Item>, today: LocalDate): Tone = when {
        items.any { it.daysUntilExpiry(today) < 0 } -> Tone.ALREADY_EXPIRED
        items.any { it.daysUntilExpiry(today) == 0 } -> Tone.DUE_TODAY
        else -> Tone.DUE_SOON
    }

    fun build(context: Context, items: List<Item>, today: LocalDate, leadDays: Int): Copy {
        val tone = toneFor(items, today)
        val random = Random(today.toEpochDays())

        val (titles, bodies) = when (tone) {
            Tone.ALREADY_EXPIRED -> expiredTitles to expiredBodies
            Tone.DUE_TODAY -> todayTitles to todayBodies
            Tone.DUE_SOON -> soonTitles to soonBodies
        }

        val count = when (tone) {
            Tone.ALREADY_EXPIRED -> items.count { it.daysUntilExpiry(today) < 0 }
            Tone.DUE_TODAY -> items.count { it.daysUntilExpiry(today) == 0 }
            Tone.DUE_SOON -> items.size
        }.coerceAtLeast(1)

        val title = context.resources.getQuantityString(titles[random.nextInt(titles.size)], count, count)

        // Naming an actual item reads as attentive rather than generic. Fall back to the
        // impersonal phrasing when several things are due, so it does not look arbitrary.
        val body = if (count == 1) {
            val name = items.firstOrNull()?.name
            if (name != null) {
                context.getString(R.string.notif_body_named, name)
            } else {
                context.getString(bodies[random.nextInt(bodies.size)], leadDays)
            }
        } else {
            context.getString(bodies[random.nextInt(bodies.size)], leadDays)
        }

        return Copy(title, body)
    }
}
