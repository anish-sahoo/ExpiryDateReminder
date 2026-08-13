package com.anish.expirydatereminder.ui.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anish.expirydatereminder.MainActivity
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.plusDays
import com.anish.expirydatereminder.domain.repository.ItemRepository
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get

/**
 * The add-item flow, end to end through the real activity, database and DI graph.
 *
 * Written after a bug that only showed up on the *second* use of the sheet. The edit view
 * model is scoped to the activity rather than to a back stack entry, so it outlived the
 * sheet, and a stale `saved = true` dismissed the sheet in the same frame it reopened.
 * Adding one item worked; every attempt after that produced a toast and no sheet. A
 * single-shot test would have passed, so [addingASecondItemGetsAFreshSheet] runs the flow
 * twice on purpose.
 */
@RunWith(AndroidJUnit4::class)
class AddItemFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val items: ItemRepository by lazy { get(ItemRepository::class.java) }

    /**
     * A few days out, so a new item lands in "expiring soon", which is expanded by default.
     * A date further ahead would file it under the collapsed group, and the assertions would
     * then fail for a reason that has nothing to do with saving.
     */
    private val soon = Clock.System.todayIn(TimeZone.currentSystemDefault()).plusDays(5)

    private fun string(id: Int) = rule.activity.getString(id)

    @After
    fun removeTestItems() = runBlocking {
        // This runs against the app's real database, so anything the test creates has to go
        // again, whether or not the test passed.
        (items.withName(FIRST) + items.withName(SECOND)).forEach { items.delete(it.id) }
    }

    @Test
    fun addingAnItemPutsItInTheList() {
        addItem(FIRST)

        assertRowShown(FIRST)
    }

    @Test
    fun addingASecondItemGetsAFreshSheet() {
        addItem(FIRST)

        openSheet()

        // The regression itself: the sheet used to close before anyone could see it. Save
        // and Cancel are the check rather than the sheet title, because the title and the
        // FAB behind it are both "Add item" and would match the same finder.
        rule.onNodeWithText(string(R.string.action_save)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_cancel)).assertIsDisplayed()

        // And it has to come up blank rather than still holding the last item. An empty year
        // field renders as its four-dash placeholder; a year would mean the previous values
        // survived.
        rule.onNodeWithText(YEAR_PLACEHOLDER).assertIsDisplayed()

        addItem(SECOND, alreadyOpen = true)

        assertRowShown(SECOND)
    }

    private fun openSheet() {
        // useUnmergedTree because the extended FAB does not merge its label into one node,
        // so the text lives on a child. Clicking the child still hits the button.
        rule.onNodeWithText(string(R.string.items_add), useUnmergedTree = true).performClick()
        waitUntilPresent(string(R.string.action_save))
    }

    private fun addItem(name: String, alreadyOpen: Boolean = false) {
        if (!alreadyOpen) openSheet()

        // Found by label, not by test tag: the labels are what a user reads, so a test that
        // breaks when they change is reporting something real.
        rule.onNodeWithText(string(R.string.edit_name)).performTextInput(name)
        rule.onNodeWithContentDescription(string(R.string.edit_month)).performTextInput(soon.monthNumber.toString())
        rule.onNodeWithContentDescription(string(R.string.edit_day)).performTextInput(soon.day.toString())
        rule.onNodeWithContentDescription(string(R.string.edit_year)).performTextInput(soon.year.toString())

        rule.onNodeWithText(string(R.string.action_save)).performClick()

        // The save runs on the view model's scope and the list updates from a database flow,
        // neither of which `waitForIdle` knows about — it only waits for composition. Waiting
        // on what the user would actually see is what makes this deterministic instead of a
        // race that happens to pass on a fast machine.
        waitUntilGone(string(R.string.action_save))
        waitUntilRowShown(name)
    }

    /**
     * List rows carry one merged content description rather than separate text nodes — they
     * use `clearAndSetSemantics` so a screen reader announces "name, expires in 5 days,
     * date, category" in one go instead of four fragments. So rows are matched the way a
     * screen reader sees them, not by their visible text.
     */
    private fun assertRowShown(name: String) =
        rule.onNodeWithContentDescription(name, substring = true).assertIsDisplayed()

    private fun waitUntilRowShown(name: String) = rule.waitUntil(TIMEOUT_MS) {
        rule.onAllNodesWithContentDescription(name, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilPresent(text: String) = rule.waitUntil(TIMEOUT_MS) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(text: String) = rule.waitUntil(TIMEOUT_MS) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    private companion object {
        const val FIRST = "EdrTestItemOne"
        const val SECOND = "EdrTestItemTwo"
        const val TIMEOUT_MS = 5_000L

        /** What [BigDateField] draws in place of an empty four-digit value. */
        const val YEAR_PLACEHOLDER = "----"
    }
}
