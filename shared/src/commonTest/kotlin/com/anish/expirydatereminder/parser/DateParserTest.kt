package com.anish.expirydatereminder.parser

import com.anish.expirydatereminder.domain.model.DateFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DateParserTest {
    private fun parser(format: DateFormat = DateFormat.DAY_FIRST) =
        DateParser(currentYear = CURRENT_YEAR, preferredFormat = format)

    @Test
    fun `reads a keyword-anchored slash date`() {
        val best = parser().parse("EXP 31/03/2027").best
        assertNotNull(best)
        assertEquals(31, best.date.day)
        assertEquals(3, best.date.month)
        assertEquals(2027, best.date.year)
        assertEquals(DateCandidate.Evidence.EXPIRY_KEYWORD, best.evidence)
    }

    @Test
    fun `reads European dotted dates`() {
        val best = parser().parse("MHD 31.03.2027").best
        assertNotNull(best)
        assertEquals(31, best.date.day)
        assertEquals(3, best.date.month)
    }

    @Test
    fun `expands two-digit years into the current century`() {
        val best = parser().parse("BEST BEFORE 31/03/27").best
        assertNotNull(best)
        assertEquals(2027, best.date.year)
    }

    @Test
    fun `month-year only resolves to the last day of that month`() {
        val best = parser().parse("EXP 02/2028").best
        assertNotNull(best)
        assertEquals(2, best.date.month)
        // 2028 is a leap year.
        assertEquals(29, best.date.day)
    }

    @Test
    fun `reads month names in each shipping locale`() {
        val cases =
            mapOf(
                "BEST BEFORE 15 MAR 2027" to 3,
                "MINDESTENS HALTBAR BIS 15 DEZ 2027" to 12,
                "A CONSOMMER DE PREFERENCE AVANT 15 AOUT 2027" to 8,
                "CONSUMIR PREFERENTEMENTE ANTES DEL 15 ENE 2027" to 1,
                "DA CONSUMARSI PREFERIBILMENTE ENTRO 15 GIU 2027" to 6,
                "TEN MINSTE HOUDBAAR TOT 15 MEI 2027" to 5,
            )
        for ((text, expectedMonth) in cases) {
            val best = parser().parse(text).best
            assertNotNull(best, "no date found in: $text")
            assertEquals(expectedMonth, best.date.month, "wrong month for: $text")
        }
    }

    @Test
    fun `prefers the expiry date over a manufacture date on the same packet`() {
        val best = parser().parse("MFG 01/03/2025 EXP 01/03/2027").best
        assertNotNull(best)
        assertEquals(2027, best.date.year)
    }

    @Test
    fun `excludes dates anchored only to a manufacture keyword`() {
        val result = parser().parse("HERGESTELLT AM 01.03.2025")
        assertTrue(result.candidates.none { it.date.year == 2025 })
    }

    @Test
    fun `excludes lot codes that look like dates`() {
        val result = parser().parse("LOT 12/24/2026")
        assertTrue(result.candidates.isEmpty(), "lot-anchored date should be excluded")
    }

    @Test
    fun `rejects implausible far-future dates from OCR misreads`() {
        assertNull(parser().parse("EXP 31/03/2099").best)
    }

    @Test
    fun `rejects calendar-invalid dates`() {
        assertNull(parser().parse("EXP 31/02/2027").best)
    }

    @Test
    fun `disambiguates using the users date format preference`() {
        val dayFirst = parser(DateFormat.DAY_FIRST).parse("EXP 03/04/2027").best
        assertNotNull(dayFirst)
        assertEquals(3, dayFirst.date.day)
        assertEquals(4, dayFirst.date.month)

        val monthFirst = parser(DateFormat.MONTH_FIRST).parse("EXP 03/04/2027").best
        assertNotNull(monthFirst)
        assertEquals(4, monthFirst.date.day)
        assertEquals(3, monthFirst.date.month)
    }

    @Test
    fun `flags ambiguous dates and does not autofill them`() {
        val result = parser().parse("03/04/2027")
        assertTrue(result.isAmbiguous)
        assertTrue(!result.shouldAutofill, "an unanchored ambiguous date must not autofill")
    }

    @Test
    fun `resolves ordering unambiguously when one number exceeds twelve`() {
        val best = parser(DateFormat.MONTH_FIRST).parse("EXP 25/03/2027").best
        assertNotNull(best)
        assertEquals(25, best.date.day)
        assertEquals(3, best.date.month)
        assertTrue(!best.dayMonthAmbiguous)
    }

    @Test
    fun `keyword-anchored unambiguous dates clear the autofill threshold`() {
        assertTrue(parser().parse("USE BY 25/03/2027").shouldAutofill)
    }

    @Test
    fun `returns nothing for text containing no date`() {
        val result = parser().parse("ORGANIC WHOLE MILK 500ML")
        assertTrue(result.candidates.isEmpty())
        assertTrue(!result.shouldAutofill)
    }

    @Test
    fun `tolerates missing accents from OCR`() {
        val best = parser().parse("A CONSOMMER DE PREFERENCE AVANT LE 31/03/2027").best
        assertNotNull(best)
        assertEquals(DateCandidate.Evidence.EXPIRY_KEYWORD, best.evidence)
    }

    private companion object {
        const val CURRENT_YEAR = 2026
    }
}
