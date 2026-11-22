package de.zdf.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

enum class Interval(val label: String, val days: Int, val perMonth: Double) {
    WEEKLY("wöchentlich", 7, 52.0 / 12),
    MONTHLY("monatlich", 30, 1.0),
    QUARTERLY("vierteljährlich", 91, 1.0 / 3),
    HALF_YEARLY("halbjährlich", 182, 1.0 / 6),
    YEARLY("jährlich", 365, 1.0 / 12),
}

data class Contract(
    val key: String,
    val name: String,
    val category: String,
    /** typischer Betrag pro Zahlung (negativ = Ausgabe) */
    val amountCents: Long,
    val interval: Interval,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val occurrences: Int,
    val accountId: String,
) {
    val monthlyCents: Long get() = (amountCents * interval.perMonth).roundToLong()
    val isIncome: Boolean get() = amountCents > 0
}

/**
 * Erkennt wiederkehrende Zahlungen (Verträge, Abos, Gehalt) anhand von
 * Empfänger, Betrag und Zahlungsabstand.
 */
object ContractDetector {

    fun key(counterparty: String, purpose: String): String {
        val base = counterparty.ifBlank { purpose }
        return base.lowercase()
            .replace(Regex("[0-9]+"), " ")
            .replace(Regex("[^\\p{L} ]"), " ")
            .split(' ')
            .filter { it.length > 1 && it !in stopWords }
            .take(3)
            .joinToString(" ")
    }

    private val stopWords = setOf("gmbh", "ag", "se", "kg", "co", "ug", "mbh", "ev", "sagt", "danke", "lastschrift", "sepa", "dauerauftrag", "europe", "sarl", "sa", "ltd", "inc", "bv", "nv", "und", "the")

    /** Kategorien mit typischerweise unregelmäßigen Einkäufen sind keine Verträge. */
    private val excluded = setOf(Categories.TRANSFER, "groceries", "restaurants", "shopping", "cash")

    fun detect(txns: List<Txn>, today: LocalDate = LocalDate.now()): List<Contract> {
        val result = mutableListOf<Contract>()
        val groups = txns
            .filter { !it.pending && it.category !in excluded && it.amountCents != 0L }
            .groupBy { Triple(key(it.counterparty, it.purpose), it.amountCents > 0, it.accountId) }

        for ((k, list) in groups) {
            if (k.first.isBlank() || list.size < 2) continue
            val sorted = list.sortedBy { it.date }
            // Beträge clustern: Median, Abweichung max. 25 %
            val median = sorted.map { it.amountCents }.sorted()[sorted.size / 2]
            val similar = sorted.filter { abs(it.amountCents - median) <= abs(median) * 0.15 + 100 }
            if (similar.size < 2) continue
            // pro Tag nur eine Buchung
            val dates = similar.map { LocalDate.parse(it.date) }.distinct().sorted()
            if (dates.size < 2) continue
            val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
            val medGap = gaps.sorted()[gaps.size / 2]
            val interval = when (medGap) {
                in 6..8 -> Interval.WEEKLY
                in 26..35 -> Interval.MONTHLY
                in 84..98 -> Interval.QUARTERLY
                in 172..195 -> Interval.HALF_YEARLY
                in 350..380 -> Interval.YEARLY
                else -> null
            } ?: continue
            val minCount = if (interval == Interval.WEEKLY || interval == Interval.MONTHLY) 3 else 2
            if (dates.size < minCount) continue
            // Regelmäßigkeit: die meisten Abstände passen zum Intervall
            val tolerance = maxOf(3, interval.days / 6)
            val regular = gaps.count { abs(it - interval.days) <= tolerance }
            if (regular < (gaps.size * 0.6)) continue
            val last = dates.last()
            // inaktiv, wenn die letzte Zahlung zu lange her ist
            if (ChronoUnit.DAYS.between(last, today) > interval.days * 1.5 + 10) continue

            val lastTxn = similar.last()
            var next = when (interval) {
                Interval.WEEKLY -> last.plusWeeks(1)
                Interval.MONTHLY -> last.plusMonths(1)
                Interval.QUARTERLY -> last.plusMonths(3)
                Interval.HALF_YEARLY -> last.plusMonths(6)
                Interval.YEARLY -> last.plusYears(1)
            }
            while (next.isBefore(today)) next = next.plusDays(interval.days.toLong())
            result += Contract(
                key = "${k.first}|${if (k.second) "in" else "out"}|${k.third}",
                name = lastTxn.counterparty.ifBlank { lastTxn.purpose }.take(60),
                category = lastTxn.category,
                amountCents = lastTxn.amountCents,
                interval = interval,
                lastDate = last,
                nextDate = next,
                occurrences = dates.size,
                accountId = lastTxn.accountId,
            )
        }
        return result.sortedBy { it.monthlyCents }
    }
}
