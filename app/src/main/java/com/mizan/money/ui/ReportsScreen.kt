package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.advisor.FinancialAdvisor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Monthly PDF report — preview of the same month range every other screen
// shows, generated and shared on demand (nothing is pre-built or stored).
@Composable
fun ReportsScreen(vm: MainViewModel, offset: Int, onOffsetChange: (Int) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var generating by remember { mutableStateOf(false) }

    val txs by vm.transactions.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val range = remember(offset, startDay) { Dates.monthRange(offset, startDay) }
    val summary = remember(txs, offset, startDay) { FinancialAdvisor.summarize(txs, range.first, range.last) }
    val monthTxs = remember(txs, range) { txs.filter { it.timestamp in range } }
    val label = monthName(offset, startDay)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Ink)
                }
                Column(Modifier.weight(1f)) {
                    Text("تقرير PDF شهري", style = H1)
                    Text("معاينة قبل المشاركة", style = Eyebrow)
                }
            }
        }

        item { MonthPicker(offset, label, onOffsetChange) }

        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusXl))
                    .background(Ink900)
                    .padding(24.dp)
            ) {
                Text("محتوى التقرير", style = Eyebrow.copy(color = OnInkSoft))
                Spacer(Modifier.height(12.dp))
                PreviewLine("الشهر", label)
                PreviewLine("عدد العمليات", "${summary.count}")
                PreviewLine("إجمالي الصرف", "${FinancialAdvisor.fmt(summary.spent)} ر.س")
                PreviewLine("إجمالي الدخل", "${FinancialAdvisor.fmt(summary.income)} ر.س")
                PreviewLine("صافي", "${FinancialAdvisor.fmt(summary.net)} ر.س")
                PreviewLine("عدد التصنيفات", "${summary.categoryTotals.size}")
            }
        }

        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusMd))
                    .background(if (generating) InkFaint else Indigo)
                    .clickable(enabled = !generating && monthTxs.isNotEmpty()) {
                        generating = true
                        scope.launch(Dispatchers.IO) {
                            val file = generateMonthlyReportPdf(ctx, label, summary, monthTxs, summary.categoryTotals)
                            withContext(Dispatchers.Main) {
                                shareExportFile(ctx, file, mimeType = "application/pdf", chooserTitle = "مشاركة التقرير")
                                generating = false
                            }
                        }
                    }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (generating) Icons.Default.HourglassEmpty else Icons.Default.PictureAsPdf,
                        null, Modifier.size(20.dp), tint = White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (generating) "جارٍ الإنشاء..." else "إنشاء ومشاركة PDF",
                        style = Body.copy(color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    )
                }
            }
        }

        if (monthTxs.isEmpty()) {
            item { EmptyState("لا توجد عمليات في هذا الشهر") }
        }
    }
}

@Composable
private fun MonthPicker(offset: Int, label: String, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(White)
            .border(1.dp, Line, RoundedCornerShape(RadiusMd))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange(offset - 1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "الشهر السابق", tint = Ink)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(label, style = H2)
        }
        IconButton(onClick = { if (offset < 0) onChange(offset + 1) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, "الشهر التالي",
                tint = if (offset < 0) Ink else InkFaint
            )
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = Body.copy(color = OnInkSoft))
        Spacer(Modifier.weight(1f))
        Text(value, style = Body.copy(color = White, fontWeight = FontWeight.Bold))
    }
}
