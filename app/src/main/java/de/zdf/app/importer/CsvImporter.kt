package de.zdf.app.importer

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CsvRow(
    val date: String,
    val amountCents: Long,
    val currency: String,
    val counterparty: String,
    val purpose: String,
)

data class CsvResult(val rows: List<CsvRow>, val skipped: Int, val detected: String)

/**
 * Liest Kontoauszüge als CSV – erkennt Trennzeichen, Kodierung und Spalten automatisch.
 * Getestet mit typischen Exporten deutscher Banken (Sparkasse, Volksbank, DKB, ING, comdirect,
 * N26, Commerzbank …) und dem PayPal-Aktivitätenbericht.
 */
object CsvImporter {

    private val dateCols = listOf("buchungstag", "buchungsdatum", "buchung", "datum", "date", "booking date", "transaktionsdatum", "wertstellung", "valutadatum", "valuta", "value date")
    private val amountCols = listOf("betrag", "betrag (€)", "betrag (eur)", "betrag in eur", "umsatz", "brutto", "amount", "amount (eur)", "gross", "umsatz in eur")
    private val debitCols = listOf("soll", "ausgang", "belastung", "debit")
    private val creditCols = listOf("haben", "eingang", "gutschrift", "credit")
    private val nameCols = listOf("beguenstigter/zahlungspflichtiger", "begünstigter/zahlungspflichtiger", "name zahlungsbeteiligter", "zahlungsempfänger*in", "zahlungsempfänger", "empfänger", "auftraggeber/empfänger", "auftraggeber / begünstigter", "begünstigter / auftraggeber", "auftraggeber", "begünstigter", "payee", "name", "counterparty", "gegenseite")
    private val purposeCols = listOf("verwendungszweck", "betreff", "beschreibung", "buchungstext", "umsatztext", "vorgang", "description", "subject", "verwendung", "hinweis", "transaction type", "typ")
    private val currencyCols = listOf("währung", "waehrung", "currency", "wkz")
    private val statusCols = listOf("status")

    fun decode(bytes: ByteArray): String {
        val utf = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        val text = try {
            utf.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix("﻿")
    }

    fun parse(bytes: ByteArray): CsvResult = parseText(decode(bytes))

    fun parseText(input: String): CsvResult {
        val lines = input.removePrefix("\uFEFF").lines()
        // Kopfzeile suchen (Banken setzen oft Metadaten davor)
        var headerIdx = -1
        var delim = ';'
        for ((i, line) in lines.withIndex().take(40)) {
            val l = line.lowercase()
            val d = listOf(';', ',', '\t').maxBy { c -> line.count { it == c } }
            val cells = split(line, d).map { it.trim().lowercase() }
            val hasDate = cells.any { c -> dateCols.any { c == it || c.startsWith(it) } }
            val hasAmount = cells.any { c -> (amountCols + debitCols + creditCols).any { c == it || c.startsWith(it) } }
            if (hasDate && hasAmount && l.isNotBlank()) { headerIdx = i; delim = d; break }
        }
        require(headerIdx >= 0) { "Keine Kopfzeile mit Datum und Betrag gefunden" }
        val header = split(lines[headerIdx], delim).map { it.trim().lowercase() }

        fun col(cands: List<String>, exclude: Set<Int> = emptySet()): Int {
            for (c in cands) header.indexOfFirst { it == c }.takeIf { it >= 0 && it !in exclude }?.let { return it }
            for (c in cands) header.indexOfFirst { it.startsWith(c) }.takeIf { it >= 0 && it !in exclude }?.let { return it }
            return -1
        }

        val iDate = col(dateCols)
        val iAmount = col(amountCols)
        val iDebit = if (iAmount < 0) col(debitCols) else -1
        val iCredit = if (iAmount < 0) col(creditCols) else -1
        val iName = col(nameCols, setOf(iDate, iAmount))
        val iPurpose = col(purposeCols, setOf(iDate, iAmount, iName))
        val iCur = col(currencyCols)
        val iStatus = col(statusCols)

        val rows = mutableListOf<CsvRow>()
        var skipped = 0
        // Zeilen mit Zeilenumbrüchen in Anführungszeichen zusammenfügen
        val records = records(lines.drop(headerIdx + 1))
        for (rec in records) {
            if (rec.isBlank()) continue
            val c = split(rec, delim)
            fun g(i: Int) = if (i in c.indices) c[i].trim() else ""
            val date = parseDate(g(iDate))
            val status = g(iStatus).lowercase()
            if (status.contains("storniert") || status.contains("abgelehnt") || status.contains("denied")) { skipped++; continue }
            val amount = when {
                iAmount >= 0 -> parseAmount(g(iAmount))
                else -> {
                    val d = parseAmount(g(iDebit))?.let { -kotlin.math.abs(it) } ?: 0
                    val h = parseAmount(g(iCredit))?.let { kotlin.math.abs(it) } ?: 0
                    if (g(iDebit).isBlank() && g(iCredit).isBlank()) null else d + h
                }
            }
            if (date == null || amount == null) { skipped++; continue }
            rows += CsvRow(
                date = date.toString(),
                amountCents = amount,
                currency = g(iCur).uppercase().takeIf { it.length == 3 } ?: "EUR",
                counterparty = g(iName),
                purpose = g(iPurpose),
            )
        }
        val detected = "Trennzeichen '${if (delim == '\t') "TAB" else delim}', Spalten: " +
            listOf("Datum" to iDate, "Betrag" to maxOf(iAmount, iDebit), "Name" to iName, "Zweck" to iPurpose)
                .filter { it.second >= 0 }.joinToString { "${it.first}=„${header[it.second]}“" }
        return CsvResult(rows, skipped, detected)
    }

    private fun records(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (l in lines) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(l)
            if (sb.count { it == '"' } % 2 == 0) { out += sb.toString(); sb.clear() }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    fun split(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delim && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    private val dateFormats = listOf("dd.MM.yyyy", "d.M.yyyy", "dd.MM.yy", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd")
        .map { DateTimeFormatter.ofPattern(it) }

    fun parseDate(s: String): LocalDate? {
        val v = s.trim().take(10).trim()
        if (v.isEmpty()) return null
        for (f in dateFormats) {
            try {
                val d = LocalDate.parse(v, f)
                return if (d.year < 100) d.plusYears(2000) else d
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** "1.234,56", "-12,50 €", "1,234.56", "12.5", "+3,00" → Cent */
    fun parseAmount(raw: String): Long? {
        var s = raw.replace("€", "").replace("EUR", "").replace(" ", "").replace(" ", "").replace("'", "").trim()
        if (s.isEmpty()) return null
        var neg = false
        if (s.endsWith("-")) { neg = true; s = s.dropLast(1) }
        if (s.startsWith("(") && s.endsWith(")")) { neg = true; s = s.drop(1).dropLast(1) }
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> s.replace(".", "").replace(',', '.')
            lastDot >= 0 && Regex("^[+-]?\\d{1,3}(\\.\\d{3})+$").matches(s) -> s.replace(".", "")
            else -> s
        }
        return try {
            val v = BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
            if (neg) -v else v
        } catch (_: NumberFormatException) {
            null
        }
    }
}
