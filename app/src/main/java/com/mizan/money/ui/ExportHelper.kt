package com.mizan.money.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Dumps the raw SMS text + what the parser extracted from it, side by side,
// so a scan can be shared for debugging misclassified/misparsed transactions
// without anyone having to manually retype bank messages by hand.
fun exportRawSmsForDebugging(context: Context, transactions: List<TransactionEntity>) {
    val sb = StringBuilder()
    sb.appendLine("== ميزان: تصدير رسائل SMS للتشخيص ==")
    sb.appendLine("تاريخ التصدير: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
    sb.appendLine("عدد العمليات: ${transactions.size}")
    sb.appendLine()

    transactions.sortedByDescending { it.timestamp }.forEachIndexed { i, tx ->
        sb.appendLine("--- عملية #${i + 1} ---")
        sb.appendLine("البنك: ${tx.bankName ?: "-"}")
        sb.appendLine("التاريخ: ${Dates.dayLabel(tx.timestamp)}")
        sb.appendLine("المبلغ المستخرج: ${tx.amount} ${tx.currency}")
        sb.appendLine("النوع المستخرج: ${if (tx.type == TxType.EXPENSE) "مصروف" else "دخل"}")
        sb.appendLine("التصنيف المستخرج: ${tx.category}")
        sb.appendLine("التاجر المستخرج: ${tx.merchant ?: "-"}")
        sb.appendLine("تحويل ذاتي: ${if (tx.isSelfTransfer) "نعم" else "لا"}")
        sb.appendLine("نص الرسالة الأصلي:")
        sb.appendLine(tx.rawSms)
        sb.appendLine()
    }

    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "mizan-sms-export.txt")
    file.writeText(sb.toString())

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة ملف التشخيص"))
}
