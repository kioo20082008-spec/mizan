package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

// ============ ألوان الخطة المعدّلة ============
private val Paper   = Color(0xFFE5D9BD)
private val Ink     = Color(0xFF182A20)
private val Bronze  = Color(0xFF8C6239)
private val Crimson = Color(0xFF7A2818)
private val Olive   = Color(0xFF4F6B43)
private val Slate   = Color(0xFF5F5A4C)

// ============ حساب تباين WCAG ============
private fun luminance(c: Color): Float {
    fun f(x: Float) = if (x <= 0.03928f) x / 12.92f
                      else ((x + 0.055) / 1.055).toDouble().pow(2.4).toFloat()
    return 0.2126f * f(c.red) + 0.7152f * f(c.green) + 0.0722f * f(c.blue)
}
private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = luminance(a); val l2 = luminance(b)
    val hi = maxOf(l1, l2); val lo = minOf(l1, l2)
    return (hi + 0.05f) / (lo + 0.05f)
}
private fun fmt2(r: Float) = String.format(java.util.Locale.US, "%.2f:1", r)

// ============ الشاشة الرئيسية للاختبار ============
@Composable
fun PreviewTestScreen() {
    Surface(Modifier.fillMaxSize(), color = Paper) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item { Header("اختبار 1 — الأرقام الجدولية (tnum)") }
            item { Test1_TabularFigures() }

            item { Header("اختبار 2 — تباين البرونز على الورق") }
            item { Test2_Contrast() }

            item { Header("اختبار 3 — شكل الـ Hero") }


cat > app/src/main/java/com/mizan/money/ui/PreviewTest.kt <<'KOTLIN_EOF'
package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

// ============ ألوان الخطة المعدّلة ============
private val Paper   = Color(0xFFE5D9BD)
private val Ink     = Color(0xFF182A20)
private val Bronze  = Color(0xFF8C6239)
private val Crimson = Color(0xFF7A2818)
private val Olive   = Color(0xFF4F6B43)
private val Slate   = Color(0xFF5F5A4C)

// ============ حساب تباين WCAG ============
private fun luminance(c: Color): Float {
    fun f(x: Float) = if (x <= 0.03928f) x / 12.92f
                      else ((x + 0.055) / 1.055).toDouble().pow(2.4).toFloat()
    return 0.2126f * f(c.red) + 0.7152f * f(c.green) + 0.0722f * f(c.blue)
}
private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = luminance(a); val l2 = luminance(b)
    val hi = maxOf(l1, l2); val lo = minOf(l1, l2)
    return (hi + 0.05f) / (lo + 0.05f)
}
private fun fmt2(r: Float) = String.format(java.util.Locale.US, "%.2f:1", r)

// ============ الشاشة الرئيسية للاختبار ============
@Composable
fun PreviewTestScreen() {
    Surface(Modifier.fillMaxSize(), color = Paper) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item { Header("اختبار 1 — الأرقام الجدولية (tnum)") }
            item { Test1_TabularFigures() }

            item { Header("اختبار 2 — تباين البرونز على الورق") }
            item { Test2_Contrast() }

            item { Header("اختبار 3 — شكل الـ Hero") }
            item { Test3_HeroShapes() }

            item { Header("اختبار 4 — surfaceTint / tonalElevation") }
            item { Test4_SurfaceTint() }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun Header(text: String) {
    Column {
        Text(text, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
    }
}

// ============ اختبار 1: tabular figures ============
@Composable
private fun Test1_TabularFigures() {
    val tnumStyle = TextStyle(
        fontSize = 16.sp,
        fontFamily = FontFamily.SansSerif,
        fontFeatureSettings = "tnum",
        color = Ink
    )
    val defaultStyle = tnumStyle.copy(fontFeatureSettings = null)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // عمود بدون tnum
        Column(Modifier.weight(1f)) {
            Text("بدون tnum", color = Slate, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            listOf("1,234.50", "88.00", "12,345.75", "7.10").forEach {
                Text(it, style = defaultStyle)
            }
        }
        // عمود مع tnum
        Column(Modifier.weight(1f)) {
            Text("مع tnum", color = Slate, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            listOf("1,234.50", "88.00", "12,345.75", "7.10").forEach {
                Text(it, style = tnumStyle)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "المطلوب: النقاط العشرية تصطف عمودياً في العمود الأيمن",
        color = Slate, fontSize = 11.sp
    )
}

// ============ اختبار 2: التباين ============
@Composable
private fun Test2_Contrast() {
    val samples = listOf(
        Triple("Hero 44sp Bold", 44.sp, FontWeight.Bold),
        Triple("H1 24sp Bold", 24.sp, FontWeight.Bold),
        Triple("H2 18sp SemiBold", 18.sp, FontWeight.SemiBold),
        Triple("Body 14sp Regular", 14.sp, FontWeight.Normal),
        Triple("Label 12sp", 12.sp, FontWeight.Normal),
        Triple("Caption 10sp", 10.sp, FontWeight.Normal)
    )
    Column(Modifier.fillMaxWidth().background(Paper).padding(12.dp)) {
        samples.forEach { (label, size, weight) ->
            val ratio = contrastRatio(Bronze, Paper)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "8,120.50 ر.س",
                    color = Bronze, fontSize = size, fontWeight = weight,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$label · ${fmt2(ratio)}",
                    color = Slate, fontSize = 10.sp, textAlign = TextAlign.End
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "AA يحتاج 4.5:1 للنص < 18sp، و3:1 للنص الكبير. لاحظ أي حجم غير مقروء.",
            color = Slate, fontSize = 10.sp
        )
        Spacer(Modifier.height(12.dp))
        // مقارنة مع الحبر
        Text(
            "للمقارنة — حبر على ورق: ${fmt2(contrastRatio(Ink, Paper))}",
            color = Ink, fontSize = 12.sp
        )
    }
}

// ============ اختبار 3: شكل الـ Hero ============
@Composable
private fun Test3_HeroShapes() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // نسخة RectangleShape
        Column {
            Text("النسخة أ — RectangleShape (صارم)", color = Slate, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth()
                    .background(Ink, RoundedCornerShape(0.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("صافي الشهر", color = Paper.copy(alpha = 0.85f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "3,240.50 ر.س",
                        color = Color(0xFFD9A56B),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
                }
            }
        }
        // نسخة CutCornerShape
        Column {
            Text("النسخة ب — CutCornerShape (زاوية مقطوعة)", color = Slate, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth()
                    .background(Ink, CutCornerShape(topEnd = 20.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("صافي الشهر", color = Paper.copy(alpha = 0.85f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "3,240.50 ر.س",
                        color = Color(0xFFD9A56B),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
                }
            }
        }
    }
}

// ============ اختبار 4: surfaceTint ============
@Composable
private fun Test4_SurfaceTint() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "نفس اللون #E5D9BD بثلاث tonalElevation مختلفة، مع colorScheme.surfaceTint الافتراضي (البرونز):",
            color = Slate, fontSize = 11.sp
        )
        // Theme افتراضي — surfaceTint = primary
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Bronze,
                surface = Paper,
                background = Paper
            )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SurfaceTile("tonalElevation = 0.dp", 0.dp)
                SurfaceTile("tonalElevation = 3.dp", 3.dp)
                SurfaceTile("tonalElevation = 6.dp", 6.dp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "الآن مع تصفير surfaceTint — لا يجب أن يتغير أي لون:",
            color = Slate, fontSize = 11.sp
        )
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Bronze,
                surface = Paper,
                background = Paper,
                surfaceTint = Color.Transparent
            )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SurfaceTile("tonalElevation = 0.dp · no tint", 0.dp)
                SurfaceTile("tonalElevation = 3.dp · no tint", 3.dp)
                SurfaceTile("tonalElevation = 6.dp · no tint", 6.dp)
            }
        }
    }
}

@Composable
private fun SurfaceTile(label: String, elevation: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        color = Paper,
        tonalElevation = elevation,
        shape = RoundedCornerShape(2.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(24.dp).background(Paper).border(1.dp, Slate)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1600)
@Composable
private fun PreviewTestPreview() {
    PreviewTestScreen()
}
