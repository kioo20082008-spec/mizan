package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Paper   = Color(0xFFE5D9BD)
private val Ink     = Color(0xFF182A20)
private val Bronze  = Color(0xFF8C6239)
private val Slate   = Color(0xFF5F5A4C)

@Composable
fun PreviewTestScreen() {
    Surface(Modifier.fillMaxSize(), color = Paper) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Test1_Tnum() }
            item { Test2_Contrast() }
            item { Test3_HeroShapes() }
            item { Test4_SurfaceTint() }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(en: String, ar: String) {
    Column {
        Text(en, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(ar, color = Slate, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
    }
}

@Composable
private fun Test1_Tnum() {
    SectionTitle("Test 1 - Tabular Figures", "test tnum")
    Spacer(Modifier.height(8.dp))

    val tnum = TextStyle(
        fontSize = 16.sp,
        fontFeatureSettings = "tnum",
        color = Ink
    )
    val plain = tnum.copy(fontFeatureSettings = null)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Text("PLAIN", color = Bronze, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("1,234.50", style = plain)
            Text("88.00", style = plain)
            Text("12,345.75", style = plain)
            Text("7.10", style = plain)
        }
        Column(Modifier.weight(1f)) {
            Text("TNUM", color = Bronze, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("1,234.50", style = tnum)
            Text("88.00", style = tnum)
            Text("12,345.75", style = tnum)
            Text("7.10", style = tnum)
        }
    }
}

@Composable
private fun Test2_Contrast() {
    SectionTitle("Test 2 - Bronze Contrast", "test contrast")
    Spacer(Modifier.height(8.dp))

    Column {
        Text("10sp", color = Bronze, fontSize = 10.sp)
        Text("12sp", color = Bronze, fontSize = 12.sp)
        Text("14sp", color = Bronze, fontSize = 14.sp)
        Text("16sp", color = Bronze, fontSize = 16.sp)
        Text("18sp Bold", color = Bronze, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("24sp Bold", color = Bronze, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("40sp Bold", color = Bronze, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Compare - Ink on Paper", color = Ink, fontSize = 14.sp)
    }
}

@Composable
private fun Test3_HeroShapes() {
    SectionTitle("Test 3 - Hero Shape", "test shape")
    Spacer(Modifier.height(8.dp))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("A. RectangleShape (strict)", color = Slate, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth()
                    .background(Ink, CutCornerShape(0.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("Net", color = Paper.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("3,240.50", color = Color(0xFFD9A56B),
                        fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
                }
            }
        }
        Column {
            Text("B. CutCornerShape (topEnd)", color = Slate, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth()
                    .background(Ink, CutCornerShape(topEnd = 20.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("Net", color = Paper.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("3,240.50", color = Color(0xFFD9A56B),
                        fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
                }
            }
        }
    }
}

@Composable
private fun Test4_SurfaceTint() {
    SectionTitle("Test 4 - surfaceTint", "test tint")
    Spacer(Modifier.height(8.dp))

    Text("With default surfaceTint (should show warm tint):",
        color = Slate, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Bronze,
            surface = Paper,
            background = Paper
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TintTile("elev 0dp", 0.dp)
            TintTile("elev 3dp", 3.dp)
            TintTile("elev 6dp", 6.dp)
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("With surfaceTint = Transparent (no tint):",
        color = Slate, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Bronze,
            surface = Paper,
            background = Paper,
            surfaceTint = Color.Transparent
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TintTile("elev 0dp no-tint", 0.dp)
            TintTile("elev 3dp no-tint", 3.dp)
            TintTile("elev 6dp no-tint", 6.dp)
        }
    }
}

@Composable
private fun TintTile(label: String, elevation: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        color = Paper,
        tonalElevation = elevation,
        shape = CutCornerShape(2.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Text(label, color = Ink, fontSize = 12.sp,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart))
        }
    }
}

@Composable
private fun PreviewTestPreview() {
    PreviewTestScreen()
}
