package de.zdf.app

import androidx.test.core.app.ApplicationProvider
import de.zdf.app.data.Account
import de.zdf.app.data.AppData
import de.zdf.app.data.BankConnection
import de.zdf.app.data.Repository
import de.zdf.app.data.Source
import de.zdf.app.data.Store
import de.zdf.app.data.Txn
import de.zdf.app.security.DataCipher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private object Plain : DataCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(data: ByteArray) = data
    }

    @Test fun removesAccountsOfReplacedConnections() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = Store(ctx, Plain)
        store.save(
            AppData(
                connections = listOf(BankConnection("c1", "s1", "N26", "DE"), BankConnection("c2", "s2", "N26", "DE")),
                accounts = listOf(
                    Account("a-old", "X", Source.ENABLE_BANKING, connectionId = "c1", institution = "N26"),
                    Account("a-new", "Y", Source.ENABLE_BANKING, iban = "DE1", connectionId = "c2", institution = "N26"),
                    Account("csv", "Z", Source.CSV),
                ),
                txns = listOf(Txn("t1", "a-old", "2026-11-01", -100), Txn("t2", "a-new", "2026-11-01", -200)),
            )
        )
        val d = Repository(ctx, store).data.value
        assertEquals(listOf("a-new", "csv"), d.accounts.map { it.id })
        assertEquals(listOf("t2"), d.txns.map { it.id })
        assertEquals(listOf("c2"), d.connections.map { it.id })
    }
}
