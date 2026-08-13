package com.anish.expirydatereminder.ui.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.migration.LegacyImporter
import com.anish.expirydatereminder.testing.FakeCategoryRepository
import com.anish.expirydatereminder.testing.FakeItemRepository
import com.anish.expirydatereminder.testing.FakeSettingsRepository
import com.anish.expirydatereminder.testing.MainDispatcherRule
import com.anish.expirydatereminder.testing.inMemoryDatabase
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageStore = ImageStore(context)

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        items: FakeItemRepository = FakeItemRepository(),
    ) = SettingsViewModel(
        settings = settings,
        categories = categories,
        items = items,
        imageStore = imageStore,
        importer = LegacyImporter(context, inMemoryDatabase(context), Dispatchers.Unconfined),
    )

    @Test
    fun `changing the date format persists it`() = runTest {
        val settings = FakeSettingsRepository()
        viewModel(settings).setDateFormat(DateFormat.ISO)
        assertEquals(DateFormat.ISO, settings.current().dateFormat)
    }

    @Test
    fun `toggling notifications and lead time persists them`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)

        vm.setNotificationsEnabled(false)
        vm.setReminderLeadDays(30)

        assertEquals(false, settings.current().notificationsEnabled)
        assertEquals(30, settings.current().reminderLeadDays)
    }

    @Test
    fun `state combines settings and categories`() = runTest {
        val vm = viewModel()
        vm.state.test {
            var state = awaitItem()
            while (state.categories.isEmpty()) state = awaitItem()
            assertEquals(FakeCategoryRepository.BUILTINS.size, state.categories.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a category succeeds quietly`() = runTest {
        val categories = FakeCategoryRepository()
        val vm = viewModel(categories = categories)

        vm.addCategory("Garage")

        assertTrue(categories.all().any { it.name == "Garage" })
        assertTrue(!vm.messages.value.categoryExists)
    }

    @Test
    fun `adding a name that already exists reports it instead of duplicating`() = runTest {
        val categories = FakeCategoryRepository()
        val vm = viewModel(categories = categories)

        vm.addCategory("Garage")
        vm.addCategory("garage")

        assertEquals(1, categories.all().count { it.name.equals("Garage", ignoreCase = true) })
        assertTrue(vm.messages.value.categoryExists)
    }

    @Test
    fun `deleting a category asks first, and states the real cost`() = runTest {
        // The confirmation says "this will remove N items", so N has to be looked up before
        // the dialog appears rather than guessed.
        val categories = FakeCategoryRepository()
        val id = categories.add("Garage")!!
        val items = FakeItemRepository(
            listOf(testItem(id = 1, categoryId = id), testItem(id = 2, categoryId = id)),
        )
        val vm = viewModel(categories = categories, items = items)

        vm.askToDeleteCategory(Category(id = id, name = "Garage", builtinKey = null, isBuiltin = false))

        assertEquals(2, vm.pendingDelete.value?.itemCount)
        assertEquals("Garage", vm.pendingDelete.value?.category?.name)
    }

    @Test
    fun `cancelling leaves the category alone`() = runTest {
        val categories = FakeCategoryRepository()
        val id = categories.add("Garage")!!
        val vm = viewModel(categories = categories)

        vm.askToDeleteCategory(Category(id, "Garage", null, isBuiltin = false))
        vm.cancelCategoryDelete()

        assertNull(vm.pendingDelete.value)
        assertTrue(categories.all().any { it.id == id })
    }

    @Test
    fun `confirming deletes it and clears the prompt`() = runTest {
        val categories = FakeCategoryRepository()
        val id = categories.add("Garage")!!
        val vm = viewModel(categories = categories)

        vm.askToDeleteCategory(Category(id, "Garage", null, isBuiltin = false))
        vm.confirmCategoryDelete()

        assertNull(vm.pendingDelete.value)
        assertTrue(categories.all().none { it.id == id })
    }

    @Test
    fun `built-in categories cannot be deleted`() = runTest {
        val categories = FakeCategoryRepository()
        val builtin = FakeCategoryRepository.BUILTINS.first()
        val vm = viewModel(categories = categories)

        vm.deleteCategory(builtin.id)

        assertTrue(categories.all().any { it.id == builtin.id })
    }

    @Test
    fun `deleting all items also sweeps up their photos`() = runTest {
        // Items cascade in SQL; photos are files and would otherwise sit in filesDir forever.
        val path = imageStore.write(itemId = 1, bytes = byteArrayOf(1))!!
        val items = FakeItemRepository(listOf(testItem(id = 1, imagePath = path)))
        val vm = viewModel(items = items)

        vm.deleteAllItems()

        assertTrue(items.items.isEmpty())
        assertTrue(!imageStore.exists(path))
    }

    @Test
    fun `deleting all user categories keeps the built-ins`() = runTest {
        val categories = FakeCategoryRepository()
        categories.add("Garage")
        val vm = viewModel(categories = categories)

        vm.deleteAllUserCategories()

        val remaining = categories.all()
        assertEquals(FakeCategoryRepository.BUILTINS.size, remaining.size)
        assertTrue(remaining.all { it.isBuiltin })
    }

    @Test
    fun `retry import is not offered when there is nothing to import`() = runTest {
        // Offering a dead end is worse than not offering the action at all.
        val vm = viewModel()
        assertTrue(!vm.messages.value.canRetryImport)
    }
}
