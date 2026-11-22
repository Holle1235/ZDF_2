package de.zdf.app.data

import java.time.LocalDate
import kotlin.random.Random

/** Beispieldaten, um die App ohne echte Konten auszuprobieren. */
object DemoData {
    fun generate(base: AppData): AppData {
        val rnd = Random(42)
        val giro = Account("demo-giro", "Girokonto (Demo)", Source.DEMO, iban = "DE89 3704 0044 0532 0130 00", balanceCents = 2_384_17, institution = "Demo-Bank", balanceDate = LocalDate.now().toString())
        val pp = Account("demo-paypal", "PayPal (Demo)", Source.DEMO, balanceCents = 12_40, institution = "PayPal", balanceDate = LocalDate.now().toString())
        val tx = mutableListOf<Txn>()
        var n = 0
        fun add(acc: Account, date: LocalDate, cents: Long, cp: String, purpose: String = "") {
            if (date.isAfter(LocalDate.now())) return
            tx += Txn("demo-${n++}", acc.id, date.toString(), cents, counterparty = cp, purpose = purpose)
        }
        val start = LocalDate.now().withDayOfMonth(1).minusMonths(6)
        var m = start
        while (!m.isAfter(LocalDate.now())) {
            add(giro, m.withDayOfMonth(1), 3_150_00, "Muster GmbH", "Gehalt ${m.monthValue}/${m.year}")
            add(giro, m.withDayOfMonth(2), -950_00, "Hausverwaltung Schmidt", "Miete Wohnung")
            add(giro, m.withDayOfMonth(3), -85_00, "Stadtwerke", "Abschlag Strom/Gas")
            add(giro, m.withDayOfMonth(5), -39_99, "Telekom Deutschland GmbH", "Mobilfunk + DSL")
            add(giro, m.withDayOfMonth(6), -13_99, "Netflix International B.V.", "Netflix Abo")
            add(giro, m.withDayOfMonth(8), -10_99, "Spotify AB", "Spotify Premium")
            add(giro, m.withDayOfMonth(10), -49_00, "Deutsche Bahn", "Deutschlandticket")
            add(giro, m.withDayOfMonth(15), -24_90, "HUK-Coburg", "Haftpflicht/Hausrat")
            add(giro, m.withDayOfMonth(15), -200_00, "Trade Republic", "Sparplan ETF")
            add(giro, m.withDayOfMonth(20), -29_90, "McFit GmbH", "Mitgliedsbeitrag")
            repeat(9) {
                val shop = listOf("REWE Markt", "EDEKA", "ALDI SUED", "LIDL", "dm-drogerie markt").random(rnd)
                add(giro, m.withDayOfMonth(rnd.nextInt(1, 28)), -rnd.nextLong(8_00, 95_00), shop, "Kartenzahlung")
            }
            repeat(3) { add(giro, m.withDayOfMonth(rnd.nextInt(1, 28)), -rnd.nextLong(15_00, 60_00), listOf("Lieferando", "Pizzeria Roma", "Starbucks").random(rnd), "") }
            repeat(2) { add(giro, m.withDayOfMonth(rnd.nextInt(1, 28)), -rnd.nextLong(30_00, 70_00), listOf("ARAL Tankstelle", "Shell").random(rnd), "Tanken") }
            add(giro, m.withDayOfMonth(12), -rnd.nextLong(40_00, 120_00), "PayPal Europe S.a.r.l.", "PayPal Zahlung")
            add(pp, m.withDayOfMonth(12), -rnd.nextLong(40_00, 120_00), "Zalando SE", "Bestellung")
            add(pp, m.withDayOfMonth(18), -rnd.nextLong(10_00, 40_00), "Amazon EU", "Bestellung")
            add(giro, m.withDayOfMonth(rnd.nextInt(1, 28)), -100_00, "Geldautomat Sparkasse", "Bargeldauszahlung")
            m = m.plusMonths(1)
        }
        add(giro, start.plusMonths(1).withDayOfMonth(20), -89_00, "ADAC e.V.", "Jahresbeitrag")
        return base.copy(
            accounts = base.accounts.filterNot { it.source == Source.DEMO } + giro + pp,
            txns = base.txns.filterNot { it.accountId.startsWith("demo-") } + tx,
        )
    }
}
