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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
import com.mizan.money.cloud.CloudBackup
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
    var ruleKeyword by remember { mutableStateOf("") }
    var ruleCategory by remember { mutableStateOf("") }
    // Seed the default selection once categories are available, without
    // clobbering whatever the user has since picked.
    LaunchedEffect(categories) {
        if (ruleCategory.isBlank() && categories.isNotEmpty()) ruleCategory = categories.first()
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var restoreResult by remember { mutableStateOf<Boolean?>(null) }

    // ---- Cloud backup (Firebase) ----
    var cloudOpen by rememberSaveable { mutableStateOf(false) }
    var cloudEmail by rememberSaveable { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudBusy by remember { mutableStateOf(false) }
    var cloudMessage by remember { mutableStateOf<String?>(null) }
    var cloudSignedIn by remember { mutableStateOf(false) }
    var showCloudRestoreConfirm by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        CloudBackup.init(ctx)
        cloudSignedIn = CloudBackup.isSignedIn
        if (cloudEmail.isBlank()) cloudEmail = CloudBackup.currentEmail ?: ""
    }

    val runCloudAuth: (Boolean) -> Unit = { isSignUp ->
        if (cloudEmail.isBlank() || cloudPassword.isBlank()) {
            cloudMessage = ctx.getString(R.string.stg_cloud_credentials_required)
        } else {
            scope.launch {
                cloudBusy = true
                cloudMessage = null
                val result = if (isSignUp) CloudBackup.signUp(cloudEmail, cloudPassword)
                             else CloudBackup.signIn(cloudEmail, cloudPassword)
                cloudBusy = false
                if (result.isSuccess) {
                    cloudSignedIn = CloudBackup.isSignedIn
                    cloudPassword = ""
                    cloudMessage = null
                } else {
                    cloudMessage = result.exceptionOrNull()?.localizedMessage
                        ?: ctx.getString(R.string.stg_cloud_failed)
                }
            }
        }
    }

    val runCloudGoogle: () -> Unit = {
        scope.launch {
            cloudBusy = true
            cloudMessage = null
            val result = CloudBackup.signInWithGoogle(ctx)
            cloudBusy = false
            if (result.isSuccess) {
                cloudSignedIn = CloudBackup.isSignedIn
                cloudMessage = null
            } else {
                cloudMessage = result.exceptionOrNull()?.localizedMessage
                    ?: ctx.getString(R.string.stg_cloud_failed)
            }
        }
    }

    val runCloudUpload: () -> Unit = {
        scope.launch {
            cloudBusy = true
            cloudMessage = null
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
            cloudMessage = ctx.getString(if (ok) R.string.stg_cloud_upload_ok else R.string.stg_cloud_failed)
        }
    }

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
                        cloudMessage = null
                        val ok = try {
                            val data = CloudBackup.download().getOrThrow()
                            withContext(Dispatchers.IO) { app.db.backupDao().restore(data) }
                            WidgetUpdater.refresh(ctx)
                            true
                        } catch (e: Exception) {
                            false
                        }
                        cloudBusy = false
                        cloudOpen = true
                        cloudMessage = ctx.getString(if (ok) R.string.stg_cloud_restore_ok else R.string.stg_cloud_failed)
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
                                            goalContributions = dao.goalContributions(),
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
                            Spacer(Modifier.height(10.dp))
                            SettingsSection(
                                icon = Icons.Default.Cloud,
                                tint = IndigoDeep,
                                title = stringResource(R.string.stg_cloud_title),
                                summary = when {
                                    !CloudBackup.isConfigured -> stringResource(R.string.stg_cloud_not_configured)
                                    cloudSignedIn -> stringResource(
                                        R.string.stg_cloud_signed_in_fmt,
                                        CloudBackup.currentEmail ?: ""
                                    )
                                    else -> stringResource(R.string.stg_cloud_signed_out)
                                },
                                expanded = cloudOpen,
                                onToggle = { cloudOpen = !cloudOpen }
                            ) {
                                if (!CloudBackup.isConfigured) {
                                    Text(stringResource(R.string.stg_cloud_disabled_desc), style = Eyebrow)
                                } else if (cloudSignedIn) {
                                    Text(stringResource(R.string.stg_cloud_intro), style = Eyebrow)
                                    Spacer(Modifier.height(10.dp))
                                    SettingsActionRow(
                                        icon = Icons.Default.CloudUpload,
                                        iconTint = Indigo,
                                        background = IndigoSoft,
                                        title = stringResource(R.string.stg_cloud_upload_title),
                                        subtitle = stringResource(R.string.stg_cloud_upload_subtitle),
                                        enabled = !cloudBusy,
                                        onClick = runCloudUpload
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SettingsActionRow(
                                        icon = Icons.Default.CloudDownload,
                                        iconTint = Danger,
                                        title = stringResource(R.string.stg_cloud_download_title),
                                        subtitle = stringResource(R.string.stg_cloud_download_subtitle),
                                        enabled = !cloudBusy,
                                        onClick = { showCloudRestoreConfirm = true }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SettingsActionRow(
                                        icon = Icons.AutoMirrored.Filled.Logout,
                                        iconTint = InkSoft,
                                        title = stringResource(R.string.stg_cloud_sign_out),
                                        subtitle = CloudBackup.currentEmail ?: "",
                                        enabled = !cloudBusy,
                                        onClick = {
                                            CloudBackup.signOut()
                                            cloudSignedIn = false
                                            cloudMessage = null
                                        }
                                    )
                                } else {
                                    Text(stringResource(R.string.stg_cloud_intro), style = Eyebrow)
                                    Spacer(Modifier.height(10.dp))
                                    if (CloudBackup.isGoogleSignInConfigured) {
                                        SettingsActionRow(
                                            icon = Icons.Default.AccountCircle,
                                            iconTint = Indigo,
                                            background = IndigoSoft,
                                            title = stringResource(R.string.stg_cloud_google),
                                            subtitle = stringResource(R.string.stg_cloud_google_sub),
                                            enabled = !cloudBusy,
                                            onClick = runCloudGoogle
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.weight(1f).height(1.dp).background(Line))
                                            Text(
                                                stringResource(R.string.stg_cloud_or),
                                                style = Eyebrow,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            )
                                            Box(Modifier.weight(1f).height(1.dp).background(Line))
                                        }
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    OutlinedTextField(
                                        value = cloudEmail,
                                        onValueChange = { cloudEmail = it.trim() },
                                        placeholder = { Text(stringResource(R.string.stg_cloud_email_hint), style = Eyebrow) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(RadiusSm),
                                        textStyle = Body
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = cloudPassword,
                                        onValueChange = { cloudPassword = it },
                                        placeholder = { Text(stringResource(R.string.stg_cloud_password_hint), style = Eyebrow) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(RadiusSm),
                                        textStyle = Body
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            Modifier.weight(1f)
                                                .clip(RoundedCornerShape(RadiusSm))
                                                .background(if (cloudBusy) PaperOuter else Indigo)
                                                .clickable(enabled = !cloudBusy) { runCloudAuth(false) }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                stringResource(R.string.stg_cloud_sign_in),
                                                style = Body.copy(
                                                    color = if (cloudBusy) InkFaint else White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        Box(
                                            Modifier.weight(1f)
                                                .clip(RoundedCornerShape(RadiusSm))
                                                .background(PaperOuter)
                                                .clickable(enabled = !cloudBusy) { runCloudAuth(true) }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                stringResource(R.string.stg_cloud_sign_up),
                                                style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                                if (cloudBusy) {
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        Modifier.fillMaxWidth(),
                                        color = Indigo,
                                        trackColor = IndigoSoft
                                    )
                                }
                                cloudMessage?.let {
                                    Spacer(Modifier.height(8.dp))
                                    Text(it, style = Eyebrow)
                                }
                            }
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
                            var open by rememberSaveable { mutableStateOf("") }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSection(
                                    icon = Icons.Default.Person,
                                    tint = Purple,
                                    title = stringResource(R.string.stg_name_label),
                                    summary = stringResource(R.string.stg_name_desc),
                                    expanded = open == "name",
                                    onToggle = { open = if (open == "name") "" else "name" }
                                ) {
                                    SettingsInputRow(
                                        value = nameInput,
                                        onValueChange = { nameInput = it },
                                        placeholder = stringResource(R.string.stg_name_hint),
                                        onSave = { vm.setOwnerName(nameInput) }
                                    )
                                }
                                SettingsSection(
                                    icon = Icons.Default.CalendarMonth,
                                    tint = Amber,
                                    title = stringResource(R.string.stg_month_start_label),
                                    summary = stringResource(R.string.stg_month_start_desc),
                                    expanded = open == "month",
                                    onToggle = { open = if (open == "month") "" else "month" }
                                ) {
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
                                }
                                SettingsSection(
                                    icon = Icons.Default.SwapHoriz,
                                    tint = Success,
                                    title = stringResource(R.string.stg_rates_label),
                                    summary = stringResource(R.string.stg_rates_desc),
                                    expanded = open == "rates",
                                    onToggle = { open = if (open == "rates") "" else "rates" }
                                ) {
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
                                }
                                SettingsSection(
                                    icon = Icons.Default.AutoFixHigh,
                                    tint = IndigoDeep,
                                    title = stringResource(R.string.stg_custom_rules_title),
                                    summary = stringResource(R.string.stg_custom_rules_desc),
                                    expanded = open == "rules",
                                    onToggle = { open = if (open == "rules") "" else "rules" }
                                ) {
                                    if (customRules.isEmpty()) {
                                        Text(stringResource(R.string.stg_custom_rules_empty), style = Eyebrow)
                                        Spacer(Modifier.height(8.dp))
                                    } else {
                                        customRules.forEachIndexed { index, rule ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${rule.first} → ${rule.second}", style = Body, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { vm.removeCustomRule(index) }) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        stringResource(R.string.stg_custom_rule_delete),
                                                        tint = Danger,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    OutlinedTextField(
                                        value = ruleKeyword,
                                        onValueChange = { ruleKeyword = it },
                                        placeholder = { Text(stringResource(R.string.stg_custom_rule_keyword_hint), style = Eyebrow) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(RadiusSm),
                                        textStyle = Body
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        categories.chunked(2).forEach { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                row.forEach { c ->
                                                    Box(
                                                        Modifier.weight(1f)
                                                            .clip(RoundedCornerShape(RadiusSm))
                                                            .background(if (ruleCategory == c) IndigoSoft else PaperOuter)
                                                            .clickable { ruleCategory = c }
                                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            categoryDisplay(c),
                                                            style = Eyebrow.copy(
                                                                color = if (ruleCategory == c) Indigo else InkSoft,
                                                                fontWeight = if (ruleCategory == c) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        )
                                                    }
                                                }
                                                if (row.size == 1) Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    val canAdd = ruleKeyword.isNotBlank() && ruleCategory.isNotBlank()
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(RadiusSm))
                                            .background(if (canAdd) Indigo else PaperOuter)
                                            .clickable(enabled = canAdd) {
                                                vm.addCustomRule(ruleKeyword, ruleCategory)
                                                ruleKeyword = ""
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            stringResource(R.string.stg_custom_rule_add),
                                            style = Body.copy(color = if (canAdd) White else InkFaint, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    SettingsActionRow(
                                        icon = Icons.Default.AutoFixHigh,
                                        iconTint = Indigo,
                                        title = stringResource(R.string.stg_recategorize_title),
                                        subtitle = stringResource(R.string.stg_recategorize_desc),
                                        onClick = { vm.recategorizeAll() }
                                    )
                                    if (lastRecategorize > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(stringResource(R.string.stg_recategorize_done_fmt, lastRecategorize), style = Eyebrow)
                                    }
                                }
                                SettingsSection(
                                    icon = Icons.Default.Notifications,
                                    tint = Indigo,
                                    title = stringResource(R.string.stg_notif_label),
                                    summary = stringResource(R.string.stg_notif_desc),
                                    switchState = notificationsEnabled,
                                    onSwitchChange = { turningOn ->
                                        if (turningOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            vm.setNotificationsEnabled(turningOn)
                                        }
                                    }
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
private fun SettingsSection(
    icon: ImageVector,
    tint: Color,
    title: String,
    summary: String,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    switchState: Boolean? = null,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val expandable = onToggle != null
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(White)
            .border(1.dp, Line, RoundedCornerShape(RadiusLg))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, tint, tint.copy(alpha = 0.12f), size = 38.dp, iconSize = 18.dp, radius = RadiusSm)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Body.copy(fontWeight = FontWeight.Bold))
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        summary,
                        style = Eyebrow.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (switchState != null) {
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = switchState,
                    onCheckedChange = { onSwitchChange?.invoke(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
                )
            } else if (expandable) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = InkFaint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (expandable) {
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

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
