package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionScreen(
    showSettingsLink: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinueWithoutSms: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(RadiusXl)).background(Ink900),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Savings, null, Modifier.size(44.dp), tint = Lime)
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), style = H1.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.permission_tagline), style = Body.copy(color = InkSoft), textAlign = TextAlign.Center)

            Spacer(Modifier.height(32.dp))

            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(White)
            ) {
                TrustPoint(
                    Icons.Default.MarkEmailRead, Indigo,
                    stringResource(R.string.permission_read_title),
                    stringResource(R.string.permission_read_desc),
                    showDivider = true
                )
                TrustPoint(
                    Icons.Default.Insights, Amber,
                    stringResource(R.string.permission_analyze_title),
                    stringResource(R.string.permission_analyze_desc),
                    showDivider = true
                )
                TrustPoint(
                    Icons.Default.Lock, Purple,
                    stringResource(R.string.permission_privacy_title),
                    stringResource(R.string.permission_privacy_desc),
                    showDivider = false
                )
            }

            Spacer(Modifier.height(12.dp))

            // Banks whose SMS the parser understands (see SmsParser.allowedSenders).
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(White)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    stringResource(R.string.onb_supported_banks),
                    style = Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BankChip(stringResource(R.string.onb_bank_alinma))
                    BankChip(stringResource(R.string.onb_bank_barq))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(Pill),
            colors = ButtonDefaults.buttonColors(containerColor = Ink900, contentColor = Lime)
        ) {
            Text(stringResource(R.string.permission_start), style = Body.copy(color = Lime, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = Lime)
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onContinueWithoutSms,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(Pill)
        ) {
            Text(
                stringResource(R.string.onb_continue_manual),
                style = Body.copy(color = Indigo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            )
        }

        if (showSettingsLink) {
            Text(
                stringResource(R.string.permission_settings_link),
                style = Body.copy(color = InkSoft, fontSize = 13.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusSm))
                    .clickable(onClick = onOpenSettings)
                    .padding(8.dp)
            )
        }
    }
}

// foundCount: transactions found so far (0 hides the line). Optional so the
// existing ScanningScreen() call keeps compiling.
@Composable
fun ScanningScreen(foundCount: Int = 0) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(RadiusXl)).background(Ink900),
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
            style = Body.copy(color = InkSoft, fontSize = 14.sp),
            textAlign = TextAlign.Center
        )
        if (foundCount > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.onb_scan_found_fmt, insNum(foundCount)),
                style = Body.copy(color = Indigo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BankChip(name: String) {
    Text(
        name,
        style = Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink),
        modifier = Modifier.clip(RoundedCornerShape(Pill))
            .background(PaperOuter)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun TrustPoint(icon: ImageVector, accent: Color, title: String, desc: String, showDivider: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, accent, accent.copy(alpha = 0.12f), size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(desc, style = Body.copy(fontSize = 13.sp, color = InkSoft))
            }
        }
        if (showDivider) {
            Box(Modifier.padding(start = 72.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Line))
        }
    }
}
