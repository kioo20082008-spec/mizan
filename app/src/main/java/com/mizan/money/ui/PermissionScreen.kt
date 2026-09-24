package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R

@Composable
fun PermissionScreen(showSettingsLink: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
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
        Text(stringResource(R.string.app_name), style = H1.copy(fontSize = 34.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.permission_tagline), style = BodyMuted)

        Spacer(Modifier.height(44.dp))

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(RadiusLg))
                .background(White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TrustPoint(
                Icons.Default.MarkEmailRead, Indigo,
                stringResource(R.string.permission_read_title),
                stringResource(R.string.permission_read_desc)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            TrustPoint(
                Icons.Default.Insights, Amber,
                stringResource(R.string.permission_analyze_title),
                stringResource(R.string.permission_analyze_desc)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            TrustPoint(
                Icons.Default.Lock, Purple,
                stringResource(R.string.permission_privacy_title),
                stringResource(R.string.permission_privacy_desc)
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(58.dp)
                .shadow(16.dp, RoundedCornerShape(RadiusMd), ambientColor = Ink900.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(RadiusMd),
            colors = ButtonDefaults.buttonColors(containerColor = Ink900, contentColor = Lime)
        ) {
            Text(stringResource(R.string.permission_start), style = Body.copy(color = Lime, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = Lime)
        }

        if (showSettingsLink) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.permission_settings_link),
                style = Eyebrow.copy(color = Indigo, fontSize = 12.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings).padding(8.dp)
            )
        }
    }
}

@Composable
fun ScanningScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(RadiusXl))
                .background(Ink900)
                .border(1.dp, Lime.copy(alpha = 0.25f), RoundedCornerShape(RadiusXl))
                .shadow(20.dp, RoundedCornerShape(RadiusXl), ambientColor = Ink900.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Lime, strokeWidth = 3.dp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.scanning_title),
            style = H1.copy(fontSize = 20.sp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.scanning_desc),
            style = BodyMuted,
            textAlign = TextAlign.Center
        )
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
