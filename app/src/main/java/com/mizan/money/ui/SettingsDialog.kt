package com.mizan.money.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mizan.money.BuildConfig
import com.mizan.money.MoneyApp
import com.mizan.money.R
import com.mizan.money.cloud.CloudBackup
import com.mizan.money.data.BackupData
import com.mizan.money.data.BackupManager
import com.mizan.money.data.ExchangeRates
import com.mizan.money.widget.WidgetTheme
import com.mizan.money.widget.WidgetThemePreference
import com.mizan.money.widget.WidgetUpdater
import com.mizan.money.ui.theme.LanguageMode
import com.mizan.money.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Which bottom sheet (if any) is open. Plain strings so rememberSaveable can
// keep it across rotation.
private const val SHEET_NONE = ""
private const val SHEET_NAME = "name"
private const val SHEET_LANGUAGE = "language"
private const val SHEET_THEME = "theme"
private const val SHEET_MONTH = "month"
private const val SHEET_RATES = "rates"
private const val SHEET_RULES = "rules"
private const val SHEET_CLOUD = "cloud"

@Composable
internal fun SettingsDialog(
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
    val ownerName by vm.ownerName.collectAsState()
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val rates by vm.exchangeRates.collectAsState()
    val categories by vm.categories.collectAsState()
    val customRules by vm.customRules.collectAsState()
    val lastRecategorize by vm.lastRecategorizeCount.collectAsState()
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setNotificationsEnabled(granted) }

    var sheet by rememberSaveable { mutableStateOf(SHEET_NONE) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    // ---- Cloud backup ----
    var cloudBusy by remember { mutableStateOf(false) }
    var cloudSignedIn by remember { mutableStateOf(false) }
    var showCloudRestoreConfirm by remember { mutableStateOf(false) }

    val app = ctx.applicationContext as MoneyApp
    val backupShareTitle = stringResource(R.string.stg_backup_share)
    val savedText = stringResource(R.string.stg2_saved)
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

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
                    if (text == null) toast(ctx.getString(R.string.stg_restore_failed)) else pendingRestoreJson = text
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        CloudBackup.init(ctx)
        cloudSignedIn = CloudBackup.isSignedIn
    }

    val runCloudUpload: () -> Unit = {
        scope.launch {
            cloudBusy = true
            val ok = try {
                val dao = app.db.backupDao()
                val data = withContext(Dispatchers.IO) {
                    BackupData(
                        transactions = dao.transactions(),
                        budgets = dao.budgets(),
                        goals = dao.goals(),
                        debts = dao.debts(),
                        recurringItems = dao.recurringItems(),
                        goalContributions = dao.goalContributions(),
                    )
                }
                CloudBackup.upload(data).isSuccess
            } catch (e: Exception) {
                false
            }
            cloudBusy = false
            toast(ctx.getString(if (ok) R.string.stg_cloud_upload_ok else R.string.stg_cloud_failed))
        }
    }

    // ================= Confirm dialogs (destructive / sharing) =================
    if (showCloudRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showCloudRestoreConfirm = false },
            containerColor = White,
            shape = RoundedCornerShape(RadiusXl),
            title = { Text(stringResource(R.string.stg_cloud_restore_confirm_title), style = H2) },
            text = { Text(stringResource(R.string.stg_cloud_restore_confirm_body), style = BodyMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showCloudRestoreConfirm = false
                    scope.launch {
                        cloudBusy = true
                        val ok = try {
                            val data = CloudBackup.download().getOrThrow()
                            withContext(Dispatchers.IO) { app.db.backupDao().restore(data) }
                            WidgetUpdater.refresh(ctx)
                            true
                        } catch (e: Exception) {
                            false
                        }
                        cloudBusy = false
                        toast(ctx.getString(if (ok) R.string.stg_cloud_restore_ok else R.string.stg_cloud_failed))
                    }
                }) { Text(stringResource(R.string.stg_cloud_restore_action), style = Body.copy(color = Danger, fontWeight = FontWeight.Bold)) }
            },
            dismissButton = {
                TextButton(onClick = { showCloudRestoreConfirm = false }) {
                    Text(stringResource(R.string.stg_cancel), style = Body.copy(color = InkSoft))
                }
            }
        )
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
                        withContext(Dispatchers.Main) {
                            toast(ctx.getString(if (ok) R.string.stg_restore_ok else R.string.stg_restore_failed))
                        }
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

    // ================= Screen =================
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(Modifier.fillMaxSize().background(Paper)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back), tint = Ink)
                }
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_title), style = H1)
            }
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==================== GENERAL ====================
                SectionCard(stringResource(R.string.stg2_section_general)) {
                    SettingsRow(
                        icon = Icons.Default.Person, tint = Purple,
                        title = stringResource(R.string.stg_name_label),
                        value = ownerName.ifBlank { stringResource(R.string.stg2_not_set) },
                        onClick = { sheet = SHEET_NAME }
                    )
                    SettingsRow(
                        icon = Icons.Default.Language, tint = Indigo,
                        title = stringResource(R.string.settings_language),
                        value = stringResource(
                            when (languageMode) {
                                LanguageMode.SYSTEM -> R.string.settings_language_system
                                LanguageMode.ARABIC -> R.string.settings_language_arabic
                                LanguageMode.ENGLISH -> R.string.settings_language_english
                            }
                        ),
                        onClick = { sheet = SHEET_LANGUAGE }
                    )
                    SettingsRow(
                        icon = Icons.Default.DarkMode, tint = IndigoDeep,
                        title = stringResource(R.string.stg_theme_label),
                        value = stringResource(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> R.string.stg_theme_system
                                ThemeMode.LIGHT -> R.string.stg_theme_light
                                ThemeMode.DARK -> R.string.stg_theme_dark
                            }
                        ),
                        onClick = { sheet = SHEET_THEME }
                    )
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Default.Palette, Amber, Amber.copy(alpha = 0.12f), size = 40.dp, iconSize = 20.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                stringResource(R.string.stg_widget_color_label),
                                style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        WidgetColorPicker()
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Notifications, tint = Danger,
                        title = stringResource(R.string.stg_notif_label),
                        value = stringResource(R.string.stg_notif_desc),
                        showDivider = false,
                        onClick = {
                            val turningOn = !notificationsEnabled
                            if (turningOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotificationsEnabled(turningOn)
                            }
                        },
                        trailing = {
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { turningOn ->
                                    if (turningOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        vm.setNotificationsEnabled(turningOn)
                                    }
                                },
                                colors = oneUiSwitchColors()
                            )
                        }
                    )
                }

                // ==================== BUDGET ====================
                SectionCard(stringResource(R.string.stg2_section_budget)) {
                    SettingsRow(
                        icon = Icons.Default.CalendarMonth, tint = Amber,
                        title = stringResource(R.string.stg_month_start_label),
                        value = stringResource(R.string.stg2_day_fmt, insNum(startDay)),
                        onClick = { sheet = SHEET_MONTH }
                    )
                    SettingsRow(
                        icon = Icons.Default.SwapHoriz, tint = Success,
                        title = stringResource(R.string.stg_rates_label),
                        value = ratesSummary(rates),
                        onClick = { sheet = SHEET_RATES }
                    )
                    SettingsRow(
                        icon = Icons.Default.Rule, tint = IndigoDeep,
                        title = stringResource(R.string.stg_custom_rules_title),
                        value = if (customRules.isEmpty()) stringResource(R.string.stg_custom_rules_desc)
                                else stringResource(R.string.stg2_rules_count_fmt, insNum(customRules.size)),
                        onClick = { sheet = SHEET_RULES }
                    )
                    SettingsRow(
                        icon = Icons.Default.AutoFixHigh, tint = Indigo,
                        title = stringResource(R.string.stg_recategorize_title),
                        value = if (lastRecategorize > 0) stringResource(R.string.stg_recategorize_done_fmt, lastRecategorize)
                                else stringResource(R.string.stg_recategorize_desc),
                        showDivider = false,
                        onClick = { vm.recategorizeAll() }
                    )
                }

                // ==================== BACKUP ====================
                SectionCard(stringResource(R.string.stg2_section_backup)) {
                    SettingsRow(
                        icon = Icons.Default.Backup, tint = Indigo,
                        title = stringResource(R.string.stg_backup_title),
                        value = stringResource(R.string.stg_backup_subtitle),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val dao = app.db.backupDao()
                                val data = BackupData(
                                    transactions = dao.transactions(),
                                    budgets = dao.budgets(),
                                    goals = dao.goals(),
                                    debts = dao.debts(),
                                    recurringItems = dao.recurringItems(),
                                    goalContributions = dao.goalContributions(),
                                )
                                val file = writeBackupFile(ctx, BackupManager.toJson(data))
                                withContext(Dispatchers.Main) {
                                    shareExportFile(ctx, file, "application/json", backupShareTitle)
                                }
                            }
                        }
                    )
                    SettingsRow(
                        icon = Icons.Default.Restore, tint = Danger,
                        title = stringResource(R.string.stg_restore_title),
                        value = stringResource(R.string.stg_restore_subtitle),
                        onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    )
                    when {
                        !CloudBackup.isConfigured -> SettingsRow(
                            icon = Icons.Default.Cloud, tint = InkSoft,
                            title = stringResource(R.string.stg_cloud_title),
                            value = stringResource(R.string.stg2_cloud_unavailable),
                            showDivider = false,
                            enabled = false
                        )
                        !cloudSignedIn -> SettingsRow(
                            icon = Icons.Default.Cloud, tint = IndigoDeep,
                            title = stringResource(R.string.stg_cloud_title),
                            value = stringResource(R.string.stg2_cloud_sign_in_row),
                            showDivider = false,
                            onClick = { sheet = SHEET_CLOUD }
                        )
                        else -> {
                            SettingsRow(
                                icon = Icons.Default.CloudUpload, tint = Indigo,
                                title = stringResource(R.string.stg2_cloud_upload_title),
                                value = stringResource(R.string.stg_cloud_signed_in_fmt, CloudBackup.currentEmail ?: ""),
                                enabled = !cloudBusy,
                                onClick = runCloudUpload,
                                busy = cloudBusy
                            )
                            SettingsRow(
                                icon = Icons.Default.CloudDownload, tint = Danger,
                                title = stringResource(R.string.stg_cloud_download_title),
                                value = stringResource(R.string.stg_cloud_download_subtitle),
                                enabled = !cloudBusy,
                                onClick = { showCloudRestoreConfirm = true }
                            )
                            SettingsRow(
                                icon = Icons.AutoMirrored.Filled.Logout, tint = InkSoft,
                                title = stringResource(R.string.stg_cloud_sign_out),
                                value = CloudBackup.currentEmail,
                                showDivider = false,
                                enabled = !cloudBusy,
                                onClick = {
                                    CloudBackup.signOut()
                                    cloudSignedIn = false
                                }
                            )
                        }
                    }
                }

                // ==================== ADVANCED ====================
                SectionCard(stringResource(R.string.stg2_section_advanced)) {
                    SettingsRow(
                        icon = Icons.Default.Sync, tint = Indigo,
                        title = if (isScanning) stringResource(R.string.stg_rescan_busy)
                                else stringResource(R.string.stg_rescan_title),
                        value = stringResource(R.string.stg_rescan_subtitle),
                        enabled = !isScanning,
                        onClick = onRescan
                    )
                    SettingsRow(
                        icon = Icons.Default.BugReport, tint = InkSoft,
                        title = stringResource(R.string.stg_export_title),
                        value = if (txs.isEmpty()) stringResource(R.string.stg_export_none)
                                else stringResource(R.string.stg_export_subtitle),
                        showDivider = false,
                        enabled = txs.isNotEmpty(),
                        onClick = { showExportConfirm = true }
                    )
                }

                // ==================== ABOUT ====================
                SectionCard(stringResource(R.string.stg2_section_about)) {
                    SettingsRow(
                        icon = Icons.Default.Info, tint = Indigo,
                        title = stringResource(R.string.stg2_version),
                        value = appVersionName(ctx)
                    )
                    SettingsRow(
                        icon = Icons.Default.Shield, tint = Success,
                        title = stringResource(R.string.stg2_privacy_title),
                        value = stringResource(R.string.stg_privacy_body),
                        valueMaxLines = 6,
                        showDivider = false
                    )
                }
            }
            // Sheets live inside the Dialog so they always open above it.
            // ================= Sheets =================
            val closeSheet = { sheet = SHEET_NONE }
            when (sheet) {
                SHEET_NAME -> SettingsNameSheet(
                    initial = ownerName,
                    onDismiss = closeSheet,
                    onSave = { vm.setOwnerName(it); sheet = SHEET_NONE; toast(savedText) }
                )
                SHEET_LANGUAGE -> SettingsChoiceSheet(
                    title = stringResource(R.string.settings_language),
                    options = listOf(
                        LanguageMode.SYSTEM to stringResource(R.string.settings_language_system),
                        LanguageMode.ARABIC to stringResource(R.string.settings_language_arabic),
                        LanguageMode.ENGLISH to stringResource(R.string.settings_language_english),
                    ),
                    selected = languageMode,
                    onDismiss = closeSheet,
                    onSelect = { sheet = SHEET_NONE; onLanguageModeChange(it) }
                )
                SHEET_THEME -> SettingsChoiceSheet(
                    title = stringResource(R.string.stg_theme_label),
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.stg_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.stg_theme_light),
                        ThemeMode.DARK to stringResource(R.string.stg_theme_dark),
                    ),
                    selected = themeMode,
                    onDismiss = closeSheet,
                    onSelect = { sheet = SHEET_NONE; onThemeModeChange(it) }
                )
                SHEET_MONTH -> SettingsDayPickerSheet(
                    selected = startDay,
                    onDismiss = closeSheet,
                    onPick = { vm.setMonthStartDay(it); sheet = SHEET_NONE; toast(savedText) }
                )
                SHEET_RATES -> SettingsRatesSheet(
                    rates = rates,
                    onDismiss = closeSheet,
                    onSave = { edited ->
                        edited.forEach { (code, v) -> vm.setExchangeRate(code, v) }
                        sheet = SHEET_NONE
                        toast(savedText)
                    }
                )
                SHEET_RULES -> SettingsRulesSheet(
                    rules = customRules,
                    categories = categories,
                    onAdd = { k, c -> vm.addCustomRule(k, c) },
                    onRemove = { vm.removeCustomRule(it) },
                    onDismiss = closeSheet
                )
                SHEET_CLOUD -> SettingsCloudSignInSheet(
                    onDismiss = closeSheet,
                    onSignedIn = {
                        cloudSignedIn = CloudBackup.isSignedIn
                        sheet = SHEET_NONE
                        toast(ctx.getString(R.string.stg_cloud_signed_in_fmt, CloudBackup.currentEmail ?: ""))
                    }
                )
            }
        }
    }
}

private fun appVersionName(ctx: Context): String =
    BuildConfig.VERSION_NAME.ifBlank {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
    }

private fun rateText(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@Composable
private fun ratesSummary(rates: Map<String, Double>): String {
    val arabic = isArabicUi()
    return ExchangeRates.supported.joinToString(" · ") { code ->
        val v = rateText(rates[code] ?: 0.0)
        "$code ${if (arabic) toArabicIndicDigits(v) else v}"
    }
}

// ============ SETTINGS BUILDING BLOCKS ============
// One UI list row: circular icon, title, current value underneath, and a
// trailing chevron (or custom trailing content such as a switch).
@Composable
private fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String? = null,
    valueMaxLines: Int = 2,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    busy: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, tint, tint.copy(alpha = 0.12f), size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                if (!value.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        value,
                        style = Body.copy(fontSize = 13.sp, color = InkSoft),
                        maxLines = valueMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (busy) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(20.dp), color = Indigo, strokeWidth = 2.dp)
            } else if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = InkFaint)
            }
        }
        if (showDivider) SettingsDivider()
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.padding(start = 70.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Line))
}

@Composable
private fun SheetCancelButton(onClick: () -> Unit, label: String = stringResource(R.string.stg_cancel)) {
    TextButton(onClick = onClick) { Text(label, style = Body.copy(color = InkSoft)) }
}

@Composable
private fun SheetPrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Pill),
        colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Lime)
    ) { Text(label, style = Body.copy(color = if (enabled) Lime else InkFaint, fontWeight = FontWeight.Bold)) }
}

@Composable
private fun SettingsNameSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initial) }
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stg_name_label), style = H2) },
        text = {
            Column {
                Text(stringResource(R.string.stg_name_desc), style = BodyMuted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.stg_name_hint), style = BodyMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
            }
        },
        dismissButton = { SheetCancelButton(onDismiss) },
        confirmButton = { SheetPrimaryButton(stringResource(R.string.stg_save)) { onSave(input) } }
    )
}

@Composable
private fun <T> SettingsChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(title, style = H2) },
        text = {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusLg)).background(Paper)) {
                options.forEachIndexed { i, (value, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = Indigo, unselectedColor = InkFaint)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                    }
                    if (i < options.lastIndex) {
                        Box(Modifier.padding(start = 60.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Line))
                    }
                }
            }
        },
        confirmButton = { SheetCancelButton(onDismiss) }
    )
}

// Grid of days 1-28 (the ViewModel clamps to 28 so every month can host it).
@Composable
private fun SettingsDayPickerSheet(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stg_month_start_label), style = H2) },
        text = {
            Column {
                Text(stringResource(R.string.stg_month_start_desc), style = BodyMuted)
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..28).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            week.forEach { day ->
                                val sel = day == selected
                                Box(
                                    Modifier.weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(Pill))
                                        .background(if (sel) Indigo else Color.Transparent)
                                        .clickable { onPick(day) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        insNum(day),
                                        style = Body.copy(
                                            fontSize = 15.sp,
                                            color = if (sel) Lime else Ink,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { SheetCancelButton(onDismiss) }
    )
}

@Composable
private fun SettingsRatesSheet(
    rates: Map<String, Double>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double>) -> Unit,
) {
    var inputs by remember(rates) {
        mutableStateOf(ExchangeRates.supported.associateWith { rateText(rates[it] ?: 0.0) })
    }
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stg_rates_label), style = H2) },
        text = {
            Column {
                Text(stringResource(R.string.stg_rates_desc), style = BodyMuted)
                Spacer(Modifier.height(12.dp))
                ExchangeRates.supported.forEach { code ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.stg_rate_fmt, code), style = Body, modifier = Modifier.width(72.dp))
                        OutlinedTextField(
                            value = inputs[code] ?: "",
                            onValueChange = { inputs = inputs + (code to sanitizeAmountInput(it)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(RadiusSm),
                            textStyle = Body
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stg_rate_unit), style = BodyMuted)
                    }
                }
            }
        },
        dismissButton = { SheetCancelButton(onDismiss) },
        confirmButton = {
            SheetPrimaryButton(stringResource(R.string.stg_save)) {
                onSave(inputs.mapValues { it.value.toDoubleOrNull() ?: 0.0 })
            }
        }
    )
}

@Composable
private fun SettingsRulesSheet(
    rules: List<Pair<String, String>>,
    categories: List<String>,
    onAdd: (String, String) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(categories.firstOrNull() ?: "") }
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stg_custom_rules_title), style = H2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.stg_custom_rules_desc), style = BodyMuted)
                Spacer(Modifier.height(12.dp))
                if (rules.isEmpty()) {
                    Text(stringResource(R.string.stg_custom_rules_empty), style = Body.copy(fontSize = 13.sp, color = InkSoft))
                    Spacer(Modifier.height(8.dp))
                } else {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd)).background(Paper)) {
                        rules.forEachIndexed { index, rule ->
                            Row(
                                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rule.first, style = Body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward, null,
                                    Modifier.padding(horizontal = 8.dp).size(16.dp), tint = InkFaint
                                )
                                Text(categoryDisplay(rule.second), style = Body.copy(color = Indigo, fontWeight = FontWeight.SemiBold),
                                    maxLines = 1, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onRemove(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        stringResource(R.string.stg_custom_rule_delete),
                                        tint = Danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (index < rules.lastIndex) {
                                Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Line))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text(stringResource(R.string.stg_custom_rule_keyword_hint), style = BodyMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c ->
                                val sel = category == c
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(Pill))
                                        .background(if (sel) IndigoSoft else PaperOuter)
                                        .clickable { category = c }
                                        .padding(vertical = 9.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        categoryDisplay(c),
                                        style = Body.copy(
                                            fontSize = 13.sp,
                                            color = if (sel) Indigo else InkSoft,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        dismissButton = { SheetCancelButton(onDismiss, stringResource(R.string.settings_close)) },
        confirmButton = {
            val canAdd = keyword.isNotBlank() && category.isNotBlank()
            SheetPrimaryButton(stringResource(R.string.stg_custom_rule_add), enabled = canAdd) {
                onAdd(keyword, category)
                keyword = ""
            }
        }
    )
}

// Maps sign-in failures to plain-language messages; null = stay silent (the
// user cancelled the account picker themselves). Type checks, not class
// names, so it keeps working after R8 renames classes.
private fun cloudErrorRes(t: Throwable?): Int? {
    var e: Throwable? = t
    var depth = 0
    while (e != null && depth < 5) {
        when (e) {
            is androidx.credentials.exceptions.GetCredentialCancellationException -> return null
            is androidx.credentials.exceptions.NoCredentialException -> return R.string.stg2_err_no_google
            is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> return R.string.stg2_err_weak_password
            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> return R.string.stg2_err_wrong_credentials
            is com.google.firebase.auth.FirebaseAuthInvalidUserException -> return R.string.stg2_err_no_account
            is com.google.firebase.auth.FirebaseAuthUserCollisionException -> return R.string.stg2_err_email_taken
            is com.google.firebase.FirebaseTooManyRequestsException -> return R.string.stg2_err_too_many
            is com.google.firebase.FirebaseNetworkException -> return R.string.stg2_err_network
            is java.io.IOException -> return R.string.stg2_err_network
        }
        e = e.cause
        depth++
    }
    return R.string.stg2_err_generic
}

@Composable
private fun SettingsCloudSignInSheet(onDismiss: () -> Unit, onSignedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(CloudBackup.currentEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun handle(result: Result<Unit>) {
        if (result.isSuccess) {
            password = ""
            onSignedIn()
        } else {
            error = cloudErrorRes(result.exceptionOrNull())?.let { ctx.getString(it) }
        }
    }
    val runAuth: (Boolean) -> Unit = { isSignUp ->
        if (email.isBlank() || password.isBlank()) {
            error = ctx.getString(R.string.stg_cloud_credentials_required)
        } else {
            scope.launch {
                busy = true
                error = null
                val result = if (isSignUp) CloudBackup.signUp(email, password) else CloudBackup.signIn(email, password)
                busy = false
                handle(result)
            }
        }
    }

    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stg_cloud_title), style = H2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.stg2_cloud_intro), style = BodyMuted)
                Spacer(Modifier.height(14.dp))
                if (CloudBackup.isGoogleSignInConfigured) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                val result = try {
                                    CloudBackup.signInWithGoogle(ctx)
                                } catch (t: Throwable) {
                                    Result.failure(t)
                                }
                                busy = false
                                handle(result)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(Pill)
                    ) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(18.dp), tint = Indigo)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stg_cloud_google), style = Body.copy(color = Ink, fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(Line))
                        Text(
                            stringResource(R.string.stg_cloud_or),
                            style = Body.copy(fontSize = 13.sp, color = InkSoft),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Box(Modifier.weight(1f).height(1.dp).background(Line))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    placeholder = { Text(stringResource(R.string.stg_cloud_email_hint), style = BodyMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text(stringResource(R.string.stg_cloud_password_hint), style = BodyMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusSm),
                    textStyle = Body
                )
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Indigo, trackColor = IndigoSoft)
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = Body.copy(fontSize = 13.sp, color = Danger), textAlign = TextAlign.Start)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { runAuth(true) }, enabled = !busy) {
                Text(stringResource(R.string.stg_cloud_sign_up), style = Body.copy(color = Indigo, fontWeight = FontWeight.SemiBold))
            }
        },
        confirmButton = {
            SheetPrimaryButton(stringResource(R.string.stg_cloud_sign_in), enabled = !busy) { runAuth(false) }
        }
    )
}

// Widget color swatches. AUTO is drawn half light / half dark; saving refreshes
// the home-screen widget right away.
@Composable
private fun WidgetColorPicker() {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(WidgetThemePreference.load(ctx)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        WidgetTheme.values().forEach { t ->
            val sel = t == selected
            Box(
                Modifier.size(40.dp)
                    .border(if (sel) 2.5.dp else 1.dp, if (sel) Indigo else Line, RoundedCornerShape(Pill))
                    .padding(if (sel) 4.dp else 1.dp)
                    .clip(RoundedCornerShape(Pill))
                    .clickable {
                        selected = t
                        WidgetThemePreference.save(ctx, t)
                        WidgetUpdater.refresh(ctx)
                    }
            ) {
                if (t == WidgetTheme.AUTO) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color(WidgetTheme.LIGHT.argb)))
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color(WidgetTheme.DARK.argb)))
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(Color(t.argb)))
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(
            when (selected) {
                WidgetTheme.AUTO -> R.string.widget_theme_auto
                WidgetTheme.LIGHT -> R.string.widget_theme_light
                WidgetTheme.DARK -> R.string.widget_theme_dark
                WidgetTheme.BLUE -> R.string.widget_theme_blue
                WidgetTheme.GREEN -> R.string.widget_theme_green
                WidgetTheme.PURPLE -> R.string.widget_theme_purple
                WidgetTheme.ROSE -> R.string.widget_theme_rose
            }
        ),
        style = Body.copy(fontSize = 13.sp, color = InkSoft)
    )
}
