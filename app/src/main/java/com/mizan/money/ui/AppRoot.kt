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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
            if (!hasReceiveSms) {
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

@Composable
private fun SettingsDialog(
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
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
    val rates by vm.exchangeRates.collectAsState()
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
    // Re-seeds itself whenever the saved rates change (i.e. right after a save),
    // so the fields always show the persisted values.
    var rateInputs by remember(rates) {
        mutableStateOf(
            rates.filterKeys { it != "SAR" }.mapValues {
                if (it.value % 1.0 == 0.0) it.value.toInt().toString() else it.value.toString()
            }
        )
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var restoreResult by remember { mutableStateOf<Boolean?>(null) }
    val app = ctx.applicationContext as MoneyApp
    val backupShareTitle = stringResource(R.string.stg_backup_share)
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                // Read fully first; only show the destructive confirm once we
                // know the file is readable.
                val text = try {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) { null }
                withContext(Dispatchers.Main) {
                    if (text == null) restoreResult = false else pendingRestoreJson = text
                }
            }
        }
    }

    if (pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.stg_restore_confirm_title), style = H2) },
            text = { Text(stringResource(R.string.stg_restore_confirm_body), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingRestoreJson!!
                    pendingRestoreJson = null
                    scope.launch(Dispatchers.IO) {
                        val ok = try {
                            BackupManager.fromJson(json).let { app.db.backupDao().restore(it) }
                            WidgetUpdater.refresh(ctx)
                            true
                        } catch (e: Exception) { false }
                        withContext(Dispatchers.Main) { restoreResult = ok }
                    }
                }) { Text(stringResource(R.string.stg_restore_action), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) {
                    Text(stringResource(R.string.stg_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }
    restoreResult?.let { ok ->
        AlertDialog(
            onDismissRequest = { restoreResult = null },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = {
                Text(
                    if (ok) stringResource(R.string.stg_restore_ok) else stringResource(R.string.stg_restore_failed),
                    style = H2
                )
            },
            confirmButton = {
                TextButton(onClick = { restoreResult = null }) {
                    Text(stringResource(R.string.settings_close), style = Body.copy(color = InkSoft))
                }
            }
        )
    }


    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.stg_export_confirm_title), style = H2) },
            text = { Text(stringResource(R.string.stg_export_confirm_body), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    scope.launch(Dispatchers.IO) {
                        val file = writeSmsExportFile(ctx, txs)
                        withContext(Dispatchers.Main) { shareExportFile(ctx, file) }
                    }
                }) { Text(stringResource(R.string.stg_share), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) {
                    Text(stringResource(R.string.stg_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(RadiusXl),
        title = { Text(stringResource(R.string.settings_title), style = H2) },
        text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                TabSwitcher(
                    listOf(
                        stringResource(R.string.stg_tab_data),
                        stringResource(R.string.stg_tab_appearance),
                        stringResource(R.string.stg_tab_finance),
                        stringResource(R.string.stg_tab_about),
                    ),
                    tab
                ) { tab = it }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (tab) {
                        // ==================== DATA ====================
                        0 -> {
                            SettingsActionRow(
                                icon = Icons.Default.Sync,
                                iconTint = Indigo,
                                background = IndigoSoft,
                                title = if (isScanning) stringResource(R.string.stg_rescan_busy)
                                        else stringResource(R.string.stg_rescan_title),
                                subtitle = stringResource(R.string.stg_rescan_subtitle),
                                enabled = !isScanning,
                                onClick = onRescan
                            )
                            Spacer(Modifier.height(10.dp))
                            SettingsActionRow(
                                icon = Icons.Default.Share,
                                iconTint = InkSoft,
                                title = stringResource(R.string.stg_export_title),
                                subtitle = if (txs.isEmpty()) stringResource(R.string.stg_export_none)
                                           else stringResource(R.string.stg_export_subtitle),
                                enabled = txs.isNotEmpty(),
                                onClick = { showExportConfirm = true }
                            )
                            Spacer(Modifier.height(10.dp))
                            SettingsActionRow(
                                icon = Icons.Default.Backup,
                                iconTint = Indigo,
                                title = stringResource(R.string.stg_backup_title),
                                subtitle = stringResource(R.string.stg_backup_subtitle),
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val dao = app.db.backupDao()
                                        val data = BackupData(
                                            transactions = dao.transactions(),
                                            budgets = dao.budgets(),
                                            goals = dao.goals(),
                                            debts = dao.debts(),
                                            recurringItems = dao.recurringItems(),
                                        )
                                        val file = writeBackupFile(ctx, BackupManager.toJson(data))
                                        withContext(Dispatchers.Main) {
                                            shareExportFile(ctx, file, "application/json", backupShareTitle)
                                        }
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                            SettingsActionRow(
                                icon = Icons.Default.Restore,
                                iconTint = Danger,
                                title = stringResource(R.string.stg_restore_title),
                                subtitle = stringResource(R.string.stg_restore_subtitle),
                                onClick = {
                                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                }
                            )
                        }

                        // ================= APPEARANCE =================
                        1 -> {
                            SettingsFieldHeader(
                                stringResource(R.string.settings_language),
                                stringResource(R.string.settings_language_desc)
                            )
                            SettingsSegmented(
                                options = listOf(
                                    LanguageMode.SYSTEM to stringResource(R.string.settings_language_system),
                                    LanguageMode.ARABIC to stringResource(R.string.settings_language_arabic),
                                    LanguageMode.ENGLISH to stringResource(R.string.settings_language_english),
                                ),
                                selected = languageMode,
                                onSelect = onLanguageModeChange
                            )
                            SettingsDivider()
                            SettingsFieldHeader(
                                stringResource(R.string.stg_theme_label),
                                stringResource(R.string.stg_theme_desc)
                            )
                            SettingsSegmented(
                                options = listOf(
                                    ThemeMode.SYSTEM to stringResource(R.string.stg_theme_system),
                                    ThemeMode.LIGHT to stringResource(R.string.stg_theme_light),
                                    ThemeMode.DARK to stringResource(R.string.stg_theme_dark),
                                ),
                                selected = themeMode,
                                onSelect = onThemeModeChange
                            )
                        }

                        // ==================== FINANCE ====================
                        2 -> {
                            SettingsFieldHeader(
                                stringResource(R.string.stg_salary_label),
                                stringResource(R.string.stg_salary_desc)
                            )
                            SettingsInputRow(
                                value = salaryInput,
                                onValueChange = { salaryInput = sanitizeAmountInput(it) },
                                placeholder = stringResource(R.string.stg_salary_hint),
                                keyboardType = KeyboardType.Decimal,
                                onSave = { vm.setManualSalary(salaryInput.toDoubleOrNull() ?: 0.0) }
                            )
                            SettingsDivider()
                            SettingsFieldHeader(
                                stringResource(R.string.stg_name_label),
                                stringResource(R.string.stg_name_desc)
                            )
                            SettingsInputRow(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                placeholder = stringResource(R.string.stg_name_hint),
                                onSave = { vm.setOwnerName(nameInput) }
                            )
                            SettingsDivider()
                            SettingsFieldHeader(
                                stringResource(R.string.stg_month_start_label),
                                stringResource(R.string.stg_month_start_desc)
                            )
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(RadiusMd))
                                    .background(PaperOuter)
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { vm.setMonthStartDay(startDay - 1) }, enabled = startDay > 1) {
                                    Icon(Icons.Default.Remove, stringResource(R.string.stg_decrease), tint = if (startDay > 1) Indigo else InkFaint)
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.stg_month_start_day_fmt, startDay), style = Body.copy(fontWeight = FontWeight.Bold))
                                }
                                IconButton(onClick = { vm.setMonthStartDay(startDay + 1) }, enabled = startDay < 28) {
                                    Icon(Icons.Default.Add, stringResource(R.string.stg_increase), tint = if (startDay < 28) Indigo else InkFaint)
                                }
                            }
                            SettingsDivider()
                            SettingsFieldHeader(
                                stringResource(R.string.stg_rates_label),
                                stringResource(R.string.stg_rates_desc)
                            )
                            com.mizan.money.data.ExchangeRates.supported.forEach { code ->
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.stg_rate_fmt, code), style = Body)
                                    Spacer(Modifier.width(10.dp))
                                    OutlinedTextField(
                                        value = rateInputs[code] ?: "",
                                        onValueChange = { rateInputs = rateInputs + (code to sanitizeAmountInput(it)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(RadiusSm),
                                        textStyle = Body
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.stg_rate_unit), style = BodyMuted)
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(Indigo)
                                            .clickable { vm.setExchangeRate(code, rateInputs[code]?.toDoubleOrNull() ?: 0.0) },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Check, stringResource(R.string.stg_save), tint = White, modifier = Modifier.size(20.dp)) }
                                }
                            }
                            SettingsDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.stg_notif_label), style = Body.copy(fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(R.string.stg_notif_desc), style = Eyebrow)
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
                        }

                        // ===================== ABOUT =====================
                        else -> {
                            Text(stringResource(R.string.app_name), style = Body.copy(fontWeight = FontWeight.Bold))
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.stg_privacy_body), style = Eyebrow)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close), style = Body.copy(color = InkSoft))
            }
        }
    )
}

// ============ SETTINGS BUILDING BLOCKS ============
@Composable
private fun SettingsFieldHeader(title: String, desc: String) {
    Text(title, style = Body.copy(fontWeight = FontWeight.Bold))
    Spacer(Modifier.height(4.dp))
    Text(desc, style = Eyebrow)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    background: Color = PaperOuter,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, iconTint, White, size = 40.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Body.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = Eyebrow.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun <T> SettingsSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(PaperOuter)
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val sel = selected == value
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(if (sel) White else Color.Transparent)
                    .clickable { onSelect(value) }
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
}

@Composable
private fun SettingsInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSave: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = Eyebrow) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(RadiusSm),
            textStyle = Body
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(RadiusSm)).background(Indigo)
                .clickable(onClick = onSave),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Check, stringResource(R.string.stg_save), tint = White, modifier = Modifier.size(20.dp)) }
    }
}

// ============ BOTTOM NAV ============
@Composable
private fun BottomNav(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple(stringResource(R.string.nav_home),         Icons.Filled.Home,                     0),
        Triple(stringResource(R.string.nav_transactions), Icons.Filled.ReceiptLong,              1),
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
