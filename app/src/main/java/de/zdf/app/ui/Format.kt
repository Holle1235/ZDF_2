package de.zdf.app.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

val DE: Locale = Locale.GERMANY

fun money(cents: Long, currency: String = "EUR", signed: Boolean = false): String {
    val f = NumberFormat.getCurrencyInstance(DE)
    runCatching { f.currency = Currency.getInstance(currency) }
    val s = f.format(cents / 100.0)
    return if (signed && cents > 0) "+$s" else s
}

fun moneyShort(cents: Long): String {
    val e = cents / 100.0
    return when {
        kotlin.math.abs(e) >= 10_000 -> String.format(DE, "%.1fk €", e / 1000)
        else -> String.format(DE, "%.0f €", e)
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", DE)
private val shortFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", DE)

fun dayHeader(iso: String): String {
    val d = LocalDate.parse(iso)
    val today = LocalDate.now()
    return when (d) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> d.format(dayFmt)
    }
}

fun shortDate(d: LocalDate): String = d.format(shortFmt)

fun monthName(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL_STANDALONE, DE).replaceFirstChar { it.uppercase() } + " " + m.year

fun monthShort(m: YearMonth): String = m.month.getDisplayName(TextStyle.SHORT_STANDALONE, DE).take(3)

/** "12,34" / "12.34" / "-5" → Cent */
fun parseUserAmount(s: String): Long? = de.zdf.app.importer.CsvImporter.parseAmount(s)
