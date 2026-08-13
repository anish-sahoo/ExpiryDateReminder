package com.anish.expirydatereminder.ui.items

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.usecase.ItemSort
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.testing.FakeCategoryRepository
import com.anish.expirydatereminder.testing.FakeItemRepository
import com.anish.expirydatereminder.testing.FakeSettingsRepository
import com.anish.expirydatereminder.testing.MainDispatcherRule
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The list screen's whole behavior: sorting, searching, filtering and delete-with-undo.
 *
 * Robolectric only for [ImageStore], which needs a real `filesDir` — the repositories are
 * fakes, so nothing here touches a database.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application, not EdrApplication: the real one starts Koin and kicks off the legacy
// migration in onCreate, which throws on the second test in the same JVM and has nothing to
// do with what is under test here.
@Config(application = Application::class)
class ItemListViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val imageStore = ImageStore(ApplicationProvider.getApplicationContext())

    private val grocery = FakeCategoryRepository.BUILTINS.first { it.builtinKey == "grocery" }
    private val medicine = FakeCategoryRepository.BUILTINS.first { it.builtinKey == "medicine" }

    private val seeded = listOf(
        testItem(id = 1, name = "Milk", expiry = ExpiryDate(1, 3, 2027), categoryId = grocery.id),
        testItem(id = 2, name = "Aspirin", expiry = ExpiryDate(1, 1, 2027), categoryId = medicine.id),
        testItem(id = 3, name = "Crème fraîche", expiry = ExpiryDate(1, 2, 2027), categoryId = grocery.id),
    )

    private fun viewModel(items: FakeItemRepository = FakeItemRepository(seeded)) = ItemListViewModel(
        items = items,
        categories = FakeCategoryRepository(),
        settings = FakeSettingsRepository(),
        imageStore = imageStore,
    )

    /** The flow opens on a placeholder before the repositories have emitted anything. */
    private suspend fun ReceiveTurbine<ItemListUiState>.awaitLoaded(): ItemListUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }

    @Test
    fun `starts sorted by expiry, soonest first`() = runTest {
        viewModel().state.test {
            assertEquals(listOf(2L, 3L, 1L), awaitLoaded().items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling sort switches to name and back again`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()

            vm.toggleSort()
            val byName = awaitItem()
            assertEquals(ItemSort.BY_NAME, byName.sort)
            assertEquals(listOf("Aspirin", "Crème fraîche", "Milk"), byName.items.map { it.name })

            vm.toggleSort()
            assertEquals(ItemSort.BY_EXPIRY, awaitItem().sort)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search narrows the list and survives diacritics`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()

            vm.search("creme")
            assertEquals(listOf(3L), awaitItem().items.map { it.id })

            vm.search("")
            assertEquals(3, awaitItem().items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting a category filters, and null clears the filter`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()

            vm.selectCategory(grocery.id)
            val filtered = awaitItem()
            assertEquals(setOf(1L, 3L), filtered.items.map { it.id }.toSet())
            assertEquals(grocery.id, filtered.selectedCategoryId)

            vm.selectCategory(null)
            val cleared = awaitItem()
            assertEquals(3, cleared.items.size)
            assertNull(cleared.selectedCategoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing yet and nothing found are different states`() = runTest {
        // The screen shows different copy for each, so conflating them would tell someone
        // with a full pantry that they have not added anything.
        viewModel(FakeItemRepository()).state.test {
            val state = awaitLoaded()
            assertTrue(state.isEmptyOverall)
            assertTrue(!state.hasNoMatches)
            cancelAndIgnoreRemainingEvents()
        }

        val vm = viewModel()
        vm.state.test {
            awaitLoaded()
            vm.search("nothing matches this")
            val state = awaitItem()
            assertTrue(state.hasNoMatches)
            assertTrue(!state.isEmptyOverall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes the item and announces it for undo`() = runTest {
        val repo = FakeItemRepository(seeded)
        val vm = viewModel(repo)
        vm.state.test {
            awaitLoaded()
            vm.delete(seeded[0])
            assertEquals(2, awaitItem().items.size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(ItemListEvent.Deleted(seeded[0]), vm.events.value)
    }

    @Test
    fun `undo puts the item back and clears the event`() = runTest {
        val repo = FakeItemRepository(seeded)
        val vm = viewModel(repo)

        vm.delete(seeded[0])
        vm.undoDelete(seeded[0])

        assertEquals(3, repo.items.size)
        assertTrue(repo.items.any { it.name == "Milk" })
        // Left set, the undo snackbar would come back on the next recomposition.
        assertNull(vm.events.value)
    }

    @Test
    fun `undo restores the photo, so the restore is lossless`() = runTest {
        val withPhoto = testItem(id = 9, name = "Yoghurt", imagePath = "images/item_9.jpg")
        val repo = FakeItemRepository(listOf(withPhoto))
        val vm = viewModel(repo)

        vm.delete(withPhoto)
        vm.undoDelete(withPhoto)

        assertEquals("images/item_9.jpg", repo.items.single().imagePath)
    }

    @Test
    fun `the photo survives until the undo window closes, then goes`() = runTest {
        val path = imageStore.write(itemId = 9, bytes = byteArrayOf(1, 2, 3))
        val withPhoto = testItem(id = 9, name = "Yoghurt", imagePath = path)
        val vm = viewModel(FakeItemRepository(listOf(withPhoto)))

        vm.delete(withPhoto)
        assertTrue(imageStore.exists(path!!), "photo must outlive the delete, or undo loses it")

        vm.confirmDelete(withPhoto)
        assertTrue(!imageStore.exists(path), "photo must be cleaned up once undo is no longer possible")
        assertNull(vm.events.value)
    }

    @Test
    fun `consuming the event clears it`() = runTest {
        val vm = viewModel()
        vm.delete(seeded[0])
        vm.consumeEvent()
        assertNull(vm.events.value)
    }
}
