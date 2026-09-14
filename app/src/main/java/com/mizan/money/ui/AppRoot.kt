package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mizan.money.MoneyApp

// ============ ENTRY ============
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as MoneyApp
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(app, app.repository))
    var hasSms by remember { mutableStateOf(checkSms(ctx)) }
    var scanned by remember { mutableStateOf(false) }
    val isScanning by vm.isScanning.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasSms = checkSms(ctx) }

    LaunchedEffect(hasSms) {
        if (hasSms && !scanned) { scanned = true; vm.scanInbox() }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Paper,
            surface = White,
            surfaceTint = Color.Transparent,
            primary = Indigo,
            onPrimary = White,
            onBackground = Ink,
            onSurface = Ink,
            error = Danger,
            onError = White,
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = PaperOuter) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Surface(
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        color = Paper
                    ) {
                        if (!hasSms) PermissionScreen {
                            launcher.launch(arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            ))
                        } else if (!scanned || isScanning) {
                            ScanningScreen()
                        } else {
                            RootScaffold(vm)
                        }
                    }
                }
            }
        }
    }
}

private fun checkSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

// ============ SCAFFOLD ============
@Composable
private fun RootScaffold(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    // Shared across tabs so paging the month on the dashboard also updates
    // what Budget/Advisor show, instead of them being stuck on the current month.
    var monthOffset by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(onSettingsClick = { showSettings = true })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DashboardScreen(vm, monthOffset, onOffsetChange = { monthOffset = it })
                    1 -> TransactionsScreen(vm)
                    2 -> BudgetScreen(vm, monthOffset)
                    else -> AdvisorScreen(vm, monthOffset)
                }
            }
        }
        BottomNav(tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
    }
    if (showSettings) {
        SettingsDialog(
            vm = vm,
            onDismiss = { showSettings = false },
            onRescan = { vm.scanInbox(); showSettings = false }
        )
    }
}

@Composable
private fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Default.Savings, Lime, Ink900, size = 46.dp, radius = RadiusSm)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("أهلاً بك 👋", style = Eyebrow)
            Text("ميزان", style = H1)
        }
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(RadiusSm)).background(White)
                .border(1.dp, Line, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, null, Modifier.size(20.dp), tint = InkSoft)
        }
    }
}

@Composable
private fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit, onRescan: () -> Unit) {
    val ctx = LocalContext.current
    val txs by vm.transactions.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text("الإعدادات", style = H2) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(IndigoSoft)
                        .clickable(onClick = onRescan)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.Sync, Indigo, White, size = 40.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("إعادة مسح الرسائل", style = Body.copy(fontWeight = FontWeight.Bold))
                        Text("يبحث مجدداً عن عمليات في آخر 120 يوم", style = Eyebrow.copy(fontSize = 11.sp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(PaperOuter)
                        .clickable(enabled = txs.isNotEmpty()) {
                            exportRawSmsForDebugging(ctx, txs)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.Share, InkSoft, White, size = 40.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("تصدير الرسائل للتشخيص", style = Body.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (txs.isEmpty()) "لا توجد عمليات بعد" else "شارك ملف نصي بكل العمليات ورسائلها الأصلية",
                            style = Eyebrow.copy(fontSize = 11.sp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(18.dp))
                Text("ميزان", style = Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text("جميع بياناتك تبقى محلية على جهازك فقط، ولا تُرسل لأي خادم خارجي إلا إذا اخترت تصديرها ومشاركتها بنفسك.", style = Eyebrow)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", style = Body.copy(color = InkSoft)) }
        }
    )
}

// ============ BOTTOM NAV — glass pill with a sliding lime indicator ============
@Composable
private fun BottomNav(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("الرئيسية",  Icons.Filled.Home,        0),
        Triple("العمليات",  Icons.Filled.ReceiptLong, 1),
        Triple("الميزانية", Icons.Filled.AccountBalanceWallet, 2),
        Triple("المستشار",  Icons.Filled.PieChart,    3)
    )
    Box(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(20.dp, RoundedCornerShape(Pill), ambientColor = Ink900.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(Pill))
                .background(Ink900)
                .padding(6.dp)
        ) {
            val itemWidth = maxWidth / items.size
            val indicatorX by animateDpAsState(itemWidth * selected, tween(320), label = "nav")
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Pill))
                    .background(Lime)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.SpaceBetween) {
                items.forEach { (label, icon, idx) ->
                    val isSel = selected == idx
                    val tint by animateColorAsState(if (isSel) Ink900 else OnInkSoft, tween(220), label = "tint")
                    Row(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable { onSelect(idx) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, label, Modifier.size(20.dp), tint = tint)
                        if (isSel) {
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
