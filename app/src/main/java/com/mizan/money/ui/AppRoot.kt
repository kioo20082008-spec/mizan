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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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
                                            WidgetUpdater.refresh(ctx)
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
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val categories by vm.categories.collectAsState()
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
    // ---- One UI large title ------------------------------------------------
    // The title starts big in the upper part of the screen (easy to read, keeps
    // content within thumb reach) and collapses into a toolbar as the content
    // scrolls — driven by nested scroll from whichever screen is showing.
    val density = LocalDensity.current
    val expandedH = 136.dp
    val collapsedH = 60.dp
    val rangePx = with(density) { (expandedH - collapsedH).toPx() }
    var headerOffset by remember { mutableFloatStateOf(0f) } // 0 = expanded, -rangePx = collapsed
    LaunchedEffect(tab) { headerOffset = 0f }
    val headerScroll = remember(rangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val next = (headerOffset + available.y).coerceIn(-rangePx, 0f)
                val used = next - headerOffset
                headerOffset = next
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val next = (headerOffset + available.y).coerceIn(-rangePx, 0f)
                val used = next - headerOffset
                headerOffset = next
                return Offset(0f, used)
            }
        }
    }
    val expandFraction = if (rangePx > 0f) 1f + headerOffset / rangePx else 1f
    val title = when (tab) {
        0 -> stringResource(R.string.app_name)
        1 -> stringResource(R.string.tx_title)
        2 -> stringResource(R.string.planning_title)
        else -> stringResource(R.string.insights_title)
    }

    Column(Modifier.fillMaxSize().nestedScroll(headerScroll)) {
        Column(Modifier.fillMaxWidth().height(collapsedH + with(density) { (rangePx + headerOffset).toDp() })) {
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 24.dp, end = 24.dp, bottom = 2.dp)
                        .graphicsLayer { alpha = expandFraction }
                ) {
                    if (tab == 0) {
                        Text(stringResource(R.string.header_greeting), style = BodyMuted)
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(title, style = H1.copy(fontSize = 30.sp), maxLines = 1)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(collapsedH).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = H2.copy(fontSize = 19.sp),
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        .graphicsLayer { alpha = 1f - expandFraction }
                )
                IconButton(onClick = { showSettings = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.header_settings), Modifier.size(24.dp), tint = Ink)
                }
            }
        }
        AnimatedVisibility(
            visible = !hasReceiveSms,
            enter = fadeIn(tween(300)) + expandVertically(tween(300, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(240, easing = FastOutSlowInEasing))
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(RadiusMd))
                    .background(White)
                    .clickable { showSettings = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = Amber)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.receive_sms_warning),
                    style = Body.copy(fontSize = 13.sp, color = Ink)
                )
            }
        }
        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(220))
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(3.dp).clip(RoundedCornerShape(Pill)),
                color = Indigo, trackColor = PaperOuter
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
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { full -> dir * full / 12 } +
                        fadeIn(tween(300, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { full -> -dir * full / 12 } +
                                fadeOut(tween(200, easing = FastOutSlowInEasing))
                        )
                },
                label = "tab"
            ) { t ->
                when (t) {
                    0 -> DashboardScreen(vm, monthOffset, onOffsetChange = { monthOffset = it }, onNavigateToTransactions = { cat -> categoryFilter = cat; tab = 1 })
                    1 -> TransactionsScreen(vm, initialCategory = categoryFilter)
                    2 -> PlanningScreen(vm, monthOffset, onOpenCategory = { cat -> categoryFilter = cat; tab = 1 })
                    else -> InsightsScreen(vm, monthOffset)
                }
            }
            // Adding a transaction is the most common manual action, so it is
            // one tap away on the two screens where money is viewed.
            AddFab(
                visible = tab <= 1,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                onClick = { showAdd = true }
            )
        }
        // Picking a tab from the bar always shows that tab unfiltered; only the
        // category shortcuts on Home/Planning pre-filter the transactions list.
        BottomNav(tab) { categoryFilter = null; tab = it }
    }
    if (showAdd) {
        AddDialog(
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { a, m, c, t -> vm.addManual(a, m, c, t); showAdd = false }
        )
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

// Kept as its own composable: called inside a Box that itself sits in a Column,
// AnimatedVisibility would otherwise resolve to the ColumnScope overload and
// fail to compile.
@Composable
private fun AddFab(visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(200)) + scaleIn(tween(220)),
        exit = fadeOut(tween(150)) + scaleOut(tween(150))
    ) {
        Box(
            Modifier.size(56.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Ink900)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.tx_add), Modifier.size(28.dp), tint = Lime)
        }
    }
}

// ============ BOTTOM NAV ============
// One UI style: flat bar on the card colour, icon + label always visible, the
// selected tab marked by a soft pill behind its icon.
@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple(stringResource(R.string.nav_home),         Icons.Filled.Home,                     0),
        Triple(stringResource(R.string.nav_transactions), Icons.AutoMirrored.Filled.ReceiptLong,  1),
        Triple(stringResource(R.string.nav_planning),     Icons.Filled.AccountBalanceWallet,     2),
        Triple(stringResource(R.string.nav_advisor),      Icons.Filled.PieChart,                 3)
    )
    Row(
        Modifier.fillMaxWidth().background(White).height(72.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, icon, idx) ->
            val isSel = selected == idx
            val tint by animateColorAsState(if (isSel) Indigo else InkSoft, tween(200), label = "navTint")
            val pill by animateColorAsState(if (isSel) IndigoSoft else Color.Transparent, tween(200), label = "navPill")
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(RadiusMd))
                    .clickable { onSelect(idx) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(Pill)).background(pill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = tint)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    fontSize = 12.sp,
                    color = tint,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
