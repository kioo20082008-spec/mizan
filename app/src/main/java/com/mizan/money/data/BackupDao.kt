package com.mizan.money.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

// Dedicated DAO for whole-database backup/restore. Keeping it separate means the
// existing repositories/DAOs (and their fakes in tests) stay untouched.
// `restore` is @Transaction so a failed import can't leave the database with
// some tables cleared and others not.
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM transactions") abstract suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM budgets") abstract suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM goals") abstract suspend fun goals(): List<GoalEntity>
    @Query("SELECT * FROM debts") abstract suspend fun debts(): List<DebtEntity>
    @Query("SELECT * FROM recurring_items") abstract suspend fun recurringItems(): List<RecurringItemEntity>
    @Query("SELECT * FROM goal_contributions") abstract suspend fun goalContributions(): List<GoalContributionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertTransactions(items: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertBudgets(items: List<BudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertGoals(items: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertDebts(items: List<DebtEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertRecurringItems(items: List<RecurringItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertGoalContributions(items: List<GoalContributionEntity>)

    @Query("DELETE FROM transactions") abstract suspend fun clearTransactions()
    @Query("DELETE FROM budgets") abstract suspend fun clearBudgets()
    @Query("DELETE FROM goals") abstract suspend fun clearGoals()
    @Query("DELETE FROM debts") abstract suspend fun clearDebts()
    @Query("DELETE FROM recurring_items") abstract suspend fun clearRecurringItems()
    @Query("DELETE FROM goal_contributions") abstract suspend fun clearGoalContributions()

    @Transaction
    open suspend fun restore(data: BackupData) {
        clearTransactions(); insertTransactions(data.transactions)
        clearBudgets(); insertBudgets(data.budgets)
        clearGoals(); insertGoals(data.goals)
        clearGoalContributions(); insertGoalContributions(data.goalContributions)
        clearDebts(); insertDebts(data.debts)
        clearRecurringItems(); insertRecurringItems(data.recurringItems)
    }
}
