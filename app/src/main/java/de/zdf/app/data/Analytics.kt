package de.zdf.app.data

import java.time.LocalDate
import java.time.YearMonth

data class MonthSummary(val month: YearMonth, val incomeCents: Long, val expenseCents: Long) {
    val netCents get() = incomeCents - expenseCents
}

object Analytics {

    fun countsForCashflow(t: Txn) = t.category != Categories.TRANSFER

    fun month(t: Txn): YearMonth = YearMonth.from(LocalDate.parse(t.date))

    fun summary(txns: List<Txn>, month: YearMonth): MonthSummary {
        var inc = 0L
        var exp = 0L
        for (t in txns) {
            if (!countsForCashflow(t) || month(t) != month) continue
            if (t.amountCents >= 0) inc += t.amountCents else exp += -t.amountCents
        }
        return MonthSummary(month, inc, exp)
    }

    fun lastMonths(txns: List<Txn>, count: Int, until: YearMonth = YearMonth.now()): List<MonthSummary> =
        (count - 1 downTo 0).map { summary(txns, until.minusMonths(it.toLong())) }

    /** Netto-Ausgaben je Kategorie (Erstattungen werden verrechnet), absteigend. */
    fun spendingByCategory(txns: List<Txn>, month: YearMonth): List<Pair<String, Long>> =
        txns.asSequence()
            .filter { countsForCashflow(it) && month(it) == month && it.category != Categories.INCOME }
            .groupBy { it.category }
            .mapValues { (_, l) -> -l.sumOf { it.amountCents } }
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }

    fun spentInCategory(txns: List<Txn>, category: String, month: YearMonth): Long =
        -txns.filter { it.category == category && countsForCashflow(it) && month(it) == month }.sumOf { it.amountCents }
}
