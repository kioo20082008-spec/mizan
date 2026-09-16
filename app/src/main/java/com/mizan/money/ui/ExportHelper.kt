package com.mizan.money.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.MonthSummary
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Dumps the raw SMS text + what the parser extracted from it, side by side, so a
// scan can be shared for debugging misclassified/misparsed transactions without
// anyone having to manually retype bank messages by hand. Building the text and
// writing it to disk is IO-bound, so call this off the main thread (Dispatchers.IO)
// and only hand the resulting File to shareExportFile() back on the main thread.
fun writeSmsExportFile(context: Context, transactions: List<TransactionEntity>): File {
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
    return file
}

// mimeType defaults to the original diagnostic export's "text/plain" so every
// existing call site is unaffected; the reports below pass their own type.
fun shareExportFile(context: Context, file: File, mimeType: String = "text/plain", chooserTitle: String = "مشاركة ملف التشخيص") {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

// ============ REAL REPORT EXPORTS (Reports tab) ============
// Distinct from writeSmsExportFile above: that one dumps raw SMS text for bug
// diagnosis, this produces an actually-readable report of the user's own data
// for the user's own use (budgeting elsewhere, sharing with an accountant, etc).

fun writeCsvReport(context: Context, transactions: List<TransactionEntity>, periodLabel: String): File {
    val sb = StringBuilder()
    // A UTF-8 BOM so Excel (still the most likely opener) detects the encoding
    // and renders Arabic text correctly instead of garbling it.
    sb.append('﻿')
    sb.appendLine("التاريخ,البنك,التاجر,التصنيف,النوع,المبلغ,العملة")
    transactions.sortedByDescending { it.timestamp }.forEach { tx ->
        val fields = listOf(
            Dates.dayLabel(tx.timestamp),
            tx.bankName ?: "-",
            (tx.merchant ?: "-").replace(",", " "),
            tx.category.replace(",", " "),
            if (tx.type == TxType.EXPENSE) "مصروف" else "دخل",
            "%.2f".format(tx.amount),
            tx.currency
        )
        sb.appendLine(fields.joinToString(","))
    }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "mizan-report-$periodLabel.csv")
    file.writeText(sb.toString())
    return file
}

fun writePdfReport(
    context: Context,
    periodLabel: String,
    summary: MonthSummary,
    budget: Double
): File {
    val doc = PdfDocument()
    // A4 at 72dpi-ish (points): 595x842 is the standard PdfDocument page size.
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas

    val title = Paint().apply { textSize = 22f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
    val label = Paint().apply { textSize = 11f; color = 0xFF6F6D76.toInt(); textAlign = Paint.Align.RIGHT }
    val value = Paint().apply { textSize = 14f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
    val barLabel = Paint().apply { textSize = 10f; textAlign = Paint.Align.RIGHT }

    val rightMargin = 545f
    var y = 60f
    canvas.drawText("تقرير ميزان المالي", rightMargin, y, title)
    y += 22f
    canvas.drawText(periodLabel, rightMargin, y, label)
    y += 34f

    fun row(l: String, v: String) {
        canvas.drawText(l, rightMargin, y, label)
        canvas.drawText(v, rightMargin, y + 16f, value)
        y += 44f
    }
    row("إجمالي الدخل", "${FinancialAdvisor.fmt(summary.income)} ر.س")
    row("إجمالي الصرف", "${FinancialAdvisor.fmt(summary.spent)} ر.س")
    row("الصافي", "${FinancialAdvisor.fmt(summary.net)} ر.س")
    if (budget > 0) row("نسبة استهلاك الميزانية", "${((summary.spent / budget) * 100).toInt()}٪")

    y += 10f
    canvas.drawText("التوزيع حسب الفئة", rightMargin, y, value)
    y += 20f
    val maxAmount = summary.categoryTotals.maxOfOrNull { it.amount } ?: 1.0
    summary.categoryTotals.take(12).forEach { cat ->
        val barMaxWidth = 380f
        val barWidth = (cat.amount / maxAmount * barMaxWidth).toFloat().coerceAtLeast(2f)
        val barPaint = Paint().apply { color = 0xFF4F46E5.toInt() }
        canvas.drawRect(rightMargin - barWidth, y, rightMargin, y + 12f, barPaint)
        canvas.drawText(
            "${cat.category} — ${FinancialAdvisor.fmt(cat.amount)} ر.س (${(cat.share * 100).toInt()}٪)",
            rightMargin, y + 24f, barLabel
        )
        y += 40f
        if (y > 800f) return@forEach // stays on one page for v1 — a full pager isn't worth the complexity yet
    }

    doc.finishPage(page)
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "mizan-report-$periodLabel.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    return file
}
