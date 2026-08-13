package com.anish.expirydatereminder.widget

import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Which layout appears at which size, and how many rows fit once it does.
 *
 * These sizes are the real ones: a launcher cell is roughly `70*cells - 30` dp, so 1x1 is
 * about 40-70dp and each further cell adds ~70.
 */
class WidgetLayoutSelectionTest {

    @Test
    fun `one cell gets the badge`() {
        assertEquals(WidgetLayout.BADGE, WidgetSizing.layoutFor(70.dp, 70.dp))
    }

    @Test
    fun `two cells wide but short gets the summary`() {
        assertEquals(WidgetLayout.SUMMARY, WidgetSizing.layoutFor(140.dp, 70.dp))
    }

    @Test
    fun `a tall narrow widget gets a list, not a summary`() {
        // The bug this pins: judging width and height together sent a one-cell-wide,
        // four-cell-tall widget to the summary and left a column of dead space.
        assertEquals(WidgetLayout.LIST, WidgetSizing.layoutFor(100.dp, 280.dp))
    }

    @Test
    fun `detail only arrives once it is both wide and tall enough`() {
        // Wide but short, and tall but narrow, both stay on single-line rows.
        assertEquals(WidgetLayout.LIST, WidgetSizing.layoutFor(220.dp, 150.dp))
        assertEquals(WidgetLayout.LIST, WidgetSizing.layoutFor(120.dp, 260.dp))
        assertEquals(WidgetLayout.DETAILED_LIST, WidgetSizing.layoutFor(220.dp, 260.dp))
    }

    @Test
    fun `the breakpoints are inclusive at their lower edge`() {
        assertEquals(
            WidgetLayout.LIST,
            WidgetSizing.layoutFor(WidgetSizing.NARROW_LIST_MIN_WIDTH, WidgetSizing.LIST_MIN_HEIGHT),
        )
        assertEquals(
            WidgetLayout.DETAILED_LIST,
            WidgetSizing.layoutFor(WidgetSizing.LIST_MIN_WIDTH, WidgetSizing.DETAIL_MIN_HEIGHT),
        )
    }

    @Test
    fun `nonsense sizes fall back to the badge instead of throwing`() {
        // Glance really does run measurement passes reporting negative sizes.
        assertEquals(WidgetLayout.BADGE, WidgetSizing.layoutFor((-16).dp, (-16).dp))
        assertEquals(WidgetLayout.BADGE, WidgetSizing.layoutFor(0.dp, 0.dp))
    }
}

class WidgetFittingRowsTest {

    private fun rows(height: Int, itemCount: Int, rowHeight: Int = 40) =
        WidgetSizing.fittingRows(height.dp, rowHeight.dp, itemCount)

    /** What [WidgetSizing.fittingRows] promises the caller will actually draw. */
    private fun consumed(rows: Int, itemCount: Int, rowHeight: Int = 40): Float {
        val gaps = (rows - 1).coerceAtLeast(0) * WidgetSizing.ROW_GAP.value
        val overflow = if (rows < itemCount) WidgetSizing.OVERFLOW_LINE.value else 0f
        return WidgetSizing.LIST_CHROME.value + rows * rowHeight + gaps + overflow
    }

    @Test
    fun `shows every item when they all fit`() {
        assertEquals(3, rows(height = 250, itemCount = 3))
    }

    @Test
    fun `never claims more rows than there are items`() {
        assertEquals(1, rows(height = 400, itemCount = 1))
    }

    @Test
    fun `caps at what fits and leaves room for the overflow line`() {
        val itemCount = 20
        val result = rows(height = 250, itemCount = itemCount)
        assertTrue(result in 1 until itemCount)
        assertTrue(
            consumed(result, itemCount) <= 250f,
            "$result rows plus the +n line overflow a 250dp widget",
        )
    }

    @Test
    fun `content never overflows the widget at any height`() {
        // The original arithmetic added a gap after every row while assuming gaps only sat
        // between them, so certain heights clipped the last row by a few dp.
        for (height in 140..400) {
            for (itemCount in 1..12) {
                val result = rows(height, itemCount)
                assertTrue(
                    consumed(result, itemCount) <= height.toFloat(),
                    "clipped at height=$height items=$itemCount rows=$result",
                )
            }
        }
    }

    @Test
    fun `always shows at least one row`() {
        // An empty list is handled before this is reached, so returning zero would render a
        // header over nothing.
        assertEquals(1, rows(height = 140, itemCount = 5))
    }

    @Test
    fun `taller rows mean fewer of them`() {
        val single = rows(height = 300, itemCount = 20, rowHeight = 40)
        val detailed = rows(height = 300, itemCount = 20, rowHeight = 48)
        assertTrue(detailed < single, "single=$single detailed=$detailed")
    }
}
