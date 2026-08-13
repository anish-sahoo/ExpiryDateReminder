package com.anish.expirydatereminder.ui.items

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.camera.GeminiNanoExtractor
import com.anish.expirydatereminder.camera.OcrEngine
import com.anish.expirydatereminder.camera.ScanCoordinator
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.usecase.DraftError
import com.anish.expirydatereminder.domain.usecase.DraftField
import com.anish.expirydatereminder.domain.usecase.DuplicateCheck
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.testing.FakeCategoryRepository
import com.anish.expirydatereminder.testing.FakeItemRepository
import com.anish.expirydatereminder.testing.FakeSettingsRepository
import com.anish.expirydatereminder.testing.MainDispatcherRule
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The add/edit form: validation, duplicate handling, and the one-shot save signal.
 *
 * The save signal has its own group of tests because it caused a real bug — the sheet
 * closing the instant it reopened — and the mechanism is easy to regress.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ItemEditViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val imageStore = ImageStore(ApplicationProvider.getApplicationContext())
    private val uncategorized = FakeCategoryRepository.UNCATEGORIZED_ID

    private fun viewModel(items: FakeItemRepository = FakeItemRepository()) = ItemEditViewModel(
        items = items,
        categories = FakeCategoryRepository(),
        settings = FakeSettingsRepository(),
        scanCoordinator = ScanCoordinator(
            // Scanning has its own tests; here it only has to exist.
            ocr = object : OcrEngine {
                override suspend fun recognize(bitmap: Bitmap) = ""
            },
            extractor = GeminiNanoExtractor(),
            settings = FakeSettingsRepository(),
            io = Dispatchers.Unconfined,
        ),
        imageStore = imageStore,
    )

    private fun ItemEditViewModel.fillValid(name: String = "Milk") {
        setName(name)
        setDay("24")
        setMonth("11")
        setYear("2027")
    }

    @Test
    fun `a new form defaults to Uncategorized`() = runTest {
        // Anything else means the category is inherited from list ordering rather than
        // chosen, which is how items end up mis-filed.
        val vm = viewModel()
        vm.load(null)
        assertEquals(uncategorized, vm.state.value.categoryId)
    }

    @Test
    fun `loading an existing item fills every field`() = runTest {
        val existing = testItem(
            id = 7,
            name = "Ibuprofen",
            expiry = ExpiryDate(3, 4, 2027),
            notes = "bathroom",
            imagePath = "images/item_7.jpg",
        )
        val vm = viewModel(FakeItemRepository(listOf(existing)))
        vm.load(7)

        val state = vm.state.value
        assertEquals("Ibuprofen", state.name)
        assertEquals("3", state.day)
        assertEquals("4", state.month)
        assertEquals("2027", state.year)
        assertEquals("bathroom", state.notes)
        assertEquals("images/item_7.jpg", state.imagePath)
        assertTrue(state.isEditing)
    }

    @Test
    fun `saving an invalid draft reports the fields rather than writing`() = runTest {
        val repo = FakeItemRepository()
        val vm = viewModel(repo)
        vm.load(null)

        vm.setName("")
        vm.setMonth("13")
        vm.save()

        assertEquals(DraftError.NAME_BLANK, vm.state.value.errors[DraftField.NAME])
        assertEquals(DraftError.MONTH_OUT_OF_RANGE, vm.state.value.errors[DraftField.MONTH])
        assertTrue(repo.items.isEmpty())
        assertTrue(!vm.state.value.saved)
    }

    @Test
    fun `a valid draft is written and the name is trimmed`() = runTest {
        val repo = FakeItemRepository()
        val vm = viewModel(repo)
        vm.load(null)

        vm.fillValid(name = "  Milk  ")
        vm.save()

        val saved = repo.items.single()
        assertEquals("Milk", saved.name)
        assertEquals(ExpiryDate(24, 11, 2027), saved.expiry)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `editing updates in place rather than inserting a second row`() = runTest {
        val repo = FakeItemRepository(listOf(testItem(id = 7, name = "Milk")))
        val vm = viewModel(repo)
        vm.load(7)

        vm.setName("Oat Milk")
        vm.save()

        assertEquals(1, repo.items.size)
        assertEquals("Oat Milk", repo.items.single().name)
    }

    @Test
    fun `a duplicate stops the save and surfaces the conflict`() = runTest {
        val existing = testItem(id = 1, name = "Milk", expiry = ExpiryDate(24, 11, 2027), categoryId = uncategorized)
        val repo = FakeItemRepository(listOf(existing))
        val vm = viewModel(repo)
        vm.load(null)

        vm.fillValid()
        vm.save()

        assertIs<DuplicateCheck.Exact>(vm.state.value.duplicate)
        assertEquals(1, repo.items.size)
        assertTrue(!vm.state.value.saved)
    }

    @Test
    fun `forcing past a duplicate writes the second row`() = runTest {
        val existing = testItem(id = 1, name = "Milk", expiry = ExpiryDate(24, 11, 2027), categoryId = uncategorized)
        val repo = FakeItemRepository(listOf(existing))
        val vm = viewModel(repo)
        vm.load(null)

        vm.fillValid()
        vm.save()
        vm.save(force = true)

        assertEquals(2, repo.items.size)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `dismissing the duplicate leaves the form as it was`() = runTest {
        val existing = testItem(id = 1, name = "Milk", expiry = ExpiryDate(24, 11, 2027), categoryId = uncategorized)
        val vm = viewModel(FakeItemRepository(listOf(existing)))
        vm.load(null)

        vm.fillValid()
        vm.save()
        vm.dismissDuplicate()

        assertEquals(DuplicateCheck.None, vm.state.value.duplicate)
        assertEquals("Milk", vm.state.value.name)
    }

    @Test
    fun `consuming the save signal clears it`() = runTest {
        val vm = viewModel()
        vm.load(null)
        vm.fillValid()
        vm.save()
        assertTrue(vm.state.value.saved)

        vm.consumeSaved()
        assertTrue(!vm.state.value.saved)
    }

    @Test
    fun `reloading clears a save that was never consumed`() = runTest {
        // Belt and braces for the sheet-closes-on-reopen bug: even if the screen forgets to
        // consume the signal, starting a new form must not inherit it.
        val vm = viewModel()
        vm.load(null)
        vm.fillValid()
        vm.save()

        vm.load(null)

        assertTrue(!vm.state.value.saved)
    }

    @Test
    fun `reloading blanks the previous item's fields`() = runTest {
        val vm = viewModel()
        vm.load(null)
        vm.fillValid(name = "Milk")
        vm.save()

        vm.load(null)

        val state = vm.state.value
        assertEquals("", state.name)
        assertEquals("", state.day)
        assertEquals("", state.month)
        assertEquals("", state.year)
        assertNull(state.itemId)
    }

    @Test
    fun `setting a date clears the field errors it fixes`() = runTest {
        val vm = viewModel()
        vm.load(null)
        vm.save()
        assertTrue(vm.state.value.errors.isNotEmpty())

        vm.setExpiry(ExpiryDate(1, 6, 2027))

        val errors = vm.state.value.errors
        assertNull(errors[DraftField.DAY])
        assertNull(errors[DraftField.MONTH])
        assertNull(errors[DraftField.YEAR])
    }

    @Test
    fun `date fields drop non-digits and respect their length`() = runTest {
        val vm = viewModel()
        vm.load(null)

        vm.setDay("1a2b3")
        vm.setYear("20x2789")

        assertEquals("12", vm.state.value.day)
        assertEquals("2027", vm.state.value.year)
    }

    @Test
    fun `removing a photo clears the path and deletes the file`() = runTest {
        val path = imageStore.write(itemId = 7, bytes = byteArrayOf(1, 2, 3))!!
        val existing = testItem(id = 7, name = "Yoghurt", imagePath = path)
        val vm = viewModel(FakeItemRepository(listOf(existing)))
        vm.load(7)

        vm.removePhoto()

        assertNull(vm.state.value.imagePath)
        assertTrue(!imageStore.exists(path), "an orphaned file would linger in filesDir forever")
    }
}
