package com.anish.expirydatereminder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anish.expirydatereminder.MainActivity
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.model.plusDays
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.ui.items.CATEGORY_FILTERS_TAG
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get

/**
 * The journeys a user actually takes after an item exists: opening it, editing it, deleting
 * it with an undo, searching, filtering and reaching settings.
 *
 * Adding is covered separately in `AddItemFlowTest`. This starts from seeded data instead, so
 * a failure in the add form does not knock out every other assertion with it.
 */
// Test names are camelCase rather than the backticked sentences used in the JVM tests: dex
// cannot represent a method name containing a comma, and the failure is a build error rather
// than something the compiler catches.
@RunWith(AndroidJUnit4::class)
class ItemJourneyTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val items: ItemRepository by lazy { get(ItemRepository::class.java) }
    private val categories: CategoryRepository by lazy { get(CategoryRepository::class.java) }

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun string(id: Int) = rule.activity.getString(id)

    @Before
    fun seed() = runBlocking {
        removeTestItems()
        val grocery = categories.byBuiltinKey("grocery")!!.id
        val medicine = categories.byBuiltinKey("medicine")!!.id
        // Both inside the default 14-day window, so they land in groups that are expanded
        // by default and are therefore actually on screen.
        items.add(draft(SOUP, grocery, 4))
        items.add(draft(TABLETS, medicine, 6))
        rule.waitForIdle()
        waitUntilRowShown(SOUP)
    }

    @After
    fun removeTestItems() = runBlocking {
        (items.withName(SOUP) + items.withName(TABLETS) + items.withName(RENAMED)).forEach { items.delete(it.id) }
    }

    private fun draft(name: String, categoryId: Long, inDays: Int) = ItemDraft(
        name = name,
        categoryId = categoryId,
        expiry = ExpiryDate.from(today.plusDays(inDays)),
    )

    @Test
    fun openingAnItemShowsItsDetail() {
        openItem(SOUP)
        rule.onNodeWithText(SOUP).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.edit_title_edit)).assertIsDisplayed()
    }

    @Test
    fun editingAnItemFromItsDetailUpdatesTheList() {
        openItem(SOUP)
        rule.onNodeWithText(string(R.string.edit_title_edit)).performClick()
        waitUntilPresent(string(R.string.action_save))

        // The field already holds the current name, so it has to be cleared first. Matched
        // by "the editable node containing this text", because the detail screen behind the
        // sheet shows the same name as a plain label.
        rule.onNode(hasSetTextAction() and hasText(SOUP)).performTextClearance()
        // Cleared, the field shows its label again, which is a stable handle.
        rule.onNodeWithText(string(R.string.edit_name)).performTextInput(RENAMED)

        // Scrolled to first: the form is taller than the sheet on a short screen, and
        // clicking an off screen node silently does nothing, which looks like a failed save.
        rule.onNodeWithText(string(R.string.action_save)).performScrollTo().performClick()
        waitUntilGone(string(R.string.action_save))

        // The detail screen's back arrow is labeled, not captioned, so it is found the way
        // a screen reader would find it.
        rule.onNodeWithContentDescription(string(R.string.action_done)).performClick()
        waitUntilRowShown(RENAMED)
    }

    @Test
    fun swipingARowDeletesItAndUndoBringsItBack() {
        // Deletion is a swipe with no confirmation, so undo is the only thing standing
        // between a mis-swipe and lost data.
        rule.onNodeWithContentDescription(SOUP, substring = true)
            .performTouchInput { swipeLeft() }

        waitUntilRowGone(SOUP)
        waitUntilPresent(string(R.string.action_undo))

        rule.onNodeWithText(string(R.string.action_undo)).performClick()

        waitUntilRowShown(SOUP)
    }

    @Test
    fun searchNarrowsTheListToWhatWasTyped() {
        rule.onNodeWithContentDescription(string(R.string.items_search_hint)).performClick()
        waitUntilPresent(string(R.string.items_search_hint))

        rule.onNodeWithText(string(R.string.items_search_hint)).performTextInput(TABLETS)

        waitUntilRowShown(TABLETS)
        waitUntilRowGone(SOUP)
    }

    @Test
    fun filteringByCategoryHidesTheOtherCategories() {
        // Scroll the row rather than matching the chip directly. The filters are a LazyRow,
        // so a chip that does not fit on screen is never composed and cannot be found: this
        // passed on a wide emulator and failed on a narrower one.
        val medicine = string(R.string.category_medicine)
        rule.onNodeWithTag(CATEGORY_FILTERS_TAG).performScrollToNode(hasText(medicine))
        rule.onNodeWithText(medicine).performClick()

        waitUntilRowShown(TABLETS)
        waitUntilRowGone(SOUP)
    }

    @Test
    fun sortingTogglesBetweenExpiryAndName() {
        // The control's own label is the assertion: it names what tapping it will do next.
        rule.onNodeWithContentDescription(string(R.string.items_sort_by_name)).performClick()
        waitUntilPresent(string(R.string.items_sort_by_expiry), byContentDescription = true)
    }

    @Test
    fun settingsOpens() {
        rule.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        waitUntilPresent(string(R.string.settings_notifications_enabled))
    }

    @Test
    fun helpOpens() {
        rule.onNodeWithContentDescription(string(R.string.help_title)).performClick()
        waitUntilPresent(string(R.string.help_title))
    }

    // ---- helpers ---------------------------------------------------------------------

    private fun openItem(name: String) {
        rule.onNodeWithContentDescription(name, substring = true).performClick()
        waitUntilPresent(string(R.string.edit_title_edit))
    }

    private fun waitUntilPresent(text: String, byContentDescription: Boolean = false) = rule.waitUntil(TIMEOUT_MS) {
        val nodes = if (byContentDescription) {
            rule.onAllNodesWithContentDescription(text)
        } else {
            rule.onAllNodesWithText(text)
        }
        nodes.fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(text: String) = rule.waitUntil(TIMEOUT_MS) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    /** Rows carry one merged content description, so they are matched the way a screen reader sees them. */
    private fun waitUntilRowShown(name: String) = rule.waitUntil(TIMEOUT_MS) {
        rule.onAllNodesWithContentDescription(name, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilRowGone(name: String) = rule.waitUntil(TIMEOUT_MS) {
        rule.onAllNodesWithContentDescription(name, substring = true).fetchSemanticsNodes().isEmpty()
    }

    private companion object {
        const val SOUP = "EdrJourneySoup"
        const val TABLETS = "EdrJourneyTablets"
        const val RENAMED = "EdrJourneyRenamed"

        // Generous because a cold CI emulator is far slower than a warm local one, and a
        // timeout here reports as a behaviour failure rather than as the machine being busy.
        const val TIMEOUT_MS = 15_000L
    }
}
