package com.mizan.money.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// reconcile() is the most destructive logic in the app — it decides what to
// insert, update, and delete on every rescan. Exercised here against in-memory
// fakes that mirror the DAO SQL, so it's covered without needing Robolectric or
// an on-device Room instance.
class TransactionRepositoryTest {

    private lateinit var txDao: FakeTransactionDao
    private lateinit var repo: TransactionRepository

    @Before
    fun setup() {
        txDao = FakeTransactionDao()
        repo = TransactionRepository(
            txDao, FakeBudgetDao(), FakeGoalDao(), FakeDebtDao(), FakeRecurringItemDao()
        )
    }

    private fun tx(hash: String, amount: Double = 10.0, isManual: Boolean = false, isEdited: Boolean = false) =
        TransactionEntity(amount = amount, smsHash = hash, timestamp = 1_000L, isManual = isManual, isEdited = isEdited)

    @Test
    fun `reconcile inserts a transaction that is not stored yet`() = runBlocking {
        repo.reconcile(listOf(tx("h1", 50.0)), setOf("h1"))
        assertEquals(1, txDao.rows.size)
        assertEquals(50.0, txDao.rows.single().amount, 0.001)
    }

    @Test
    fun `reconcile re-derives an SMS row that was not edited by the user`() = runBlocking {
        txDao.insert(tx("h1", 50.0))
        repo.reconcile(listOf(tx("h1", 75.0)), setOf("h1"))
        assertEquals(75.0, txDao.rows.single().amount, 0.001)
    }

    @Test
    fun `reconcile never overwrites a row the user edited`() = runBlocking {
        txDao.insert(tx("h1", 50.0, isEdited = true))
        repo.reconcile(listOf(tx("h1", 999.0)), setOf("h1"))
        assertEquals(50.0, txDao.rows.single().amount, 0.001)
    }

    @Test
    fun `reconcile deletes an SMS row that no longer parses`() = runBlocking {
        txDao.insert(tx("stale", 10.0))
        repo.reconcile(emptyList(), setOf("stale"))
        assertTrue(txDao.rows.isEmpty())
    }

    @Test
    fun `reconcile never deletes a manual transaction`() = runBlocking {
        txDao.insert(tx("manual-1", 10.0, isManual = true))
        repo.reconcile(emptyList(), setOf("manual-1"))
        assertEquals(1, txDao.rows.size)
    }

    @Test
    fun `reconcile leaves rows outside the scanned window alone`() = runBlocking {
        txDao.insert(tx("keep", 10.0))
        repo.reconcile(emptyList(), emptySet())
        assertEquals(1, txDao.rows.size)
    }

    @Test
    fun `applyCategoryToMerchant updates matching rows and skips the edited one`() = runBlocking {
        txDao.insert(TransactionEntity(amount = 1.0, merchant = "بنده", category = "أخرى", smsHash = "a", timestamp = 1))
        txDao.insert(TransactionEntity(amount = 2.0, merchant = "Panda", category = "أخرى", smsHash = "b", timestamp = 2))
        txDao.insert(TransactionEntity(amount = 3.0, merchant = "بنده", category = "أخرى", smsHash = "c", timestamp = 3))
        val edited = txDao.rows.first { it.smsHash == "a" }

        repo.applyCategoryToMerchant("بنده", "بقالة", edited.id)
        repo.applyCategoryToMerchant("panda", "تسوق", edited.id)

        assertEquals("أخرى", txDao.rows.first { it.smsHash == "a" }.category)
        assertEquals("تسوق", txDao.rows.first { it.smsHash == "b" }.category)
        assertEquals("بقالة", txDao.rows.first { it.smsHash == "c" }.category)
    }
}

private class FakeTransactionDao : TransactionDao {
    val rows = mutableListOf<TransactionEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<TransactionEntity>> = MutableStateFlow(rows.toList())
    override suspend fun getAllOnce(): List<TransactionEntity> = rows.toList()
    override suspend fun insert(tx: TransactionEntity): Long {
        val id = if (tx.id == 0L) nextId++ else tx.id
        rows += tx.copy(id = id)
        return id
    }
    override suspend fun insertAll(list: List<TransactionEntity>): List<Long> = list.map { insert(it) }
    override suspend fun update(tx: TransactionEntity) {
        val i = rows.indexOfFirst { it.id == tx.id }
        if (i >= 0) rows[i] = tx
    }
    override suspend fun delete(tx: TransactionEntity) { rows.removeAll { it.id == tx.id } }
    override suspend fun count(): Int = rows.size
    override suspend fun findByHash(hash: String): TransactionEntity? = rows.firstOrNull { it.smsHash == hash }
    override suspend fun deleteStaleByHash(hash: String) {
        rows.removeAll { it.smsHash == hash && !it.isManual && !it.isEdited }
    }
    override suspend fun updateCategoryByMerchant(merchant: String, category: String, excludeId: Long) {
        for (i in rows.indices) {
            val r = rows[i]
            if (r.id != excludeId && r.merchant?.equals(merchant, ignoreCase = true) == true) {
                rows[i] = r.copy(category = category)
            }
        }
    }
}

private class FakeBudgetDao : BudgetDao {
    val rows = mutableListOf<BudgetEntity>()
    override fun observeAll(): Flow<List<BudgetEntity>> = MutableStateFlow(rows.toList())
    override suspend fun getAllOnce(): List<BudgetEntity> = rows.toList()
    override suspend fun find(monthKey: String, category: String): BudgetEntity? =
        rows.firstOrNull { it.monthKey == monthKey && it.category == category }
    override suspend fun upsert(b: BudgetEntity) {
        val i = rows.indexOfFirst { it.monthKey == b.monthKey && it.category == b.category }
        if (i >= 0) rows[i] = b else rows += b
    }
    override suspend fun delete(monthKey: String, category: String) {
        rows.removeAll { it.monthKey == monthKey && it.category == category }
    }
}

private class FakeGoalDao : GoalDao {
    val rows = mutableListOf<GoalEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<GoalEntity>> = MutableStateFlow(rows.toList())
    override suspend fun insert(g: GoalEntity): Long {
        val id = if (g.id == 0L) nextId++ else g.id
        rows += g.copy(id = id)
        return id
    }
    override suspend fun update(g: GoalEntity) {
        val i = rows.indexOfFirst { it.id == g.id }
        if (i >= 0) rows[i] = g
    }
    override suspend fun delete(g: GoalEntity) { rows.removeAll { it.id == g.id } }
}

private class FakeDebtDao : DebtDao {
    val rows = mutableListOf<DebtEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<DebtEntity>> = MutableStateFlow(rows.toList())
    override suspend fun insert(d: DebtEntity): Long {
        val id = if (d.id == 0L) nextId++ else d.id
        rows += d.copy(id = id)
        return id
    }
    override suspend fun update(d: DebtEntity) {
        val i = rows.indexOfFirst { it.id == d.id }
        if (i >= 0) rows[i] = d
    }
    override suspend fun delete(d: DebtEntity) { rows.removeAll { it.id == d.id } }
}

private class FakeRecurringItemDao : RecurringItemDao {
    val rows = mutableListOf<RecurringItemEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<RecurringItemEntity>> = MutableStateFlow(rows.toList())
    override suspend fun getEnabledOnce(): List<RecurringItemEntity> = rows.filter { it.reminderEnabled }
    override suspend fun insert(r: RecurringItemEntity): Long {
        val id = if (r.id == 0L) nextId++ else r.id
        rows.removeAll { it.id == id }
        rows += r.copy(id = id)
        return id
    }
    override suspend fun update(r: RecurringItemEntity) {
        val i = rows.indexOfFirst { it.id == r.id }
        if (i >= 0) rows[i] = r
    }
    override suspend fun delete(r: RecurringItemEntity) { rows.removeAll { it.id == r.id } }
}
