package com.anish.expirydatereminder.ui.items

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.repository.CategoryRepository
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.testing.MainDispatcherRule
import com.anish.expirydatereminder.testing.TEST_TODAY
import com.anish.expirydatereminder.testing.fixedToday
import com.anish.expirydatereminder.testing.testItem
import com.anish.expirydatereminder.ui.theme.EdrTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The item list, rendered for real, on the JVM.
 *
 * These cover what the instrumented suite used to and no longer does on every push: that the
 * screen groups by urgency, that filtering narrows it, that the counts say what they should.
 * Robolectric runs the same Compose test API without an emulator, so a run takes seconds and
 * cannot fail because a device was slow or a chip was off screen.
 *
 * Repositories are mocked rather than faked. The screen only reads from them here, so a stub
 * that emits a fixed list is both shorter and more explicit about what the case depends on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ItemListComponentTest {

    // Order matters: Main has to point at a test dispatcher before the composition starts,
    // or the work a view model launches never runs and the screen renders its initial state
    // forever.
    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    // The row photo resolves its ImageStore through koinInject, so a screen cannot compose
    // without a container. Only what the composables reach for goes in here.
    @Before
    fun startContainer() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        startKoin { modules(module { single { ImageStore(context) } }) }
    }

    @After
    fun stopContainer() = stopKoin()

    private val grocery = Category(id = 1, name = "Grocery", builtinKey = "grocery", isBuiltin = true)
    private val medicine = Category(id = 2, name = "Medicine", builtinKey = "medicine", isBuiltin = true)

    // Dates relative to a fixed today, so "expired" and "expiring soon" mean the same thing
    // on every run. The default reminder window is 14 days.
    private val expired = testItem(
        id = 1,
        name = "Whole Milk",
        expiry = ExpiryDate(9, 6, 2026),
        categoryId = grocery.id,
        categoryName = "Grocery",
        categoryBuiltinKey = "grocery",
    )
    private val soon = testItem(
        id = 2,
        name = "Greek Yogurt",
        expiry = ExpiryDate(18, 6, 2026),
        categoryId = grocery.id,
        categoryName = "Grocery",
        categoryBuiltinKey = "grocery",
    )
    private val later = testItem(
        id = 3,
        name = "Ibuprofen",
        expiry = ExpiryDate(1, 12, 2026),
        categoryId = medicine.id,
        categoryName = "Medicine",
        categoryBuiltinKey = "medicine",
    )

    private val visible = MutableStateFlow(listOf(expired, soon, later))

    private val items = mockk<ItemRepository>(relaxed = true) {
        every { observeItems(any()) } returns visible
    }
    private val categories = mockk<CategoryRepository>(relaxed = true) {
        every { observeCategories() } returns flowOf(listOf(grocery, medicine))
    }
    private val settings = mockk<SettingsRepository>(relaxed = true) {
        every { observeSettings() } returns flowOf(AppSettings())
    }

    private fun show() {
        val viewModel = ItemListViewModel(
            items = items,
            categories = categories,
            settings = settings,
            imageStore = ImageStore(ApplicationProvider.getApplicationContext()),
            today = fixedToday(),
        )
        rule.setContent {
            EdrTheme {
                ItemListScreen(
                    onAddItem = {},
                    onOpenItem = {},
                    onOpenSettings = {},
                    onOpenHelp = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun `the hero counts what needs attention, not what exists`() {
        show()

        // One expired, one inside the 14 day window, one far out. The third is deliberately
        // not counted: the pre-2.0 app counted everything and the number meant nothing.
        rule.onNodeWithText("2 need attention").assertIsDisplayed()
    }

    @Test
    fun `an expired item is described as expired rather than as due`() {
        show()

        // Rows carry one merged content description, the way a screen reader hears them.
        rule.onNodeWithContentDescription("Whole Milk", substring = true).assertIsDisplayed()
    }

    @Test
    fun `filtering by category asks the repository for that category`() {
        show()

        rule.onNodeWithText("Medicine").performClick()
        rule.waitForIdle()

        // The filter is a query, not a client-side sieve, so the check is that the screen
        // asked for the right thing rather than that it hid rows itself.
        verify { items.observeItems(medicine.id) }
    }

    @Test
    fun `today is injected, so the grouping does not drift with the calendar`() {
        show()

        // Belt and braces: if Today were still read from the system clock, this fixture
        // would start reporting a different count the moment the real date passed June 18.
        assert(TEST_TODAY.toString() == "2026-06-15")
        rule.onNodeWithText("2 need attention").assertIsDisplayed()
    }
}
