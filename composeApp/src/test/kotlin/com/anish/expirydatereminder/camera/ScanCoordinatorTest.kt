package com.anish.expirydatereminder.camera

import android.app.Application
import android.graphics.Bitmap
import com.anish.expirydatereminder.domain.model.AppSettings
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.testing.FakeSettingsRepository
import com.anish.expirydatereminder.testing.fixedToday
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The camera-to-date pipeline, minus the two pieces that need real hardware.
 *
 * OCR is stubbed with the text it would have produced, and the on-device model is left
 * unavailable — which is also the honest default, since Nano is flagship-only. What is
 * actually under test is the part that runs on every device: what the parser makes of the
 * text, and whether the result is confident enough to put in the user's field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScanCoordinatorTest {

    private val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private fun coordinator(text: String, dateFormat: DateFormat = DateFormat.DAY_FIRST) = ScanCoordinator(
        ocr = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap) = text
        },
        // Not available off a flagship, which is the path most users take.
        extractor = GeminiNanoExtractor(),
        settings = FakeSettingsRepository(AppSettings(dateFormat = dateFormat)),
        today = fixedToday(),
        io = Dispatchers.Unconfined,
    )

    private fun failingOcr() = ScanCoordinator(
        ocr = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): String = error("camera exploded")
        },
        extractor = GeminiNanoExtractor(),
        settings = FakeSettingsRepository(),
        today = fixedToday(),
        io = Dispatchers.Unconfined,
    )

    @Test
    fun `blank text produces nothing rather than a guess`() = runTest {
        val outcome = coordinator("").scan(bitmap)
        assertTrue(outcome.foundNothing)
        assertNull(outcome.prefill)
    }

    @Test
    fun `a failing OCR call degrades instead of propagating`() = runTest {
        // A crash here would take down the add sheet the user is standing in.
        val outcome = failingOcr().scan(bitmap)
        assertTrue(outcome.foundNothing)
    }

    @Test
    fun `text with no date at all produces nothing`() = runTest {
        val outcome = coordinator("ORGANIC WHOLE MILK\n1 LITRE").scan(bitmap)
        assertNull(outcome.prefill)
    }

    @Test
    fun `a clearly labeled date is prefilled`() = runTest {
        val outcome = coordinator("BEST BEFORE 24/11/2027").scan(bitmap)
        assertEquals(24, outcome.prefill?.day)
        assertEquals(11, outcome.prefill?.month)
        assertEquals(2027, outcome.prefill?.year)
    }

    @Test
    fun `the user's date format decides an ambiguous reading`() = runTest {
        // 03/04 is the 3rd of April in the UK and the 4th of March in the US. Guessing
        // wrong here silently sets a date a month out.
        val dayFirst = coordinator("EXP 03/04/2027", DateFormat.DAY_FIRST).scan(bitmap)
        assertEquals(3, dayFirst.prefill?.day)
        assertEquals(4, dayFirst.prefill?.month)

        val monthFirst = coordinator("EXP 03/04/2027", DateFormat.MONTH_FIRST).scan(bitmap)
        assertEquals(4, monthFirst.prefill?.day)
        assertEquals(3, monthFirst.prefill?.month)
    }

    @Test
    fun `alternatives never repeat the prefilled date`() = runTest {
        // The UI offers alternatives as "did you mean", so echoing the chosen one back
        // would read as a bug.
        val outcome = coordinator("BEST BEFORE 24/11/2027\nPACKED 01/01/2027").scan(bitmap)
        assertTrue(outcome.alternatives.none { it == outcome.prefill })
    }

    @Test
    fun `suggests a product name from the longest wordy line`() = runTest {
        val outcome = coordinator("500g\nOrganic Greek Yoghurt\nEXP 24/11/2027").scan(bitmap)
        assertEquals("Organic greek yoghurt", outcome.suggestedName)
    }

    @Test
    fun `does not mistake a barcode or a date for a name`() = runTest {
        val outcome = coordinator("5012345678900\n24/11/2027").scan(bitmap)
        assertNull(outcome.suggestedName)
    }

    @Test
    fun `reports honestly that the on-device model was not used`() = runTest {
        val outcome = coordinator("BEST BEFORE 24/11/2027").scan(bitmap)
        assertTrue(!outcome.usedOnDeviceModel)
    }
}
