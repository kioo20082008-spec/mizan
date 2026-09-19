package com.mizan.money.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mizan.money.MoneyApp
import com.mizan.money.R
import com.mizan.money.data.BackupData
import com.mizan.money.data.BackupManager
import com.mizan.money.widget.WidgetUpdater
import com.mizan.money.ui.theme.LanguageMode
import com.mizan.money.ui.theme.LanguagePreference
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

    var themeMode by remember { mutableStateOf(ThemePreference.load(ctx)) }
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    var languageMode by remember { mutableStateOf(LanguagePreference.load(ctx)) }
    val layoutDir = when (languageMode) {
        LanguageMode.ENGLISH -> LayoutDirection.Ltr
        LanguageMode.ARABIC -> LayoutDirection.Rtl
        LanguageMode.SYSTEM -> if (isRtlSystem(ctx)) LayoutDirection.Rtl else LayoutDirection.Ltr
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
            // MainViewModel.scanInbox marks the scan done only once it actually
            // succeeds, so a failed first scan is retried on the next launch.
            vm.scanInbox()
        }
    }

    ProvideMizanTheme(isDark = isDark) {
        MaterialTheme(
            colorScheme = if (isDark) darkColorScheme(
                background = Paper, surface = White, surfaceVariant = PaperOuter,
                surfaceTint = Color.Transparent, primary = Indigo, onPrimary = Color.White,
                onBackground = Ink, onSurface = Ink, onSurfaceVariant = InkSoft,
                error = Danger, onError = Color.White, outline = Line,
            ) else lightColorScheme(
                background = Paper, surface = White, surfaceVariant = PaperOuter,
                surfaceTint = Color.Transparent, primary = Indigo, onPrimary = Color.White,
                onBackground = Ink, onSurface = Ink, onSurfaceVariant = InkSoft,
                error = Danger, onError = Color.White, outline = Line,
            )
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                Surface(Modifier.fillMaxSize(), color = PaperOuter) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Surface(Modifier.fillMaxWidth().fillMaxHeight(), color = Paper) {
                            val stage = when {
                                !hasSms -> 0
                                !scanned -> 1
                                else -> 2
                            }
                            AnimatedContent(
                                targetState = stage,
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = {
                                    (fadeIn(tween(500, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.96f,
                                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                                        ))
                                        .togetherWith(
                                            fadeOut(tween(260, easing = FastOutSlowInEasing)) +
                                                scaleOut(targetScale = 1.04f, animationSpec = tween(260))
                                        )
                                },
                                label = "stage"
                            ) { s ->
                                when (s) {
                                    0 -> PermissionScreen(
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
                                    )
                                    1 -> ScanningScreen()
                                    else -> RootScaffold(
                                        vm = vm,
                                        themeMode = themeMode,
                                        onThemeModeChange = { newMode ->
                                            themeMode = newMode
                                            ThemePreference.save(ctx, newMode)
                                        },
                                        languageMode = languageMode,
                                        onLanguageModeChange = { newMode ->
                                            LanguagePreference.save(ctx, newMode)
                                            languageMode = newMode
                                            (ctx as? Activity)?.recreate()
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
}

private fun checkSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

private fun checkReceiveSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

private fun isRtlSystem(ctx: Context): Boolean =
    ctx.resources.configuration.layoutDirection == android.util.LayoutDirection.RTL

// ============ SCAFFOLD ============
@Composable
private fun RootScaffold(
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val isScanning by vm.isScanning.collectAsState()
    val ctx = LocalContext.current
    var hasReceiveSms by remember { mutableStateOf(checkReceiveSms(ctx)) }
    // RECEIVE_SMS can be granted/revoked from system settings while the app is
    // backgrounded, so re-check on every resume (the READ_SMS flag in AppRoot
    // already does this; the warning banner used to stay stale).
    val rootLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(rootLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasReceiveSms = checkReceiveSms(ctx)
        }
        rootLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { rootLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(onSettingsClick = { showSettings = true })
            AnimatedVisibility(
                visible = !hasReceiveSms,
                enter = fadeIn(tween(300)) + expandVertically(tween(300, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(240, easing = FastOutSlowInEasing))
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Amber.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = Amber)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.receive_sms_warning),
                        style = Eyebrow.copy(fontSize = 10.sp, color = Amber)
                    )
                }
            }
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(220))
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Lime, trackColor = Color.Transparent
                )
            }
            Box(Modifier.weight(1f)) {
                val layoutDir = LocalLayoutDirection.current
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val forward = targetState > initialState
                        val base = if (layoutDir == LayoutDirection.Rtl) -1 else 1
                        val dir = if (forward) base else -base
                        (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { full -> dir * full / 10 } +
                            fadeIn(tween(340, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { full -> -dir * full / 10 } +
                                    fadeOut(tween(220, easing = FastOutSlowInEasing))
                            )
                    },
                    label = "tab"
                ) { t ->
                    when (t) {
                        0 -> DashboardScreen(vm, monthOffset, onOffsetChange = { monthOffset = it }, onNavigateToTransactions = { cat -> categoryFilter = cat; tab = 1 })
                        1 -> TransactionsScreen(vm, initialQuery = categoryFilter)
                        2 -> PlanningScreen(vm, monthOffset, onOpenCategory = { cat -> categoryFilter = cat; tab = 1 })
                        else -> InsightsScreen(vm, monthOffset)
                    }
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
            languageMode = languageMode,
            onLanguageModeChange = onLanguageModeChange,
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
            Text(stringResource(R.string.header_greeting), style = Eyebrow)
            Text(stringResource(R.string.app_name), style = H1)
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(White)
                .border(1.dp, Line, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, stringResource(R.string.header_settings), Modifier.size(20.dp), tint = InkSoft)
        }
    }
}

// ============ BOTTOM NAV ============
@Composable
private fun BottomNav(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple(stringResource(R.string.nav_home),         Icons.Filled.Home,                     0),
        Triple(stringResource(R.string.nav_transactions), Icons.AutoMirrored.Filled.ReceiptLong,  1),
        Triple(stringResource(R.string.nav_planning),     Icons.Filled.AccountBalanceWallet,     2),
        Triple(stringResource(R.string.nav_advisor),      Icons.Filled.PieChart,                 3)
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
            val indicatorX by animateDpAsState(
                itemWidth * selected,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "nav"
            )
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
                    val tint by animateColorAsState(if (isSel) Ink900 else OnInkSoft, tween(240), label = "tint")
                    Row(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable { onSelect(idx) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, label, Modifier.size(20.dp), tint = tint)
                        AnimatedVisibility(
                            visible = isSel,
                            enter = fadeIn(tween(220)) + expandHorizontally(tween(260, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180, easing = FastOutSlowInEasing))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(6.dp))
                                Text(label, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
