package com.mizan.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mizan.money.R
import java.util.Calendar

// Shared building blocks for the Planning tab (budget, goals, commitments) so
// every button, chip and progress bar there follows the same One UI shapes.

// Standard primary action: accent-blue pill with white content.
@Composable
fun PlanningPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 40.dp else 48.dp),
        shape = RoundedCornerShape(Pill),
        contentPadding = PaddingValues(horizontal = if (compact) 16.dp else 20.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink900,
            contentColor = Lime,
            disabledContainerColor = PaperOuter,
            disabledContentColor = InkFaint,
        ),
        elevation = null,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = Body.copy(
                color = if (enabled) Lime else InkFaint,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 14.sp else 15.sp
            )
        )
    }
}

// Secondary text action on a soft pill (e.g. "withdraw", "edit").
@Composable
fun PlanningSoftPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Indigo,
    bg: Color = IndigoSoft,
) {
    Box(
        modifier.heightIn(min = 40.dp)
            .clip(RoundedCornerShape(Pill))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = Body.copy(color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp))
    }
}

// Selectable pill chip used for deadlines, types, categories and mode tabs.
@Composable
fun PlanningChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Pill))
            .background(if (selected) IndigoSoft else PaperOuter)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = Body.copy(
                fontSize = 13.sp,
                color = if (selected) Indigo else InkSoft,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
fun PlanningProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 6) {
    Box(
        modifier.fillMaxWidth().height(height.dp)
            .clip(RoundedCornerShape(Pill))
            .background(PaperOuter)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(Pill))
                .background(color)
        )
    }
}

// Confirm/cancel slots for FormSheet.
@Composable
fun PlanningSheetConfirm(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    PlanningPillButton(text = text, onClick = onClick, enabled = enabled, compact = true)
}

@Composable
fun PlanningSheetCancel(onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = RoundedCornerShape(Pill)) {
        Text(stringResource(R.string.pl_cancel), style = Body.copy(color = InkSoft, fontWeight = FontWeight.SemiBold))
    }
}

// Full-width destructive action placed at the bottom of an edit sheet.
@Composable
fun PlanningDeleteButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(Pill),
        colors = ButtonDefaults.textButtonColors(containerColor = Danger.copy(alpha = 0.08f))
    ) {
        Text(text, style = Body.copy(color = Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp))
    }
}

// Title row above a group of cards: heading + optional subtitle + add pill.
@Composable
fun PlanningSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = H2.copy(fontSize = 17.sp))
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Eyebrow.copy(fontSize = 13.sp))
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            PlanningPillButton(text = actionLabel, onClick = onAction, icon = actionIcon, compact = true)
        }
    }
}

// Empty state inside a white card: icon, one-line explanation, add button.
@Composable
fun PlanningEmptyCard(
    icon: ImageVector,
    text: String,
    actionLabel: String,
    actionIcon: ImageVector? = null,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(White)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(icon, Indigo, IndigoSoft, size = 56.dp, iconSize = 26.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, style = BodyMuted.copy(fontSize = 14.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PlanningPillButton(text = actionLabel, onClick = onAction, icon = actionIcon, compact = true)
    }
}

// Calendar dialog shared by goal deadlines and debt dates.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningDatePickerDialog(initial: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onPick)
                onDismiss()
            }) { Text(stringResource(R.string.debts_date_confirm), style = Body.copy(color = Indigo, fontWeight = FontWeight.Bold)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pl_cancel), style = Body.copy(color = InkSoft)) }
        }
    ) { DatePicker(state = state) }
}

// Read-only date row that opens the calendar. Filled (no border) to match the
// One UI grouped-field look.
@Composable
fun PlanningDateField(label: String, value: Long?, emptyText: String, onPick: (Long) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = Eyebrow.copy(fontSize = 13.sp))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(RadiusMd))
                .background(PaperOuter)
                .clickable { show = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value?.let { Dates.dayLabel(it) } ?: emptyText,
                style = Body.copy(color = if (value != null) Ink else InkSoft, fontSize = 14.sp),
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.CalendarMonth, null, tint = InkSoft, modifier = Modifier.size(18.dp))
        }
    }
    if (show) {
        PlanningDatePickerDialog(initial = value, onDismiss = { show = false }, onPick = onPick)
    }
}

// Integer rendered with Arabic-Indic digits when the UI is Arabic.
@Composable
fun localDigits(n: Int): String = if (isArabicUi()) toArabicIndicDigits(n.toString()) else n.toString()

// Plural string via the current (already-localized) resources. Used instead of
// pluralStringResource to avoid depending on its opt-in status.
@Composable
fun planningPlural(id: Int, count: Int): String =
    LocalContext.current.resources.getQuantityString(id, count, localDigits(count))

fun planningEditableAmount(v: Double): String =
    if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

// Stored value meaning "last day of the month" for a bill's due day. The
// existing Int field is reused: 31 clamps to each month's real length.
const val LAST_DAY_OF_MONTH = 31

/** The due day actually used in the month of [cal], clamped to its length. */
fun planningDueDayIn(day: Int, cal: Calendar): Int =
    day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH))

/**
 * Days from today until the next occurrence of a monthly due [day] (1..31),
 * clamping to each month's real length (day 31 in February = the 28th/29th).
 */
fun planningDaysUntilDue(day: Int, now: Calendar = Calendar.getInstance()): Int {
    val todayDay = now.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
    val dueThisMonth = planningDueDayIn(day, now)
    if (dueThisMonth >= todayDay) return dueThisMonth - todayDay
    val next = (now.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, 1)
    }
    return (daysInMonth - todayDay) + planningDueDayIn(day, next)
}
