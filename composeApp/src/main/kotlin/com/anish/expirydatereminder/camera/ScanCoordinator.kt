package com.anish.expirydatereminder.camera

import android.graphics.Bitmap
import com.anish.expirydatereminder.domain.Today
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.parser.DateParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What a scan produced. Never written anywhere directly: it prefills the add/edit form,
 * where the user sees and can correct it before saving.
 */
data class ScanOutcome(
    /** Populate the date fields with this. Null means leave them alone. */
    val prefill: ExpiryDate?,
    /** Offered as chips when the result is uncertain or several dates were found. */
    val alternatives: List<ExpiryDate>,
    /** Best-effort product name suggestion; a hint, never authoritative. */
    val suggestedName: String?,
    val usedOnDeviceModel: Boolean,
) {
    val foundNothing: Boolean get() = prefill == null && alternatives.isEmpty()
}

/**
 * Runs OCR, then the deterministic parser, and escalates to Gemini Nano only when the
 * parser is unsure.
 *
 * The escalation condition matters: on a clear `EXP 03/2027` there is nothing an LLM can
 * add, and invoking one would cost latency and battery for no gain.
 */
class ScanCoordinator(
    private val ocr: OcrEngine,
    private val extractor: GeminiNanoExtractor,
    private val settings: SettingsRepository,
    private val today: Today,
    private val io: CoroutineDispatcher,
) {

    suspend fun scan(bitmap: Bitmap): ScanOutcome = withContext(io) {
        val text = runCatching { ocr.recognize(bitmap) }.getOrElse { "" }
        if (text.isBlank()) return@withContext EMPTY

        val today = today()
        val parser = DateParser(
            currentYear = today.year,
            preferredFormat = settings.current().dateFormat,
        )
        val parsed = parser.parse(text)

        val needsHelp = !parsed.shouldAutofill || parsed.isAmbiguous
        val refined = if (needsHelp) extractor.refine(text) else null

        val prefill = when {
            refined != null -> refined
            parsed.shouldAutofill -> parsed.best?.date
            // Deliberately nothing: a low-confidence guess the user does not notice is
            // worse than an empty field they fill in themselves.
            else -> null
        }

        ScanOutcome(
            prefill = prefill,
            alternatives = parsed.candidates.map { it.date }.filter { it != prefill }.take(MAX_ALTERNATIVES),
            suggestedName = suggestName(text),
            usedOnDeviceModel = refined != null,
        )
    }

    /**
     * Cheap heuristic: the longest mostly-alphabetic line is usually the product name.
     * Offered only as a prefill suggestion in an editable field, so a wrong guess costs
     * the user one edit.
     */
    private fun suggestName(text: String): String? = text.lines()
        .map { it.trim() }
        .filter { it.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH }
        .filter { line -> line.count { it.isLetter() } >= line.length * MIN_ALPHA_RATIO }
        .maxByOrNull { it.length }
        ?.lowercase()
        ?.replaceFirstChar { it.uppercase() }

    private companion object {
        val EMPTY = ScanOutcome(null, emptyList(), null, usedOnDeviceModel = false)
        const val MAX_ALTERNATIVES = 4
        const val MIN_NAME_LENGTH = 3
        const val MAX_NAME_LENGTH = 40
        const val MIN_ALPHA_RATIO = 0.6
    }
}
