package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.mizan.money.ui.theme.ProvideMizanTheme
import com.mizan.money.ui.theme.ThemeMode
import com.mizan.money.ui.theme.ThemePreference
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
    var scanned by remember { mutableStateOf(vm.hasCompletedInitialScan()) }
    var permissionAttempted by remember { mutableStateOf(false) }

    // Theme preference is persisted in the same "mizan_prefs" file the rest of
    // the app already uses. SYSTEM delegates to the OS; LIGHT/DARK force the
    // app regardless of what the OS thinks.
    var themeMode by remember { mutableStateOf(ThemePreference.load(ctx)) }
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasSms = checkSms(ctx); permissionAttempted = true }

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

    ProvideMizanTheme(isDark = isDark) {
        MaterialTheme(
            colorScheme = if (isDark) darkColorScheme(
                background = Paper,
                surface = White,
                surfaceVariant = PaperOuter,
                surfaceTint = Color.Transparent,
                primary = Indigo,
                onPrimary = Color.White,
                onBackground = Ink,
                onSurface = Ink,
                onSurfaceVariant = InkSoft,
                error = Danger,
                onError = Color.White,
                outline = Line,
            ) else lightColorScheme(
                background = Paper,
                surface = White,
                surfaceVariant = PaperOuter,
                surfaceTint = Color.Transparent,
                primary = Indigo,
                onPrimary = Color.White,
                onBackground = Ink,
                onSurface = Ink,
                onSurfaceVariant = InkSoft,
                error = Danger,
                onError = Color.White,
                outline = Line,
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
                                ScanningScreen()
                            } else {
                                RootScaffold(
                                    vm = vm,
                                    themeMode = themeMode,
                                    onThemeModeChange = { newMode ->
                                        themeMode = newMode
                                        ThemePreference.save(ctx, newMode)
                                    }
                                )
                            }
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

private fun checkReceiveSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

// ============ SCAFFOLD ============
@Composable
private fun RootScaffold(
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val isScanning by vm.isScanning.collectAsState()
    val ctx = LocalContext.current
    var hasReceiveSms by remember { mutableStateOf(checkReceiveSms(ctx)) }
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
                    0 -> DashboardScreen(vm, monthOffset, onOffsetChange = { monthOffset = it }, onNavigateToTransactions = { cat -> categoryFilter = cat; tab = 1 })
                    1 -> TransactionsScreen(vm, initialQuery = categoryFilter)
                    2 -> PlanningScreen(vm, monthOffset)
                    else -> InsightsScreen(vm, monthOffset)
                }
            }
        }
        BottomNav(tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
    }
    if (showSettings) {
        SettingsDialog(
            vm = vm,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
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
private fun SettingsDialog(
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    onRescan: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val txs by vm.transactions.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val startDay by vm.monthStartDay.collectAsState()
    val manualSalary by vm.manualSalary.collectAsState()
    val ownerName by vm.ownerName.collectAsState()
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setNotificationsEnabled(granted) }
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
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(18.dp))

                // ============ THEME ============
                Text("المظهر", style = Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text("اختر بين الفاتح أو الداكن، أو اتباع إعدادات النظام.", style = Eyebrow)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(PaperOuter)
                        .padding(4.dp)
                ) {
                    val options = listOf(
                        ThemeMode.SYSTEM to "النظام",
                        ThemeMode.LIGHT to "فاتح",
                        ThemeMode.DARK to "داكن",
                    )
                    options.forEach { (mode, label) ->
                        val sel = themeMode == mode
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(RadiusSm))
                                .background(if (sel) White else Color.Transparent)
                                .clickable { onThemeModeChange(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = Body.copy(
                                    fontSize = 13.sp,
                                    color = if (sel) Indigo else InkSoft,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الإشعارات", style = Body.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        Text("تنبيه عند تجاوز الميزانية أو اقتراب موعد فاتورة", style = Eyebrow)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { turningOn ->
                            if (turningOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotificationsEnabled(turningOn)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                    )
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

// ============ BOTTOM NAV ============
@Composable
private fun BottomNav(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("الرئيسية",  Icons.Filled.Home,        0),
        Triple("العمليات",  Icons.Filled.ReceiptLong, 1),
        Triple("التخطيط",   Icons.Filled.AccountBalanceWallet, 2),
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
