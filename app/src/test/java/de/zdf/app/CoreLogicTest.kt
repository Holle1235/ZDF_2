package de.zdf.app

import de.zdf.app.data.Analytics
import de.zdf.app.data.Categories
import de.zdf.app.data.Categorizer
import de.zdf.app.data.ContractDetector
import de.zdf.app.data.Interval
import de.zdf.app.data.Rule
import de.zdf.app.data.Txn
import de.zdf.app.importer.CsvImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CoreLogicTest {

    @Test fun amounts() {
        assertEquals(123456L, CsvImporter.parseAmount("1.234,56"))
        assertEquals(-1250L, CsvImporter.parseAmount("-12,50 €"))
        assertEquals(123456L, CsvImporter.parseAmount("1,234.56"))
        assertEquals(1250L, CsvImporter.parseAmount("12.5"))
        assertEquals(300L, CsvImporter.parseAmount("+3,00"))
        assertEquals(100000L, CsvImporter.parseAmount("1.000"))
        assertEquals(-500L, CsvImporter.parseAmount("5,00-"))
    }

    @Test fun sparkasseCsv() {
        val csv = """
            "Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Beguenstigter/Zahlungspflichtiger";"Kontonummer";"BLZ";"Betrag";"Waehrung";"Info"
            "DE123";"15.11.26";"15.11.26";"FOLGELASTSCHRIFT";"Netflix Abo";"NETFLIX INTERNATIONAL B.V.";"NL1";"X";"-13,99";"EUR";"Umsatz gebucht"
            "DE123";"01.11.26";"01.11.26";"GUTSCHRIFT";"Gehalt November";"Muster GmbH";"DE9";"X";"3.150,00";"EUR";"Umsatz gebucht"
        """.trimIndent()
        val r = CsvImporter.parseText(csv)
        assertEquals(2, r.rows.size)
        assertEquals("2026-11-15", r.rows[0].date)
        assertEquals(-1399L, r.rows[0].amountCents)
        assertEquals("NETFLIX INTERNATIONAL B.V.", r.rows[0].counterparty)
        assertEquals("Netflix Abo", r.rows[0].purpose)
        assertEquals(315000L, r.rows[1].amountCents)
    }

    @Test fun csvWithPreambleAndSollHaben() {
        val csv = """
            Kontonummer:;123456
            Zeitraum:;01.10.2026 - 31.10.2026

            Buchungsdatum;Empfänger;Verwendungszweck;Soll;Haben
            02.10.2026;REWE Markt;"Einkauf; Filiale 12";23,45;
            03.10.2026;Arbeitgeber AG;Lohn;;2.000,00
        """.trimIndent()
        val r = CsvImporter.parseText(csv)
        assertEquals(2, r.rows.size)
        assertEquals(-2345L, r.rows[0].amountCents)
        assertEquals("Einkauf; Filiale 12", r.rows[0].purpose)
        assertEquals(200000L, r.rows[1].amountCents)
    }

    @Test fun paypalCsv() {
        val csv = "﻿\"Datum\",\"Uhrzeit\",\"Zeitzone\",\"Name\",\"Typ\",\"Status\",\"Währung\",\"Brutto\",\"Gebühr\",\"Netto\"\n" +
            "\"10.11.2026\",\"12:00:00\",\"MEZ\",\"Zalando SE\",\"Express-Checkout-Zahlung\",\"Abgeschlossen\",\"EUR\",\"-59,90\",\"0,00\",\"-59,90\"\n"
        val r = CsvImporter.parseText(csv)
        assertEquals(1, r.rows.size)
        assertEquals(-5990L, r.rows[0].amountCents)
        assertEquals("Zalando SE", r.rows[0].counterparty)
    }

    @Test fun categorize() {
        assertEquals("groceries", Categorizer.categorize("REWE Markt GmbH", "", -2000, emptyList()))
        assertEquals("subscriptions", Categorizer.categorize("Netflix International", "", -1399, emptyList()))
        assertEquals(Categories.INCOME, Categorizer.categorize("Muster GmbH", "Gehalt", 300000, emptyList()))
        assertEquals(Categories.TRANSFER, Categorizer.categorize("PayPal Europe S.a.r.l.", "", -2000, emptyList(), hasPaypal = true))
        assertEquals("shopping", Categorizer.categorize("PayPal Europe S.a.r.l.", "Amazon", -2000, emptyList(), hasPaypal = false))
        assertEquals("leisure", Categorizer.categorize("Oma Erna", "", -2000, listOf(Rule("oma erna", "leisure"))))
        assertEquals(Categories.TRANSFER, Categorizer.categorize("Bank", "", 2000, emptyList(), isPaypalAccount = true, code = "T0300"))
    }

    @Test fun contracts() {
        val today = LocalDate.of(2026, 11, 20)
        val tx = (0 until 6).map { i ->
            Txn("n$i", "a", today.minusMonths(i.toLong()).withDayOfMonth(6).toString(), -1399, counterparty = "Netflix International B.V.", category = "subscriptions")
        } + (0 until 2).map { i ->
            Txn("y$i", "a", today.minusYears(i.toLong()).minusDays(30).toString(), -8900, counterparty = "ADAC e.V.", category = "mobility")
        } + listOf(
            Txn("r1", "a", "2026-11-02", -2345, counterparty = "REWE"),
            Txn("r2", "a", "2026-11-09", -8812, counterparty = "REWE"),
            Txn("r3", "a", "2026-11-11", -1210, counterparty = "REWE"),
        )
        val c = ContractDetector.detect(tx, today)
        val netflix = c.single { it.name.startsWith("Netflix") }
        assertEquals(Interval.MONTHLY, netflix.interval)
        assertEquals(LocalDate.of(2026, 12, 6), netflix.nextDate)
        assertEquals(Interval.YEARLY, c.single { it.name.startsWith("ADAC") }.interval)
        assertTrue(c.none { it.name == "REWE" })
    }

    @Test fun analyticsIgnoresTransfers() {
        val m = YearMonth.of(2026, 11)
        val tx = listOf(
            Txn("1", "a", "2026-11-01", 100000, category = Categories.INCOME),
            Txn("2", "a", "2026-11-02", -5000, category = Categories.TRANSFER),
            Txn("3", "a", "2026-11-03", -3000, category = "groceries"),
            Txn("4", "a", "2026-11-04", 1000, category = "groceries"),
        )
        val s = Analytics.summary(tx, m)
        assertEquals(101000L, s.incomeCents)
        assertEquals(3000L, s.expenseCents)
        assertEquals(listOf("groceries" to 2000L), Analytics.spendingByCategory(tx, m))
    }
}
