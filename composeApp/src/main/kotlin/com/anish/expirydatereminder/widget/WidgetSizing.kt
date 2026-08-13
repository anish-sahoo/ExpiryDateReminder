package com.anish.expirydatereminder.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which layout the widget shows at a given size, and how many rows fit in it.
 *
 * Separate from the composable, and free of Glance, so the breakpoints can be checked
 * directly. Getting this wrong is not a crash — it is a widget that quietly wastes half its
 * cell or clips its last row, which is exactly the kind of thing that survives a manual look
 * and only shows up when someone resizes it on a device you do not have.
 */
enum class WidgetLayout {
    /** 1x1: a solid badge with the count. */
    BADGE,

    /** 2x1: the count beside the single most urgent item. */
    SUMMARY,

    /** One line per item. */
    LIST,

    /** As [LIST], plus each item's date on a second line. */
    DETAILED_LIST,
}

object WidgetSizing {

    /** Below either of these only the badge fits. One launcher cell is roughly 70dp. */
    val SUMMARY_MIN_WIDTH = 110.dp
    val SUMMARY_MIN_HEIGHT = 70.dp

    /** Narrower than this and a row is all truncation; wide enough for a name plus "-5d". */
    val NARROW_LIST_MIN_WIDTH = 100.dp

    /** Wide enough for a second line of detail under the name. */
    val LIST_MIN_WIDTH = 180.dp

    /** A list needs a header and two readable rows to earn its space. */
    val LIST_MIN_HEIGHT = 140.dp

    /** Two-line rows only once several of them will still fit. */
    val DETAIL_MIN_HEIGHT = 200.dp

    val ROW_HEIGHT = 40.dp
    val DETAIL_ROW_HEIGHT = 48.dp
    val ROW_GAP = 6.dp
    val HEADER_GAP = 10.dp

    /** Card padding, the header line and the gap under it. */
    val LIST_CHROME = 14.dp * 2 + 16.dp + HEADER_GAP

    /** Room for the "+n more" line, reserved only when there is an overflow to report. */
    val OVERFLOW_LINE = 20.dp

    /**
     * Width and height are judged separately on purpose. A tall, one-cell-wide widget has room
     * for plenty of rows even though no row can hold a date, so it gets the list with the
     * detail turned down rather than one centered summary and a column of dead space.
     *
     * Glance runs measurement passes that report nonsense sizes, negative values included, so
     * anything unexpected has to fall through to the smallest layout rather than throw.
     */
    fun layoutFor(width: Dp, height: Dp): WidgetLayout = when {
        width >= NARROW_LIST_MIN_WIDTH && height >= LIST_MIN_HEIGHT ->
            if (width >= LIST_MIN_WIDTH && height >= DETAIL_MIN_HEIGHT) {
                WidgetLayout.DETAILED_LIST
            } else {
                WidgetLayout.LIST
            }
        width < SUMMARY_MIN_WIDTH || height < SUMMARY_MIN_HEIGHT -> WidgetLayout.BADGE
        else -> WidgetLayout.SUMMARY
    }

    fun rowHeightFor(layout: WidgetLayout): Dp =
        if (layout == WidgetLayout.DETAILED_LIST) DETAIL_ROW_HEIGHT else ROW_HEIGHT

    /**
     * How many rows fit in [height]. Gaps sit between rows, so `n` rows cost
     * `n*rowHeight + (n-1)*gap`.
     *
     * Solved in two passes rather than one: reserving the "+n more" line unconditionally
     * wastes a row whenever the list happens to fit exactly. Callers only reach this above
     * [LIST_MIN_HEIGHT], which guarantees room for at least one row.
     */
    fun fittingRows(height: Dp, rowHeight: Dp, itemCount: Int): Int {
        fun rowsIn(available: Dp) = ((available + ROW_GAP) / (rowHeight + ROW_GAP)).toInt().coerceAtLeast(1)

        val available = height - LIST_CHROME
        val optimistic = rowsIn(available)
        return if (optimistic >= itemCount) itemCount else rowsIn(available - OVERFLOW_LINE)
    }
}
