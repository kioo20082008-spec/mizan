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

internal fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.RIGHT): Paint =
    Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = size
        this.color = color
        textAlign = align
    }

internal class PdfBuilder {
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
