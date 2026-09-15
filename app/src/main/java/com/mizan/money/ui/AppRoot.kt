package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mizan.money.MoneyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============ ENTRY ============
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as MoneyApp
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(app, app.repository))
    var hasSms by remember { mutableStateOf(checkSms(ctx)) }
    // Seeded from a persisted flag (not just false), so a returning user doesn't
    // see the full-screen "analyzing your messages for the first time" loader —
    // and pay the cost of a full 120-day re-scan — on every single app launch.
    var scanned by remember { mutableStateOf(vm.hasCompletedInitialScan()) }
    var permissionAttempted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasSms = checkSms(ctx); permissionAttempted = true }

    // The permission dialog's own callback only fires for a request made from
    // inside this app. A user who denies here, opens system Settings, grants it
    // there, then returns via the back/recents gesture never triggers that
    // callback — so recheck whenever the app comes back to the foreground too.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasSms = checkSms(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasSms) {
        if (hasSms && !scanned) {
            scanned = true
            vm.markInitialScanDone()
            vm.scanInbox()
        }
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
                        if (!hasSms) PermissionScreen(
                            showSettingsLink = permissionAttempted,
                            onGrant = {
                                launcher.launch(arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS
                                ))
                            },
                            onOpenSettings = {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", ctx.packageName, null))
                                )
                            }
                        ) else if (!scanned) {
                            // Only the first-ever scan gets the full-screen loader.
                            // A later rescan (from Settings) must not unmount the
                            // whole app shell — RootScaffold shows its own inline
                            // indicator for that via vm.isScanning instead, so tab/
                            // month navigation state survives a routine rescan.
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
// READ_SMS alone is enough for the app to function (initial scan + manual
// rescan), but without RECEIVE_SMS new transactions only show up after the
// user rescans by hand — worth a light heads-up rather than blocking on it
// like the main permission gate does.
private fun checkReceiveSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

// Full-screen destinations reached from more than one tab (a transaction's own
// detail dialog, a dashboard category chip, Settings) — kept out of the tab
// switch below since they don't belong to any single tab, and shown instead
// of the whole tab/bottom-nav shell rather than as a dialog, since they need
// their own scroll content.
sealed class OverlayScreen {
    data class Merchant(val name: String) : OverlayScreen()
    data class Category(val name: String) : OverlayScreen()
    data object Reports : OverlayScreen()
}

// ============ SCAFFOLD ============
@Composable
private fun RootScaffold(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Shared across tabs so paging the month on the dashboard also updates
    // what Budget/Advisor show, instead of them being stuck on the current month.
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Set when a Dashboard category chip is tapped, so Transactions opens
    // pre-filtered to that category instead of just switching tabs blindly.
    // rememberSaveable so a configuration change (e.g. rotation) doesn't drop
    // the pending filter while the app is mid-navigation.
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<OverlayScreen?>(null) }
    val isScanning by vm.isScanning.collectAsState()
    val ctx = LocalContext.current
    var hasReceiveSms by remember { mutableStateOf(checkReceiveSms(ctx)) }

    val currentOverlay = overlay
    if (currentOverlay != null) {
        when (currentOverlay) {
            is OverlayScreen.Merchant -> StatsDetailScreen(vm, DetailFilter.ByMerchant(currentOverlay.name)) { overlay = null }
            is OverlayScreen.Category -> StatsDetailScreen(vm, DetailFilter.ByCategory(currentOverlay.name)) { overlay = null }
            OverlayScreen.Reports -> ReportsScreen(vm, monthOffset, onOffsetChange = { monthOffset = it }) { overlay = null }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(onSettingsClick = { showSettings = true })
            if (!hasReceiveSms) {
                Row(
                    Modifier.fillMaxWidth().background(Amber.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = Amber)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "إذن استقبال الرسائل غير ممنوح — أعد المسح يدوياً من الإعدادات بعد كل عملية جديدة",
                        style = Eyebrow.copy(fontSize = 10.sp, color = Amber)
                    )
                }
            }
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Lime, trackColor = Color.Transparent
                )
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DashboardScreen(
                        vm, monthOffset,
                        onOffsetChange = { monthOffset = it },
                        onNavigateToTransactions = { cat -> categoryFilter = cat; tab = 1 },
                        onOpenCategoryDetail = { cat -> overlay = OverlayScreen.Category(cat) }
                    )
                    1 -> TransactionsScreen(
                        vm, initialQuery = categoryFilter,
                        onOpenMerchantDetail = { merchant -> overlay = OverlayScreen.Merchant(merchant) }
                    )
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
            onRescan = { vm.scanInbox(); showSettings = false },
            onOpenReports = { showSettings = false; overlay = OverlayScreen.Reports }
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
            Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(White)
                .border(1.dp, Line, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, "الإعدادات", Modifier.size(20.dp), tint = InkSoft)
        }
    }
}

@Composable
private fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit, onRescan: () -> Unit, onOpenReports: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val txs by vm.transactions.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val ownerName by vm.ownerName.collectAsState()
    var salaryInput by remember(manualSalary) {
        mutableStateOf(
            manualSalary.takeIf { it > 0 }
                ?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        )
    }
    var nameInput by remember(ownerName) { mutableStateOf(ownerName) }
    var showExportConfirm by remember { mutableStateOf(false) }

    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text("مشاركة بياناتك؟", style = H2) },
            text = {
                Text(
                    "بيشارك ملف نصي فيه كل عملياتك: المبالغ، أسماء البنوك، آخر 4 أرقام من البطاقة، ونص رسائل SMS الأصلية كاملة. اختر بنفسك وين ترسله من قائمة المشاركة التالية.",
                    style = BodyMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    scope.launch(Dispatchers.IO) {
                        val file = writeSmsExportFile(ctx, txs)
                        withContext(Dispatchers.Main) { shareExportFile(ctx, file) }
                    }
                }) { Text("مشاركة", style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) { Text("إلغاء", style = Body.copy(color = InkSoft)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text("الإعدادات", style = H2) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(IndigoSoft)
                        .clickable(enabled = !isScanning, onClick = onRescan)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.Sync, Indigo, White, size = 40.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (isScanning) "جارٍ المسح..." else "إعادة مسح الرسائل", style = Body.copy(fontWeight = FontWeight.Bold))
                        Text("يبحث مجدداً عن عمليات في آخر 120 يوم", style = Eyebrow.copy(fontSize = 11.sp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(PaperOuter)
                        .clickable(enabled = txs.isNotEmpty()) {
                            showExportConfirm = true
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
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(IndigoSoft)
                        .clickable { onOpenReports() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.PictureAsPdf, Indigo, White, size = 40.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("تقرير PDF شهري", style = Body.copy(fontWeight = FontWeight.Bold))
                        Text("شارك ملخص الشهر كملف PDF", style = Eyebrow.copy(fontSize = 11.sp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(18.dp))
                Text("راتبك الشهري", style = Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(
                    "يُستخدم في نصائح المستشار المالي (قاعدة 50/30/20 وغيرها) بدل الاعتماد فقط على اكتشافه تلقائياً من الإيداعات المتكررة.",
                    style = Eyebrow
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = { salaryInput = sanitizeAmountInput(it) },
                        placeholder = { Text("مثال: 8000", style = Eyebrow) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(Indigo)
                            .clickable { vm.setManualSalary(salaryInput.toDoubleOrNull() ?: 0.0) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, "حفظ", tint = White, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(18.dp))
                Text("اسمك", style = Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(
                    "يُستخدم فقط للتعرف على تحويلاتك بين حساباتك أنت (مثلاً من الإنماء إلى برق) عن طريق اسم المستفيد بالرسالة — اكتبه بنفس الشكل اللي يظهر فيه، عربي أو إنجليزي.",
                    style = Eyebrow
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = { Text("مثال: Waleed Hamadallah", style = Eyebrow) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(RadiusSm),
                        textStyle = Body
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(Indigo)
                            .clickable { vm.setOwnerName(nameInput) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, "حفظ", tint = White, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(18.dp))
                Text("بداية الدورة الشهرية", style = Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(
                    "اليوم الذي يبدأ منه حساب «الشهر» في كل الصفحات — غيّره ليطابق يوم نزول راتبك بدل أول الشهر تلقائياً.",
                    style = Eyebrow
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(PaperOuter)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.setMonthStartDay(startDay - 1) }, enabled = startDay > 1) {
                        Icon(Icons.Default.Remove, "إنقاص", tint = if (startDay > 1) Indigo else InkFaint)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("يوم $startDay من كل شهر", style = Body.copy(fontWeight = FontWeight.Bold))
                    }
                    IconButton(onClick = { vm.setMonthStartDay(startDay + 1) }, enabled = startDay < 28) {
                        Icon(Icons.Default.Add, "زيادة", tint = if (startDay < 28) Indigo else InkFaint)
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
