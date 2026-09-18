package com.mizan.money.data

import org.json.JSONArray
import org.json.JSONObject

// A complete, self-contained snapshot of everything the user has. Restoring
// replaces the whole database, so this is the only durable way to get data back
// after an uninstall (allowBackup is off by design, since the app promises the
// data never leaves the device).
data class BackupData(
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val recurringItems: List<RecurringItemEntity> = emptyList(),
)

// Uses org.json (bundled with Android) instead of pulling in a serialization
// library, keeping the APK and build unchanged. Ids are preserved so a restored
// goal/debt keeps its identity; for transactions they're only an optimization.
object BackupManager {
    const val VERSION = 1
    private const val MAGIC = "mizan"

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", MAGIC)
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("transactions", JSONArray().apply {
            data.transactions.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("amount", t.amount)
                    put("currency", t.currency)
                    put("merchant", t.merchant ?: JSONObject.NULL)
                    put("category", t.category)
                    put("type", t.type.name)
                    put("bankName", t.bankName ?: JSONObject.NULL)
                    put("cardLast4", t.cardLast4 ?: JSONObject.NULL)
                    put("rawSms", t.rawSms)
                    put("smsHash", t.smsHash)
                    put("timestamp", t.timestamp)
                    put("isManual", t.isManual)
                    put("isSelfTransfer", t.isSelfTransfer)
                    put("isEdited", t.isEdited)
                    put("excludeFromDailyAvg", t.excludeFromDailyAvg)
                    put("isReimbursement", t.isReimbursement)
                })
            }
        })
        root.put("budgets", JSONArray().apply {
            data.budgets.forEach { b ->
                put(JSONObject().apply {
                    put("monthKey", b.monthKey)
                    put("category", b.category)
                    put("limitAmount", b.limitAmount)
                    put("rolloverEnabled", b.rolloverEnabled)
                })
            }
        })
        root.put("goals", JSONArray().apply {
            data.goals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("targetAmount", g.targetAmount)
                    put("currentAmount", g.currentAmount)
                    put("targetDate", g.targetDate ?: JSONObject.NULL)
                    put("createdAt", g.createdAt)
                    put("isArchived", g.isArchived)
                })
            }
        })
        root.put("debts", JSONArray().apply {
            data.debts.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("type", d.type.name)
                    put("totalAmount", d.totalAmount)
                    put("remainingAmount", d.remainingAmount)
                    put("installmentAmount", d.installmentAmount)
                    put("nextDueDate", d.nextDueDate ?: JSONObject.NULL)
                    put("lender", d.lender ?: JSONObject.NULL)
                    put("createdAt", d.createdAt)
                    put("isArchived", d.isArchived)
                })
            }
        })
        root.put("recurringItems", JSONArray().apply {
            data.recurringItems.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("merchant", r.merchant)
                    put("expectedAmount", r.expectedAmount)
                    put("expectedDayOfMonth", r.expectedDayOfMonth)
                    put("category", r.category)
                    put("reminderEnabled", r.reminderEnabled)
                    put("lastNotifiedMonthKey", r.lastNotifiedMonthKey ?: JSONObject.NULL)
                    put("createdAt", r.createdAt)
                })
            }
        })
        return root.toString()
    }

    // Throws IllegalArgumentException if the text isn't a Mizan backup — the
    // caller surfaces that as "couldn't read the file" instead of wiping data.
    fun fromJson(json: String): BackupData {
        val root = try { JSONObject(json) } catch (e: Exception) {
            throw IllegalArgumentException("Not valid JSON", e)
        }
        if (root.optString("app") != MAGIC) throw IllegalArgumentException("Not a Mizan backup")

        val transactions = root.optJSONArray("transactions").mapObjects { o ->
            TransactionEntity(
                id = o.optLong("id", 0L),
                amount = o.optDouble("amount", 0.0),
                currency = o.optString("currency", "SAR"),
                merchant = o.stringOrNull("merchant"),
                category = o.optString("category", "أخرى"),
                type = o.enumOr("type", TxType.EXPENSE) { TxType.valueOf(it) },
                bankName = o.stringOrNull("bankName"),
                cardLast4 = o.stringOrNull("cardLast4"),
                rawSms = o.optString("rawSms", ""),
                smsHash = o.optString("smsHash", "restored-${o.optLong("id", 0L)}"),
                timestamp = o.optLong("timestamp", 0L),
                isManual = o.optBoolean("isManual", false),
                isSelfTransfer = o.optBoolean("isSelfTransfer", false),
                isEdited = o.optBoolean("isEdited", false),
                excludeFromDailyAvg = o.optBoolean("excludeFromDailyAvg", false),
                isReimbursement = o.optBoolean("isReimbursement", false),
            )
        }
        val budgets = root.optJSONArray("budgets").mapObjects { o ->
            BudgetEntity(
                monthKey = o.optString("monthKey"),
                category = o.optString("category"),
                limitAmount = o.optDouble("limitAmount", 0.0),
                rolloverEnabled = o.optBoolean("rolloverEnabled", false),
            )
        }
        val goals = root.optJSONArray("goals").mapObjects { o ->
            GoalEntity(
                id = o.optLong("id", 0L),
                name = o.optString("name"),
                targetAmount = o.optDouble("targetAmount", 0.0),
                currentAmount = o.optDouble("currentAmount", 0.0),
                targetDate = o.longOrNull("targetDate"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                isArchived = o.optBoolean("isArchived", false),
            )
        }
        val debts = root.optJSONArray("debts").mapObjects { o ->
            DebtEntity(
                id = o.optLong("id", 0L),
                name = o.optString("name"),
                type = o.enumOr("type", DebtType.OTHER) { DebtType.valueOf(it) },
                totalAmount = o.optDouble("totalAmount", 0.0),
                remainingAmount = o.optDouble("remainingAmount", 0.0),
                installmentAmount = o.optDouble("installmentAmount", 0.0),
                nextDueDate = o.longOrNull("nextDueDate"),
                lender = o.stringOrNull("lender"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                isArchived = o.optBoolean("isArchived", false),
            )
        }
        val recurring = root.optJSONArray("recurringItems").mapObjects { o ->
            RecurringItemEntity(
                id = o.optLong("id", 0L),
                merchant = o.optString("merchant"),
                expectedAmount = o.optDouble("expectedAmount", 0.0),
                expectedDayOfMonth = o.optInt("expectedDayOfMonth", 1),
                category = o.optString("category", "أخرى"),
                reminderEnabled = o.optBoolean("reminderEnabled", true),
                lastNotifiedMonthKey = o.stringOrNull("lastNotifiedMonthKey"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        return BackupData(transactions, budgets, goals, debts, recurring)
    }
}

private inline fun <T> JSONArray?.mapObjects(map: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { map(getJSONObject(it)) }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.longOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)

private inline fun <E : Enum<E>> JSONObject.enumOr(key: String, default: E, parse: (String) -> E): E =
    try { parse(optString(key)) } catch (e: Exception) { default }
