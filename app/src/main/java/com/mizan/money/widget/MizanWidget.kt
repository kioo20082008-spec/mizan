package com.mizan.money.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mizan.money.MainActivity
import com.mizan.money.advisor.FinancialAdvisor
import com.mizan.money.ui.Amber
import com.mizan.money.ui.Danger
import com.mizan.money.ui.Indigo
import com.mizan.money.ui.Ink900
import com.mizan.money.ui.Lime
import com.mizan.money.ui.OnInkSoft
import com.mizan.money.ui.PaperOuter
import com.mizan.money.ui.Success

class MizanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { WidgetContent(data) }
    }
}

@Composable
private fun WidgetContent(data: WidgetData) {
    val accent = when {
        data.income <= 0.0 -> Indigo
        data.pct >= 1.0f -> Danger
        data.pct >= 0.8f -> Amber
        else -> Success
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Ink900))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "ميزان",
                style = TextStyle(
                    color = ColorProvider(Lime),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = data.monthLabel,
                style = TextStyle(
                    color = ColorProvider(OnInkSoft),
                    fontSize = 11.sp
                )
            )
        }

        Spacer(GlanceModifier.height(10.dp))

        Text(
            text = "استهلاك دخل الشهر",
            style = TextStyle(
                color = ColorProvider(OnInkSoft),
                fontSize = 10.sp
            )
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = if (data.income > 0) "${(data.pct * 100).toInt()}٪" else "—",
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(GlanceModifier.height(8.dp))

        LinearProgressIndicator(
            progress = data.pct.coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(accent),
            backgroundColor = ColorProvider(PaperOuter)
        )

        Spacer(GlanceModifier.height(8.dp))

        Text(
            text = if (data.hasData)
                "صرفت ${FinancialAdvisor.fmt(data.spent)} ر.س"
            else "لا توجد بيانات",
            style = TextStyle(
                color = ColorProvider(OnInkSoft),
                fontSize = 11.sp
            )
        )
    }
}
