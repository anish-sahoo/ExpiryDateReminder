package com.anish.expirydatereminder.ui.items

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.camera.ScanAvailability
import com.anish.expirydatereminder.camera.ScanCoordinator
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.ItemDraft
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.testing.MainDispatcherRule
import com.anish.expirydatereminder.testing.fixedToday
import com.anish.expirydatereminder.ui.theme.EdrTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The add sheet, driven through its real UI on the JVM.
 *
 * Replaces what the instrumented `AddItemFlowTest` covered on every push. The bug that suite
 * was written for is the one worth keeping honest: the edit view model outlives the sheet, so
 * a stale `saved = true` dismissed the sheet in the frame it reopened. Adding one item worked
 * and every attempt after that produced a toast and no sheet, which is why
 * [reopeningTheSheetAfterASaveShowsItAgain] runs the flow twice on purpose.
 *
 * The repository is a mock so the assertion can be about what the screen tried to save rather
 * than about what came back out of a database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddItemComponentTest {

    // Order matters: Main has to point at a test dispatcher before the composition starts,
    // or the work a view model launches never runs and the screen renders its initial state
    // forever.
    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val grocery = Category(id = 1, name = "Grocery", builtinKey = "grocery", isBuiltin = true)
    private val uncategorized =
        Category(id = 9, name = "Uncategorized", builtinKey = "uncategorized", isBuiltin = true)

    private val items = mockk<ItemRepository>(relaxed = true) {
        every { observeItems(any()) } returns flowOf(emptyList())
        coEvery { add(any()) } returns 1L
        // The duplicate check reads this before saving; no matches means no confirmation.
        coEvery { withName(any()) } returns emptyList()
    }
    private val categories = mockk<CategoryRepository>(relaxed = true) {
        every { observeCategories() } returns flowOf(listOf(uncategorized, grocery))
        // load() reads this one, not the flow. A relaxed mock would return an empty list,
        // leaving the form with no category, and save() refuses to write one of those.
        coEvery { all() } returns listOf(uncategorized, grocery)
    }
    private val settings = mockk<SettingsRepository>(relaxed = true) {
        every { observeSettings() } returns flowOf(AppSettings())
        coEvery { current() } returns AppSettings()
    }

    private lateinit var viewModel: ItemEditViewModel

    @Before
    fun startContainer() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        startKoin {
            modules(
                module {
                    single { ImageStore(context) }
                    // Stubbed unsupported: the scan button is only rendered where the device
                    // can actually scan, and ML Kit has no business loading in a JVM test.
                    single { mockk<ScanAvailability> { every { scanSupported() } returns false } }
                },
            )
        }
        viewModel = ItemEditViewModel(
            items = items,
            categories = categories,
            settings = settings,
            scanCoordinator = mockk<ScanCoordinator>(relaxed = true),
            imageStore = ImageStore(context),
            today = fixedToday(),
        )
    }

    @After
    fun stopContainer() = stopKoin()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun showSheet() {
        rule.setContent {
            EdrTheme { ItemEditSheet(itemId = null, onDismiss = {}, viewModel = viewModel) }
        }
        // The sheet starts Hidden and animates open. Without letting that settle its content
        // is composed but parked off screen, so clicks land on nothing and every assertion
        // fails for a reason that has nothing to do with the form.
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    // The form is taller than the sheet, so Save sits below the fold. Clicking a node that
    // is off screen silently does nothing, which looks exactly like a save that failed.
    private fun save() {
        rule.onNodeWithText(string(R.string.action_save)).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun fillAndSave(name: String) {
        rule.onNodeWithText(string(R.string.edit_name)).performTextInput(name)
        rule.onNodeWithContentDescription(string(R.string.edit_month)).performTextInput("7")
        rule.onNodeWithContentDescription(string(R.string.edit_day)).performTextInput("20")
        rule.onNodeWithContentDescription(string(R.string.edit_year)).performTextInput("2026")
        save()
        rule.waitForIdle()
    }

    @Test
    fun `saving sends what was typed, field for field`() {
        showSheet()

        fillAndSave("Olive Oil")

        val draft = slot<ItemDraft>()
        coVerify { items.add(capture(draft)) }
        assert(draft.captured.name == "Olive Oil") { "name was ${draft.captured.name}" }
        assert(draft.captured.expiry.day == 20)
        assert(draft.captured.expiry.month == 7)
        assert(draft.captured.expiry.year == 2026)
    }

    @Test
    fun `reopening the sheet after a save shows it again, blank`() {
        showSheet()
        fillAndSave("Olive Oil")

        // What the regression broke. `saved` has to be consumed, or the next open dismisses
        // itself in the same frame and the user sees a toast and nothing else.
        viewModel.consumeSaved()
        viewModel.load(null)
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.action_save)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_cancel)).assertIsDisplayed()
        // A year field holding a value would mean the previous item's values survived.
        rule.onNodeWithText(YEAR_PLACEHOLDER).assertIsDisplayed()
    }

    @Test
    fun `an item with no name is not saved`() {
        showSheet()

        rule.onNodeWithContentDescription(string(R.string.edit_month)).performTextInput("7")
        rule.onNodeWithContentDescription(string(R.string.edit_day)).performTextInput("20")
        rule.onNodeWithContentDescription(string(R.string.edit_year)).performTextInput("2026")
        save()
        rule.waitForIdle()

        coVerify(exactly = 0) { items.add(any()) }
    }

    @Test
    fun `the scan button is hidden where the device cannot scan`() {
        showSheet()

        rule.onAllNodesWithText(string(R.string.edit_scan)).assertCountEquals(0)
    }

    private companion object {
        const val YEAR_PLACEHOLDER = "----"
    }
}
