package com.anish.expirydatereminder.camera

import android.util.Log
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.lengthOfMonth
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional refinement pass using on-device Gemini Nano, via ML Kit's GenAI Prompt API.
 *
 * Strictly an enhancement. Nano is flagship-only (Pixel 9/10, Galaxy S25/S26 and a
 * handful of others) and the API is beta with no SLA, so every path here degrades to
 * "return null" and the deterministic [com.anish.expirydatereminder.parser.DateParser]
 * result stands. The scan feature must work identically on a device with no Nano at all.
 *
 * The model receives the OCR *text*, never the image: it is smaller, faster, and keeps
 * photo bytes out of a general-purpose inference path.
 */
class GeminiNanoExtractor {

    private var model: GenerativeModel? = null

    /**
     * Checks whether Nano can run here. Never triggers a download: a multi-hundred-MB
     * fetch on a metered connection is not something to start behind the user's back for
     * an optional convenience.
     */
    suspend fun isAvailable(): Boolean = runCatching {
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            client().checkStatus() == FeatureStatus.AVAILABLE
        } ?: false
    }.getOrElse {
        Log.d(TAG, "Gemini Nano unavailable on this device", it)
        false
    }

    /**
     * @return the resolved expiry date, or null if the model was unavailable, timed out,
     *   or produced anything that did not parse cleanly.
     */
    suspend fun refine(ocrText: String): ExpiryDate? {
        if (!isAvailable()) return null
        val prompt = buildPrompt(ocrText)
        return runCatching {
            withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                val response = client().generateContent(prompt)
                response.candidates.firstOrNull()?.text?.let(::parseIsoish)
            }
        }.getOrElse {
            Log.d(TAG, "Gemini Nano refinement failed", it)
            null
        }
    }

    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }

    fun close() {
        runCatching { model?.close() }
        model = null
    }

    /**
     * Constrained hard on purpose. A chatty model that explains itself is useless here;
     * anything that does not match the expected shape is discarded rather than salvaged.
     */
    private fun buildPrompt(ocrText: String) = """
        You are reading text scanned from food or medicine packaging.
        Find the EXPIRY date only. Ignore manufacture dates, packaging dates, lot codes
        and batch numbers.
        Reply with exactly one line in the form YYYY-MM-DD and nothing else.
        If the text has a month and year but no day, use the last day of that month.
        If there is no expiry date, reply exactly: NONE

        TEXT:
        $ocrText
    """.trimIndent()

    /**
     * Reads the model's reply, which is asked for in ISO form but is not guaranteed to be.
     *
     * Every field is checked rather than trusted. A model that answers "2027-13-45" has to
     * produce nothing at all, because a wrong date quietly placed in the user's field is
     * worse than an empty one they fill in themselves.
     */
    private fun parseIsoish(raw: String): ExpiryDate? {
        val match = ISO_DATE.find(raw.trim()) ?: return null
        val (yearText, monthText, dayText) = match.destructured

        val year = yearText.toIntOrNull() ?: return null
        val month = monthText.toIntOrNull() ?: return null
        val day = dayText.toIntOrNull() ?: return null

        if (month !in 1..MONTHS_IN_YEAR) return null
        if (day !in 1..lengthOfMonth(year, month)) return null

        return ExpiryDate(day, month, year)
    }

    private companion object {
        const val TAG = "GeminiNanoExtractor"
        const val MONTHS_IN_YEAR = 12
        const val STATUS_TIMEOUT_MS = 2_000L

        /**
         * Kept short deliberately. The user is waiting in front of an editable field; if
         * the model has not answered by now, letting them type is the better experience.
         */
        const val INFERENCE_TIMEOUT_MS = 6_000L

        val ISO_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    }
}
