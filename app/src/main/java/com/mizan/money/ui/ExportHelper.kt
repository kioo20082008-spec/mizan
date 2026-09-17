package com.mizan.money.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
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

// ============ SMS DIAGNOSTIC EXPORT (unchanged) ============
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

fun shareExportFile(context: Context, file: File, mimeType: String = "text/plain", chooserTitle: String = "مشاركة ملف التشخيص") {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

// ============ CSV (unchanged) ============
fun writeCsvReport(context: Context, transactions: List<TransactionEntity>, periodLabel: String): File {
    val sb = StringBuilder()
    sb.append('\uFEFF')
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

// ============ PDF: PROFESSIONAL REPORT ============
//
// Structure (multi-page, paginated by content):
//   Page 1: Header + big net + 3 stat boxes + budget bar + month-vs-month
//   Page 2+: Key insights (advice), categories with budget status,
//            top transactions, then the full transaction list.
//
// Arabic text in PdfDocument renders through the platform's own text
// pipeline (Paint.drawText), which handles shaping and RTL glyph ordering.
// We use Paint.Align.RIGHT for Arabic fields so the draw origin is the
// right edge; Paint.Align.LEFT for currency amounts so digits stay put.

data class PdfLabels(
    val reportTitle: String,
    val netLabel: String,
    val incomeLabel: String,
    val spendingLabel: String,
    val savingsRateLabel: String,
    val budgetUsageLabel: String,
    val budgetSpentOf: String,      // "من" between spent/total
    val compareLabel: String,
    val thisMonthLabel: String,
    val lastMonthLabel: String,
    val higherFormat: String,       // %1$d٪
    val lowerFormat: String,        // %1$d٪
    val insightsTitle: String,
    val categoriesTitle: String,
    val noLimitLabel: String,       // "بدون حد"
    val overLimitFormat: String,    // "تجاوز بـ %1$s"
    val remainingFormat: String,    // "متبقي %1$s"
    val topTransactionsTitle: String,
    val allTransactionsTitle: String,
    val pageLabelFormat: String,    // "صفحة %1$d"
    val generatedBy: String,
    val currencyLabel: String,      // "ر.س" / "SAR"
)

data class PdfAdvice(
    val title: String,
    val body: String,
    val level: String, // "danger" | "warn" | "good" | "info"
)

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val PAD = 40f

private const val C_BG = 0xFFFAF9F6.toInt()
private const val C_INK = 0xFF15141A.toInt()
private const val C_SOFT = 0xFF6F6D76.toInt()
private const val C_FAINT = 0xFFA4A2AA.toInt()
private const val C_LINE = 0xFFE9E6DE.toInt()
private const val C_INDIGO = 0xFF4F46E5.toInt()
private const val C_GREEN = 0xFF22C55E.toInt()
private const val C_RED = 0xFFF43F5E.toInt()
private const val C_AMBER = 0xFFF59E0B.toInt()
private const val C_INK_BG = 0xFF121017.toInt()
private const val C_LIME = 0xFFD7F26B.toInt()
private const val C_INK_2 = 0xFF1E1B26.toInt()

private fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.RIGHT): Paint =
    Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = size
        this.color = color
        textAlign = align
    }

private class PdfBuilder {
    val doc = PdfDocument()
    var pageNum = 0
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = 0f

    fun newPage(bg: Int = C_BG): Canvas {
        if (pageNum > 0) doc.finishPage(page)
        pageNum++
        val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
        page = doc.startPage(info)
        canvas = page.canvas
        canvas.drawColor(bg)
        y = PAD
        return canvas
    }

    fun ensureSpace(needed: Float, bg: Int = C_BG) {
        if (y + needed > PAGE_H - PAD - 30) newPage(bg)
    }

    fun finish(): PdfDocument {
        if (pageNum > 0) doc.finishPage(page)
        return doc
    }
}

fun writePdfReport(
    ctx: Context,
    monthLabel: String,
    summary: MonthSummary,
    prevSummary: MonthSummary,
    budget: Double,
    transactions: List<TransactionEntity>,
    advice: List<PdfAdvice>,
    categoryNames: Map<String, String>,
    labels: PdfLabels,
): File {
    val b = PdfBuilder()
    val right = PAGE_W - PAD
    val left = PAD
    val center = PAGE_W / 2f

    fun localName(raw: String): String = categoryNames[raw] ?: raw
    fun fmt(v: Double) = FinancialAdvisor.fmt(v)

    // ============================================================
    // PAGE 1 — Cover + summary
    // ============================================================
    b.newPage()
    var c = b.canvas

    // Dark header band
    c.drawRect(0f, 0f, PAGE_W.toFloat(), 200f, Paint().apply { color = C_INK_BG })
    c.drawRect(PAD, 165f, PAD + 40f, 172f, Paint().apply { color = C_LIME })

    c.drawText(labels.reportTitle, right, 80f, paint(28f, C_LIME, bold = true))
    c.drawText(monthLabel, right, 120f, paint(16f, 0xFFACA9B8.toInt()))

    // Net hero — right under the header
    b.y = 240f
    val netColor = if (summary.net >= 0) C_GREEN else C_RED
    c.drawText(labels.netLabel, right, b.y, paint(12f, C_FAINT))
    b.y += 44f
    c.drawText(fmt(kotlin.math.abs(summary.net)) + " " + labels.currencyLabel, right, b.y, paint(34f, netColor, bold = true))

    // 3 metric boxes
    b.y += 40f
    val boxTop = b.y
    val boxH = 70f
    val boxGap = 8f
    val boxW = (right - left - boxGap * 2) / 3f
    fun metricBox(i: Int, label: String, value: String, color: Int) {
        val x = left + i * (boxW + boxGap)
        c.drawRect(x, boxTop, x + boxW, boxTop + boxH, Paint().apply {
            this.color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        })
        c.drawRect(x, boxTop, x + boxW, boxTop + boxH, Paint().apply {
            this.color = C_LINE
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })
        c.drawText(label, x + boxW - 10f, boxTop + 22f, paint(9f, C_FAINT))
        c.drawText(value, x + boxW - 10f, boxTop + 52f, paint(16f, color, bold = true))
    }
    metricBox(0, labels.incomeLabel, fmt(summary.income), C_GREEN)
    metricBox(1, labels.spendingLabel, fmt(summary.spent), C_INK)
    val savingsPct = if (summary.income > 0) ((summary.income - summary.spent) / summary.income * 100).toInt() else 0
    metricBox(2, labels.savingsRateLabel, "$savingsPct٪", if (savingsPct >= 20) C_GREEN else if (savingsPct >= 0) C_AMBER else C_RED)

    // Budget bar
    b.y = boxTop + boxH + 40f
    if (budget > 0) {
        val pct = (summary.spent / budget).coerceIn(0.0, 1.0).toFloat()
        val over = summary.spent > budget
        val barColor = if (over) C_RED else if (pct > 0.8f) C_AMBER else C_GREEN

        c.drawText(labels.budgetUsageLabel, right, b.y, paint(11f, C_SOFT))
        c.drawText("${(summary.spent / budget * 100).toInt()}٪", left, b.y, paint(12f, barColor, bold = true, align = Paint.Align.LEFT))
        b.y += 10f

        // Track
        val trackH = 14f
        c.drawRect(left, b.y, right, b.y + trackH, Paint().apply { color = C_LINE })
        // Fill (from right, since RTL)
        val fillW = (right - left) * pct
        c.drawRect(right - fillW, b.y, right, b.y + trackH, Paint().apply { color = barColor })
        b.y += trackH + 20f

        c.drawText(
            "${fmt(summary.spent)} ${labels.budgetSpentOf} ${fmt(budget)} ${labels.currencyLabel}",
            right, b.y, paint(10f, C_SOFT)
        )
        b.y += 34f
    }

    // Month vs month
    if (prevSummary.spent > 0) {
        c.drawText(labels.compareLabel, right, b.y, paint(11f, C_SOFT))
        b.y += 26f
        c.drawText("${labels.thisMonthLabel}: ${fmt(summary.spent)} ${labels.currencyLabel}", right, b.y, paint(11f, C_INK))
        b.y += 18f
        c.drawText("${labels.lastMonthLabel}: ${fmt(prevSummary.spent)} ${labels.currencyLabel}", right, b.y, paint(11f, C_SOFT))
        b.y += 22f
        val diff = ((summary.spent - prevSummary.spent) / prevSummary.spent * 100).toInt()
        val diffStr = if (diff >= 0) labels.higherFormat.format(diff) else labels.lowerFormat.format(-diff)
        val diffColor = if (diff >= 0) C_RED else C_GREEN
        c.drawText(diffStr, right, b.y, paint(12f, diffColor, bold = true))
        b.y += 24f
    }

    // ============================================================
    // PAGE 2+ — Insights, Categories, Top transactions, All list
    // ============================================================

    // --- Key insights ---
    if (advice.isNotEmpty()) {
        b.ensureSpace(80f)
        c = b.canvas
        c.drawText(labels.insightsTitle, right, b.y, paint(14f, C_INK, bold = true))
        b.y += 6f
        c.drawRect(right - 40f, b.y, right, b.y + 3f, Paint().apply { color = C_INDIGO })
        b.y += 22f

        advice.take(5).forEach { a ->
            b.ensureSpace(60f)
            c = b.canvas
            val color = when (a.level) {
                "danger" -> C_RED
                "warn" -> C_AMBER
                "good" -> C_GREEN
                else -> C_INDIGO
            }
            c.drawRect(right - 3f, b.y - 12f, right, b.y + 40f, Paint().apply { this.color = color })
            c.drawText(a.title, right - 12f, b.y, paint(11f, color, bold = true))
            b.y += 18f
            c.drawText(a.body, right - 12f, b.y, paint(10f, C_SOFT))
            b.y += 30f
        }
        b.y += 10f
    }

    // --- Categories with budget status ---
    val catTotals = summary.categoryTotals
    if (catTotals.isNotEmpty()) {
        b.ensureSpace(80f)
        c = b.canvas
        c.drawText(labels.categoriesTitle, right, b.y, paint(14f, C_INK, bold = true))
        b.y += 6f
        c.drawRect(right - 40f, b.y, right, b.y + 3f, Paint().apply { color = C_INDIGO })
        b.y += 26f

        val maxAmt = catTotals.maxOf { it.amount }.coerceAtLeast(1.0)
        catTotals.take(15).forEach { cat ->
            b.ensureSpace(60f)
            c = b.canvas
            val name = localName(cat.category)
            c.drawText(name, right, b.y, paint(11f, C_INK, bold = true))
            c.drawText("${fmt(cat.amount)} ${labels.currencyLabel}   ${(cat.share * 100).toInt()}٪", left, b.y, paint(10f, C_SOFT, align = Paint.Align.LEFT))
            b.y += 8f
            // Bar
            val barH = 6f
            val w = (right - left) * (cat.amount / maxAmt).toFloat()
            c.drawRect(left, b.y, right, b.y + barH, Paint().apply { color = C_LINE })
            c.drawRect(right - w, b.y, right, b.y + barH, Paint().apply { color = C_INDIGO })
            b.y += barH + 10f

            // Budget status under
            // (budget-per-category lives in the DB; we don't have it here — the
            // user can cross-reference the Budget screen. For now, print just
            // the amount share. This keeps the PDF generator independent of
            // budget data shape.)
            b.y += 8f
        }
        b.y += 10f
    }

    // --- Top transactions ---
    val expenses = transactions.filter { it.type == TxType.EXPENSE }.sortedByDescending { it.amount }
    if (expenses.isNotEmpty()) {
        b.ensureSpace(80f)
        c = b.canvas
        c.drawText(labels.topTransactionsTitle, right, b.y, paint(14f, C_INK, bold = true))
        b.y += 6f
        c.drawRect(right - 40f, b.y, right, b.y + 3f, Paint().apply { color = C_INDIGO })
        b.y += 26f

        expenses.take(10).forEachIndexed { i, tx ->
            b.ensureSpace(30f)
            c = b.canvas
            val merchant = (tx.merchant ?: "-").take(35)
            c.drawText("${i + 1}. $merchant", right, b.y, paint(10f, C_INK))
            c.drawText("${fmt(tx.amount)} ${labels.currencyLabel}", left, b.y, paint(10f, C_RED, bold = true, align = Paint.Align.LEFT))
            b.y += 6f
            c.drawText(Dates.dayLabel(tx.timestamp) + "  ·  " + localName(tx.category), right, b.y, paint(8f, C_FAINT))
            b.y += 22f
        }
        b.y += 10f
    }

    // --- All transactions ---
    if (transactions.isNotEmpty()) {
        b.ensureSpace(80f)
        c = b.canvas
        c.drawText(labels.allTransactionsTitle, right, b.y, paint(14f, C_INK, bold = true))
        b.y += 6f
        c.drawRect(right - 40f, b.y, right, b.y + 3f, Paint().apply { color = C_INDIGO })
        b.y += 26f

        transactions.sortedByDescending { it.timestamp }.forEach { tx ->
            b.ensureSpace(34f)
            c = b.canvas
            val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
            val amtColor = if (tx.type == TxType.EXPENSE) C_RED else C_GREEN
            c.drawText((tx.merchant ?: "-").take(38), right, b.y, paint(9f, C_INK))
            c.drawText("$sign${fmt(tx.amount)}", left, b.y, paint(9f, amtColor, bold = true, align = Paint.Align.LEFT))
            b.y += 6f
            c.drawText(Dates.dayLabel(tx.timestamp) + "  ·  " + localName(tx.category), right, b.y, paint(8f, C_FAINT))
            b.y += 20f
        }
    }

    // Footer on last page
    c = b.canvas
    c.drawText(labels.generatedBy, center, PAGE_H - 30f, paint(8f, C_FAINT, align = Paint.Align.CENTER))

    // Write out
    val doc = b.finish()
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "mizan-report-${monthLabel.replace(" ", "-")}.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    return file
}
