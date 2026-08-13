package com.anish.expirydatereminder.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity as actionOpenApp
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.anish.expirydatereminder.MainActivity
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.Today
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.ExpiryStatus
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.model.minusDays
import com.anish.expirydatereminder.domain.model.plusDays
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.ui.common.format
import kotlinx.datetime.LocalDate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Home screen widget.
 *
 * Four layouts, not one layout that shrinks. Each answers a different question, and each is
 * written to fill the space it is given rather than leaving a gap under the content:
 *
 *  - [BadgeLayout] (1x1) — "is anything wrong?" A solid urgency-colored badge with a count.
 *  - [SummaryLayout] (2x1) — "how bad, and what?" Count, caption and the single most urgent
 *    item.
 *  - [ListLayout] (3x2 and up) — "what should I deal with?" One line per item.
 *  - [ListLayout] with `detailed` (3x3 and up) — the same list, plus each item's date.
 *
 * Sized with [SizeMode.Exact] rather than a fixed breakpoint set, because the row count is
 * derived from the height the launcher actually gives us. Under a breakpoint set, anything
 * between two entries rendered the smaller layout and left the remainder of the cell empty.
 *
 * Glance renders to RemoteViews, so custom typefaces are out of reach; hierarchy comes
 * from size, weight and the tinted urgency containers instead.
 */
class ExpiryWidget :
    GlanceAppWidget(),
    KoinComponent {

    private val items: ItemRepository by inject()
    private val settings: SettingsRepository by inject()
    private val today: Today by inject()

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = today()
        val config = settings.current()
        val leadDays = config.reminderLeadDays
        val due = items.expiringBetween(
            fromSortKey = ExpiryDate.from(today.minusDays(PAST_WINDOW_DAYS)).sortKey,
            toSortKey = ExpiryDate.from(today.plusDays(leadDays)).sortKey,
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when (val layout = WidgetSizing.layoutFor(size.width, size.height)) {
                    WidgetLayout.BADGE -> BadgeLayout(due, today)
                    WidgetLayout.SUMMARY -> SummaryLayout(due, today, leadDays)
                    else -> ListLayout(
                        due = due,
                        today = today,
                        leadDays = leadDays,
                        dateFormat = config.dateFormat,
                        height = size.height,
                        detailed = layout == WidgetLayout.DETAILED_LIST,
                    )
                }
            }
        }
    }

    /**
     * 1x1: a solid badge.
     *
     * Opaque on purpose. The translucent container tints used inside the app read as a
     * washed-out gray smudge over an arbitrary wallpaper, which left the digit floating with
     * nothing to anchor it. At this size the color *is* the message, so it has to survive
     * whatever is behind it.
     */
    @Composable
    private fun BadgeLayout(due: List<Item>, today: LocalDate) {
        val context = LocalContext.current
        val empty = due.isEmpty()
        val status = statusOf(due, today)
        val badgeFill = if (empty) neutral() else tint(status)

        Column(
            GlanceModifier
                .fillMaxSize()
                .background(fixedColor(badgeFill))
                .cornerRadius(BADGE_RADIUS)
                .padding(4.dp)
                .clickable(actionOpenApp<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (empty) "✓" else due.size.toString(),
                maxLines = 1,
                style = TextStyle(
                    color = fixedColor(Color.White),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            // One short word, so the number is not left to explain itself.
            Text(
                context.getString(if (empty) R.string.widget_badge_clear else R.string.widget_badge_due)
                    .uppercase(),
                maxLines = 1,
                style = TextStyle(
                    color = fixedColor(Color.White.copy(alpha = 0.85f)),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }

    /**
     * 2x1: the count beside the one item that matters most.
     *
     * A list needs a header plus at least two rows to be worth the space; below that a single
     * named item says more than two truncated ones.
     */
    @Composable
    private fun SummaryLayout(due: List<Item>, today: LocalDate, leadDays: Int) {
        val context = LocalContext.current
        val empty = due.isEmpty()
        val status = statusOf(due, today)
        val badgeFill = if (empty) neutral() else tint(status)

        Row(
            widgetSurface().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                GlanceModifier
                    .background(fixedColor(badgeFill))
                    .cornerRadius(ROW_RADIUS)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (empty) "✓" else due.size.toString(),
                    maxLines = 1,
                    style = TextStyle(
                        color = fixedColor(Color.White),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    context.getString(if (empty) R.string.widget_empty_short else R.string.widget_title),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val next = due.firstOrNull()
                if (next != null) {
                    Text(
                        next.name,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        relativeDays(next.daysUntilExpiry(today)),
                        maxLines = 1,
                        style = TextStyle(
                            color = fixedColor(tint(next.statusOn(today, leadDays))),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun ListLayout(
        due: List<Item>,
        today: LocalDate,
        leadDays: Int,
        dateFormat: DateFormat,
        height: Dp,
        detailed: Boolean,
    ) {
        val context = LocalContext.current

        val rowHeight = WidgetSizing.rowHeightFor(
            if (detailed) WidgetLayout.DETAILED_LIST else WidgetLayout.LIST,
        )
        val rows = WidgetSizing.fittingRows(height, rowHeight, itemCount = due.size)

        Column(widgetSurface().padding(14.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widget_title).uppercase(),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (due.isNotEmpty()) {
                    Text(
                        due.size.toString(),
                        style = TextStyle(
                            color = fixedColor(Color.White),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier
                            .background(fixedColor(tint(statusOf(due, today))))
                            .cornerRadius(PILL_RADIUS)
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }

            Spacer(GlanceModifier.height(WidgetSizing.HEADER_GAP))

            if (due.isEmpty()) {
                Text(
                    context.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
                return@Column
            }

            // The gap goes before every row but the first, so the measurement in
            // [fittingRows] matches what is actually drawn and nothing overflows the cell.
            due.take(rows).forEachIndexed { index, item ->
                if (index > 0) Spacer(GlanceModifier.height(WidgetSizing.ROW_GAP))
                ItemRow(item, today, leadDays, dateFormat, detailed)
            }

            val remaining = due.size - rows
            if (remaining > 0) {
                Text(
                    context.getString(R.string.widget_more, remaining),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    modifier = GlanceModifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }

    /** Each row is its own tinted container, echoing the grouped cards in the app. */
    @Composable
    private fun ItemRow(item: Item, today: LocalDate, leadDays: Int, dateFormat: DateFormat, detailed: Boolean) {
        val context = LocalContext.current
        val status = item.statusOn(today, leadDays)

        Row(
            GlanceModifier
                .fillMaxWidth()
                .height(WidgetSizing.rowHeightFor(if (detailed) WidgetLayout.DETAILED_LIST else WidgetLayout.LIST))
                .background(fixedColor(container(status)))
                .cornerRadius(ROW_RADIUS)
                .padding(horizontal = 10.dp)
                .clickable(
                    actionStartActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse("edr://item/${item.id}"))
                            .setClass(context, MainActivity::class.java),
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stands in for the shaped glyph the app uses; Glance has no morphing shapes.
            Spacer(
                GlanceModifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(fixedColor(tint(status)))
                    .cornerRadius(ACCENT_BAR_RADIUS),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    item.name,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (detailed) {
                    Text(
                        item.expiry.format(dateFormat),
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
            Text(
                relativeDays(item.daysUntilExpiry(today)),
                maxLines = 1,
                style = TextStyle(
                    color = fixedColor(tint(status)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    @Composable
    private fun widgetSurface(): GlanceModifier = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(SURFACE_RADIUS)
        .clickable(actionOpenApp<MainActivity>())

    private companion object {
        // Sizing lives in WidgetSizing, which is plain Kotlin and therefore testable.

        // Glance cannot read MaterialTheme.shapes, so the app's scale is restated here.
        val SURFACE_RADIUS = 24.dp
        val BADGE_RADIUS = 22.dp
        val ROW_RADIUS = 14.dp
        val PILL_RADIUS = 9.dp
        val ACCENT_BAR_RADIUS = 2.dp

        /** Recently expired things are the most actionable, so include a short tail. */
        const val PAST_WINDOW_DAYS = 7
    }
}

/**
 * A [ColorProvider] holding one color for both light and dark.
 *
 * The urgency palette is fixed on purpose, so "the same either way" is the honest statement.
 * It also keeps every call on the day/night overload, away from the single-argument one that
 * shares a facade class with a restricted overload.
 */
private fun fixedColor(color: Color): ColorProvider = dayNightColorProvider(day = color, night = color)

/** Expired items dominate the summary: they are the ones that need acting on. */
private fun statusOf(due: List<Item>, today: LocalDate): ExpiryStatus =
    if (due.any { it.daysUntilExpiry(today) < 0 }) ExpiryStatus.EXPIRED else ExpiryStatus.EXPIRING_SOON

/**
 * Compact day offset, matching the badges in the app's list.
 *
 * Not localized, and deliberately so: "-4d" is digits and a single letter that survives a
 * 40dp-wide column, where a translated phrase would be truncated in most languages.
 */
private fun relativeDays(days: Int): String = if (days < 0) "${days}d" else "+${days}d"

// colors go through the day/night overload with the same value on both sides. That is
// literally true here — the urgency palette does not change between light and dark — and it
// avoids the single-argument ColorProvider, which shares a JVM facade class with a
// @RestrictTo(LIBRARY_GROUP) overload and so trips lint's RestrictedApi check.
//
// The urgency accents are hardcoded to the app's fixed palette rather than derived: a widget
// sits on the user's wallpaper, and "expiring soon" has to mean the same thing there as in
// the app. Row *text*, by contrast, comes from GlanceTheme, because the surface underneath it
// follows the system light/dark theme — fixed light-toned text was invisible in light mode.
private fun tint(status: ExpiryStatus): Color = when (status) {
    ExpiryStatus.EXPIRED -> Color(0xFFB3503C)
    ExpiryStatus.EXPIRING_SOON -> Color(0xFFB0842F)
    ExpiryStatus.OK -> Color(0xFF7C8B84)
}

private fun container(status: ExpiryStatus): Color = when (status) {
    ExpiryStatus.EXPIRED -> Color(0x33B3503C)
    ExpiryStatus.EXPIRING_SOON -> Color(0x33B0842F)
    ExpiryStatus.OK -> Color(0x1F7C8B84)
}

private fun neutral(): Color = Color(0xFF7C8B84)

class ExpiryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpiryWidget()
}
