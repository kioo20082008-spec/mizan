package com.mizan.money.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mizan.money.advisor.CategoryTotal
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.advisor.MonthSummary
import com.mizan.money.data.TransactionEntity
import com.mizan.money.data.TxType
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val PAD = 40f
private val PAGE_W_F = PAGE_W.toFloat()
private val PAGE_MID = PAGE_W_F / 2f
private val PAGE_RIGHT = PAGE_W_F - PAD

private const val C_INK = 0xFF15141A.toInt()
private const val C_SOFT = 0xFF6F6D76.toInt()
private const val C_FAINT = 0xFFA4A2AA.toInt()
private const val C_PAPER = 0xFFFAF9F6.toInt()
private const val C_INDIGO = 0xFF4F46E5.toInt()
private const val C_SUCCESS = 0xFF22C55E.toInt()
private const val C_DANGER = 0xFFF43F5E.toInt()
private const val C_LIME = 0xFFD7F26B.toInt()
private const val C_INK_DARK = 0xFF121017.toInt()
private const val C_WHITE = 0xFFFFFFFF.toInt()
private const val C_ONINK_SOFT = 0xFFACA9B8.toInt()

private fun mkPaint(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
    isAntiAlias = true
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    textSize = size
    this.color = color
    // Right-aligned text growing leftward from the given x matches this app's
    // RTL layout — every label/value below is anchored at its right edge.
    textAlign = Paint.Align.RIGHT
}

private fun Canvas.txt(s: String, xRight: Float, y: Float, p: Paint) = drawText(s, xRight, y, p)

private fun Canvas.hr(y: Float) {
    drawLine(PAD, y, PAGE_RIGHT, y, Paint().apply { color = 0x22000000; strokeWidth = 1f })
}

// Builds a local, shareable PDF (cover/summary, category breakdown, full
// transaction list) — nothing here touches the network, it only reads what's
// already in the database. PdfDocument does file/bitmap work, so call this off
// the main thread and hand the returned File to shareExportFile() back on it.
fun generateMonthlyReportPdf(
    ctx: Context,
    monthLabel: String,
    summary: MonthSummary,
    monthTxs: List<TransactionEntity>,
    categoryTotals: List<CategoryTotal>
): File {
    val doc = PdfDocument()
    var pageNum = 0

    // ---- Page 1: cover + summary ----
    pageNum++
    var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
    var c = page.canvas
    c.drawColor(C_PAPER)

    c.drawRect(0f, 0f, PAGE_W_F, 300f, Paint().apply { color = C_INK_DARK })
    c.drawRect(PAD, 250f, PAD + 60f, 256f, Paint().apply { color = C_LIME })

    c.txt("ميزان", PAGE_RIGHT, 110f, mkPaint(40f, C_LIME, bold = true))
    c.txt("تقرير شهري", PAGE_RIGHT, 150f, mkPaint(18f, C_ONINK_SOFT))
    c.txt(monthLabel, PAGE_RIGHT, 210f, mkPaint(24f, C_WHITE, bold = true))

    var y = 360f
    c.txt("صافي الشهر", PAGE_RIGHT, y, mkPaint(12f, C_FAINT))
    y += 42f
    val netColor = if (summary.net >= 0) C_SUCCESS else C_DANGER
    c.txt("${FinancialAdvisor.fmt(abs(summary.net))} ر.س", PAGE_RIGHT, y, mkPaint(34f, netColor, bold = true))

    y += 40f; c.hr(y); y += 40f

    c.txt("إجمالي الدخل", PAGE_RIGHT, y, mkPaint(11f, C_FAINT))
    c.txt("إجمالي الصرف", PAGE_MID, y, mkPaint(11f, C_FAINT))
    y += 26f
    c.txt("${FinancialAdvisor.fmt(summary.income)} ر.س", PAGE_RIGHT, y, mkPaint(17f, C_INK, bold = true))
    c.txt("${FinancialAdvisor.fmt(summary.spent)} ر.س", PAGE_MID, y, mkPaint(17f, C_INK, bold = true))

    y += 50f; c.hr(y); y += 40f

    c.txt("عدد العمليات هذا الشهر", PAGE_RIGHT, y, mkPaint(11f, C_FAINT))
    y += 26f
    c.txt("${summary.count}", PAGE_RIGHT, y, mkPaint(17f, C_INK, bold = true))

    c.txt("تم إنشاؤه محلياً على جهازك — ميزان", PAGE_MID, PAGE_H - 40f, mkPaint(10f, C_FAINT))
    doc.finishPage(page)

    // ---- Page 2: categories ----
    if (categoryTotals.isNotEmpty()) {
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        c = page.canvas
        c.drawColor(C_PAPER)

        c.txt("التصنيفات", PAGE_RIGHT, 70f, mkPaint(24f, C_INK, bold = true))
        c.hr(95f)

        var row = 140f
        val maxAmount = categoryTotals.maxOf { it.amount }
        val barWidth = 420f
        for (cat in categoryTotals) {
            if (row > PAGE_H - 80) break
            c.txt(cat.category, PAGE_RIGHT, row, mkPaint(14f, C_INK, bold = true))
            c.txt("${FinancialAdvisor.fmt(cat.amount)} ر.س", PAD + 220f, row, mkPaint(13f, C_SOFT))

            val fillW = barWidth * (cat.amount / maxAmount).toFloat()
            c.drawRect(PAD, row + 10f, PAD + barWidth, row + 18f, Paint().apply { color = 0x11000000 })
            c.drawRect(PAGE_RIGHT - fillW, row + 10f, PAGE_RIGHT, row + 18f, Paint().apply { color = C_INDIGO })

            row += 55f
        }
        doc.finishPage(page)
    }

    // ---- Page 3+: transactions (paginates itself if the month is long) ----
    pageNum++
    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
    c = page.canvas
    c.drawColor(C_PAPER)
    c.txt("كل العمليات", PAGE_RIGHT, 70f, mkPaint(24f, C_INK, bold = true))
    c.hr(95f)
    var ry = 140f

    val sorted = monthTxs.sortedByDescending { it.timestamp }
    if (sorted.isEmpty()) {
        c.txt("لا توجد عمليات هذا الشهر", PAGE_RIGHT, ry, mkPaint(13f, C_FAINT))
    }
    for (tx in sorted) {
        if (ry > PAGE_H - 60) {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            c = page.canvas
            c.drawColor(C_PAPER)
            c.txt("كل العمليات (تابع)", PAGE_RIGHT, 70f, mkPaint(24f, C_INK, bold = true))
            c.hr(95f)
            ry = 140f
        }
        val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
        val amtColor = if (tx.isSelfTransfer) C_FAINT else if (tx.type == TxType.EXPENSE) C_DANGER else C_SUCCESS

        c.txt(tx.merchant ?: "غير معروف", PAGE_RIGHT, ry, mkPaint(13f, C_INK, bold = true))
        c.txt("${tx.category} · ${Dates.dayLabel(tx.timestamp)}", PAGE_RIGHT, ry + 18f, mkPaint(10f, C_FAINT))
        c.txt("$sign${FinancialAdvisor.fmt(tx.amount)} ر.س", PAD + 160f, ry, mkPaint(14f, amtColor, bold = true))

        ry += 45f
    }
    doc.finishPage(page)

    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "mizan-report.pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    return file
}
