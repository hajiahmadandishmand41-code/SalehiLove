package com.salehilove.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.salehilove.core.database.Reminder
import com.salehilove.core.database.dao.PlannerDao
import com.salehilove.core.designsystem.components.AppCard
import com.salehilove.core.designsystem.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val defaultReminders = listOf(
    Reminder(label = "برنامه صبحگاهی", type = "morning", hour = 7, minute = 0),
    Reminder(label = "مرور نیم‌روز", type = "noon", hour = 13, minute = 0),
    Reminder(label = "جمع‌بندی شب", type = "night", hour = 21, minute = 0),
)

/**
 * Reminder management: default morning/noon/night reminders are created once,
 * then each can be retimed, enabled/disabled (alarm actually cancelled /
 * rescheduled) or removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersPanel(dao: PlannerDao, context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    fun reload() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { dao.reminders() } }
                .onSuccess { list ->
                    reminders = list
                    if (list.isEmpty()) {
                        withContext(Dispatchers.IO) {
                            defaultReminders.forEach { dao.insertReminder(it) }
                        }
                        reminders = withContext(Dispatchers.IO) { dao.reminders() }
                    }
                }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reminders.isEmpty()) {
            EmptyState("یادآوری تعریف نشده")
        } else {
            reminders.forEach { reminder ->
                AppCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(reminder.label, fontWeight = FontWeight.Bold)
                            Text(
                                "%02d:%02d".format(reminder.hour, reminder.minute),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(onClick = { editing = reminder }) { Text("تغییر ساعت") }
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        dao.updateReminder(reminder.copy(enabled = enabled))
                                        if (enabled) {
                                            ReminderScheduler.scheduleOne(context, reminder = reminder)
                                        } else {
                                            ReminderScheduler.cancel(context, reminder)
                                        }
                                    }
                                    reload()
                                }
                            },
                        )
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    dao.deleteReminder(reminder.id)
                                    ReminderScheduler.cancel(context, reminder)
                                }
                                reload()
                            }
                        }) {
                            Icon(Icons.Filled.Delete, "حذف یادآور", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Text(
                "یادآورها با خاموش/روشن شدن گوشی و تغییر ساعت خودکار دوباره زمان‌بندی می‌شوند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { reminder ->
        val pickerState = rememberTimePickerState(
            initialHour = reminder.hour,
            initialMinute = reminder.minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { editing = null }) {
            AppCard(containerColor = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("ساعت یادآور «${reminder.label}»", fontWeight = FontWeight.Bold)
                    TimePicker(state = pickerState)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = null }) { Text("انصراف") }
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val updated = reminder.copy(
                                        hour = pickerState.hour,
                                        minute = pickerState.minute,
                                        enabled = true,
                                    )
                                    dao.updateReminder(updated)
                                    ReminderScheduler.cancel(context, reminder)
                                    if (updated.enabled) {
                                        ReminderScheduler.scheduleOne(context, reminder = updated)
                                    }
                                }
                                editing = null
                                reload()
                            }
                        }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
