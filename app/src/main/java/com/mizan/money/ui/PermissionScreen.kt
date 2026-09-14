package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))

        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(RadiusXl))
                .background(Ink900)
                .border(1.dp, Lime.copy(alpha = 0.25f), RoundedCornerShape(RadiusXl))
                .shadow(20.dp, RoundedCornerShape(RadiusXl), ambientColor = Ink900.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Savings, null, Modifier.size(46.dp), tint = Lime)
        }

        Spacer(Modifier.height(22.dp))
        Text("ميزان", style = H1.copy(fontSize = 34.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(6.dp))
        Text("إدارة مالية بخصوصية تامة", style = BodyMuted)

        Spacer(Modifier.height(44.dp))

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(RadiusLg))
                .background(White)
                .border(1.dp, Line, RoundedCornerShape(RadiusLg))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TrustPoint(Icons.Default.MarkEmailRead, Indigo, "اقرأ رسائل بنكك", "لتصنيف مصاريفك تلقائياً")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            TrustPoint(Icons.Default.Insights, Amber, "حلّل عاداتك", "اعرض أنماط صرفك بوضوح")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            TrustPoint(Icons.Default.Lock, Purple, "خصوصيتك أولاً", "البيانات على جهازك فقط")
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(58.dp)
                .shadow(16.dp, RoundedCornerShape(RadiusMd), ambientColor = Ink900.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(RadiusMd),
            colors = ButtonDefaults.buttonColors(containerColor = Ink900, contentColor = Lime)
        ) {
            Text("ابدأ الآن", style = Body.copy(color = Lime, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp).graphicsLayer(rotationZ = 180f), tint = Lime)
        }
    }
}

@Composable
private fun TrustPoint(icon: ImageVector, accent: Color, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, accent, accent.copy(alpha = 0.12f), size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = H2.copy(fontSize = 15.sp))
            Spacer(Modifier.height(2.dp))
            Text(desc, style = Eyebrow.copy(fontSize = 12.sp))
        }
    }
}
