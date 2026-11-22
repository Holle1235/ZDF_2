package de.zdf.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Erkennt Umbuchungen zwischen eigenen Konten (z. B. N26-Spaces, Bank → PayPal):
 * gleicher Betrag mit umgekehrtem Vorzeichen auf zwei verschiedenen eigenen Konten
 * innerhalb von [MAX_DAYS] Tagen. Beide Buchungen werden als Umbuchung markiert und
 * zählen dadurch weder als Einnahme noch als Ausgabe.
 */
object TransferMatcher {
    const val MAX_DAYS = 3

    fun apply(d: AppData): AppData {
        if (d.accounts.size < 2) return d
        val candidates = d.txns.filter { !it.categoryManual && it.amountCents != 0L }
        val byAmount = candidates.filter { it.amountCents > 0 }.groupBy { it.amountCents }
        val paired = HashSet<String>()
        for (out in candidates.filter { it.amountCents < 0 }.sortedBy { it.date }) {
            val dOut = LocalDate.parse(out.date)
            val match = byAmount[-out.amountCents]
                ?.filter { it.id !in paired && it.accountId != out.accountId }
                ?.minByOrNull { abs(ChronoUnit.DAYS.between(dOut, LocalDate.parse(it.date))) }
                ?.takeIf { abs(ChronoUnit.DAYS.between(dOut, LocalDate.parse(it.date))) <= MAX_DAYS }
                ?: continue
            paired += out.id
            paired += match.id
        }
        if (paired.isEmpty()) return d
        return d.copy(txns = d.txns.map { if (it.id in paired && it.category != Categories.TRANSFER) it.copy(category = Categories.TRANSFER) else it })
    }
}
