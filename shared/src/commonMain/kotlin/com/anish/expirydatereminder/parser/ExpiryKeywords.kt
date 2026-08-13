package com.anish.expirydatereminder.parser

/**
 * Keyword banks for the seven shipping locales plus English.
 *
 * Order matters within each list only insofar as longer phrases must be matched before
 * their abbreviations, which [normalize] and the longest-first sort in [ExpiryKeywords]
 * take care of.
 */
object ExpiryKeywords {
    /** Markers that a nearby date is the expiry date. */
    val EXPIRY: List<String> =
        listOf(
            // English
            "BEST BEFORE END",
            "BEST BEFORE",
            "BEST BY",
            "USE BY",
            "EXPIRY DATE",
            "EXPIRES",
            "EXPIRATION",
            "EXP DATE",
            "EXP",
            "BBE",
            "BB",
            "SELL BY",
            // German
            "MINDESTENS HALTBAR BIS ENDE",
            "MINDESTENS HALTBAR BIS",
            "VERWENDBAR BIS",
            "ZU VERBRAUCHEN BIS",
            "HALTBAR BIS",
            "MHD",
            // French
            "A CONSOMMER DE PREFERENCE AVANT LE",
            "A CONSOMMER DE PREFERENCE AVANT",
            "A CONSOMMER JUSQU AU",
            "DATE LIMITE DE CONSOMMATION",
            "DLUO",
            "DLC",
            "DDM",
            // Spanish
            "CONSUMIR PREFERENTEMENTE ANTES DEL",
            "CONSUMIR PREFERENTEMENTE ANTES DE",
            "FECHA DE CADUCIDAD",
            "CADUCIDAD",
            "CONSUMIR ANTES DE",
            "CAD",
            // Italian
            "DA CONSUMARSI PREFERIBILMENTE ENTRO IL",
            "DA CONSUMARSI PREFERIBILMENTE ENTRO",
            "DA CONSUMARSI ENTRO",
            "SCADENZA",
            "SCAD",
            // Dutch
            "TEN MINSTE HOUDBAAR TOT",
            "TE GEBRUIKEN TOT",
            "HOUDBAAR TOT",
            "THT",
            "TGT",
            // Portuguese
            "CONSUMIR DE PREFERENCIA ANTES DE",
            "VALIDADE",
            "VAL",
            "CONSUMIR ATE",
            // Polish
            "NAJLEPIEJ SPOZYC PRZED KONCEM",
            "NAJLEPIEJ SPOZYC PRZED",
            "NALEZY SPOZYC DO",
            "TERMIN WAZNOSCI",
        ).sortedByDescending { it.length }

    /**
     * Markers that a nearby date is NOT the expiry date. A packet showing both a
     * manufacture date and an expiry date is the single most common failure mode for a
     * naive "first date wins" parser.
     */
    val MANUFACTURE: List<String> =
        listOf(
            // English
            "MANUFACTURED ON",
            "MANUFACTURE DATE",
            "PACKED ON",
            "DATE OF MANUFACTURE",
            "PRODUCTION DATE",
            "MFG DATE",
            "MFD",
            "MFG",
            "PROD",
            "PKD",
            // German
            "HERGESTELLT AM",
            "PRODUKTIONSDATUM",
            "ABGEFULLT AM",
            "HERGESTELLT",
            // French
            "FABRIQUE LE",
            "DATE DE FABRICATION",
            "EMBALLE LE",
            "FAB",
            // Spanish
            "FECHA DE FABRICACION",
            "FABRICADO EL",
            "ENVASADO EL",
            "FAB",
            // Italian
            "PRODOTTO IL",
            "DATA DI PRODUZIONE",
            "CONFEZIONATO IL",
            // Dutch
            "GEPRODUCEERD OP",
            "PRODUCTIEDATUM",
            "VERPAKT OP",
            // Portuguese
            "FABRICADO EM",
            "DATA DE FABRICACAO",
            "EMBALADO EM",
            // Polish
            "DATA PRODUKCJI",
            "WYPRODUKOWANO",
        ).sortedByDescending { it.length }

    /** Lot and batch codes routinely contain digit groups that look like dates. */
    val LOT: List<String> =
        listOf(
            "LOT NO",
            "LOT",
            "BATCH NO",
            "BATCH",
            "CHARGE",
            "CHARGENUMMER",
            "PARTIDA",
            "LOTE",
            "LOTTO",
            "PARTIJ",
            "SERIA",
            "L/N",
            "BN",
        ).sortedByDescending { it.length }

    /** Month names and abbreviations across the shipping locales, mapped to month number. */
    val MONTH_NAMES: Map<String, Int> =
        buildMap {
            // Space-separated rather than a vararg per month: the Polish and Portuguese
            // additions pushed several of these past the line limit, and one string per
            // month keeps the table scannable as a table.
            fun put(month: Int, names: String) = names.split(' ').forEach { put(it, month) }
            put(1, "JAN JANUARY JANUAR JANVIER ENERO ENE GENNAIO GEN JANEIRO STYCZEN STY")
            put(2, "FEB FEBRUARY FEBRUAR FEVRIER FEV FEBRERO FEBBRAIO FEVEREIRO LUTY LUT")
            put(3, "MAR MARCH MARZ MAERZ MARS MARZO MARCO MARZEC MZC")
            put(4, "APR APRIL AVR AVRIL ABR ABRIL APRILE KWIECIEN KWI")
            put(5, "MAY MAI MEI MAYO MAGGIO MAG MAIO MAJ")
            put(6, "JUN JUNE JUNI JUIN JUNIO GIUGNO GIU JUNHO CZERWIEC CZE")
            put(7, "JUL JULY JULI JUIL JUILLET JULIO LUGLIO LUG JULHO LIPIEC LIP")
            put(8, "AUG AUGUST AOUT AGO AGOSTO SIERPIEN SIE")
            put(9, "SEP SEPT SEPTEMBER SEPTEMBRE SEPTIEMBRE SETTEMBRE SET SETEMBRO WRZESIEN WRZ")
            put(10, "OCT OCTOBER OKT OKTOBER OCTOBRE OCTUBRE OTTOBRE OTT OUT OUTUBRO PAZDZIERNIK PAZ")
            put(11, "NOV NOVEMBER NOVEMBRE NOVIEMBRE NOVEMBRO LISTOPAD LIS")
            put(12, "DEC DECEMBER DEZ DEZEMBER DECEMBRE DIC DICIEMBRE DICEMBRE DEZEMBRO GRUDZIEN GRU")
        }

    /** Separators that may appear inside a date and must survive normalization. */
    private const val DATE_SEPARATORS = "/.-"

    /**
     * How far back a date must look to find its keyword. Derived rather than hardcoded:
     * the longest marker is `DA CONSUMARSI PREFERIBILMENTE ENTRO IL` at 38 characters, so
     * a fixed 32-character window silently scored every long European phrase as
     * unanchored.
     */
    val longestKeywordLength: Int by lazy {
        (EXPIRY + MANUFACTURE + LOT).maxOf { it.length }
    }

    /**
     * Uppercases and strips diacritics so `À CONSOMMER` matches `A CONSOMMER` and OCR
     * output that dropped the accent still hits. Collapses punctuation to spaces, except
     * for date separators, since `31.03.2027` must not become `31 03 2027`.
     */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.uppercase()) {
            val replacement =
                when (ch) {
                    'À', 'Á', 'Â', 'Ã', 'Ä', 'Å', 'Ą' -> 'A'
                    'È', 'É', 'Ê', 'Ë', 'Ę' -> 'E'
                    'Ì', 'Í', 'Î', 'Ï' -> 'I'
                    'Ò', 'Ó', 'Ô', 'Õ', 'Ö', 'Ó' -> 'O'
                    'Ù', 'Ú', 'Û', 'Ü' -> 'U'
                    'Ç', 'Ć' -> 'C'
                    'Ñ', 'Ń' -> 'N'
                    'Ł' -> 'L'
                    'Ś' -> 'S'
                    'Ź', 'Ż' -> 'Z'
                    'ß' -> 'S'
                    else -> ch
                }
            sb.append(
                if (replacement.isLetterOrDigit() || replacement in DATE_SEPARATORS) replacement else ' ',
            )
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
