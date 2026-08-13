package com.anish.expirydatereminder.ui.items

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.ExpiryStatus
import com.anish.expirydatereminder.images.ImageStore
import com.anish.expirydatereminder.testing.FakeItemRepository
import com.anish.expirydatereminder.testing.FakeSettingsRepository
import com.anish.expirydatereminder.testing.MainDispatcherRule
import com.anish.expirydatereminder.testing.TEST_TODAY
import com.anish.expirydatereminder.testing.fixedToday
import com.anish.expirydatereminder.testing.testItem
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ItemDetailViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val imageStore = ImageStore(ApplicationProvider.getApplicationContext())

    private fun viewModel(items: FakeItemRepository, settings: FakeSettingsRepository = FakeSettingsRepository()) =
        ItemDetailViewModel(items = items, settings = settings, imageStore = imageStore, today = fixedToday())

    @Test
    fun `loads the item and the settings it renders with`() = runTest {
        val item = testItem(id = 3, name = "Yoghurt")
        val settings = FakeSettingsRepository(AppSettings(reminderLeadDays = 30))
        val vm = viewModel(FakeItemRepository(listOf(item)), settings)

        vm.load(3)

        assertEquals("Yoghurt", vm.state.value.item?.name)
        assertEquals(30, vm.state.value.settings.reminderLeadDays)
    }

    @Test
    fun `loading a missing item leaves the screen blank rather than crashing`() = runTest {
        // Reachable in practice: a widget deep link can outlive the item it points at.
        val vm = viewModel(FakeItemRepository())
        vm.load(999)
        assertNull(vm.state.value.item)
    }

    @Test
    fun `urgency follows the user's own lead time`() = runTest {
        val today = LocalDate(2027, 3, 1)
        val item = testItem(id = 1, expiry = ExpiryDate(20, 3, 2027))

        val defaultLead = ItemDetailUiState(item = item, today = today, settings = AppSettings(reminderLeadDays = 14))
        assertEquals(ExpiryStatus.OK, defaultLead.status)

        val longLead = ItemDetailUiState(item = item, today = today, settings = AppSettings(reminderLeadDays = 30))
        assertEquals(ExpiryStatus.EXPIRING_SOON, longLead.status)
    }

    @Test
    fun `an absent item reports OK rather than an urgent color`() = runTest {
        assertEquals(ExpiryStatus.OK, ItemDetailUiState(today = TEST_TODAY).status)
    }

    @Test
    fun `removing a photo clears the row and deletes the file`() = runTest {
        val path = imageStore.write(itemId = 3, bytes = byteArrayOf(9))!!
        val repo = FakeItemRepository(listOf(testItem(id = 3, imagePath = path)))
        val vm = viewModel(repo)
        vm.load(3)

        vm.removePhoto()

        assertNull(vm.state.value.item?.imagePath)
        assertNull(repo.items.single().imagePath)
        assertTrue(!imageStore.exists(path))
    }

    @Test
    fun `deleting removes the row, its photo, and signals the screen to close`() = runTest {
        val path = imageStore.write(itemId = 3, bytes = byteArrayOf(9))!!
        val repo = FakeItemRepository(listOf(testItem(id = 3, imagePath = path)))
        val vm = viewModel(repo)
        vm.load(3)

        vm.delete()

        assertTrue(repo.items.isEmpty())
        assertTrue(!imageStore.exists(path))
        assertTrue(vm.state.value.deleted)
    }

    @Test
    fun `deleting before anything has loaded is a no-op`() = runTest {
        val repo = FakeItemRepository(listOf(testItem(id = 3)))
        val vm = viewModel(repo)

        vm.delete()

        assertEquals(1, repo.items.size)
        assertTrue(!vm.state.value.deleted)
    }
}
