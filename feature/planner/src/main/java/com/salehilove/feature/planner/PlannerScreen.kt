package com.salehilove.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.RepeatType
import com.salehilove.core.database.Task
import com.salehilove.core.database.dao.PlannerDao
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class PlannerUi(
    val date: LocalDate = LocalDate.now(),
    val tasks: List<Task> = emptyList(),
    val progress: Pair<Int, Int> = 0 to 0, // done to total
)

/**
 * Daily/weekly planner: pick a date, see its tasks (daily + weekly repeats
 * land automatically on their days), tick them off, add/edit/delete, and see
 * the day's progress. Reminders are managed on a separate tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(dao: PlannerDao, context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(PlannerUi()) }
    var editor by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    var showReminders by remember { mutableStateOf(false) }

    fun reload(date: LocalDate) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { dao.tasksForDate(date) to dao.todayProgress(date) }
            }.onSuccess { (tasks, progress) ->
                ui = ui.copy(date = date, tasks = tasks, progress = progress)
            }
        }
    }

    LaunchedEffect(ui.date) { reload(ui.date) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "وظیفه جدید") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { ui = ui.copy(date = ui.date.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "روز قبل")
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        PersianCalendar.formatDate(ui.date),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        PersianCalendar.weekdayName(ui.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { ui = ui.copy(date = ui.date.plusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "روز بعد")
                }
            }
            Spacer(Modifier.height(8.dp))
            val (done, total) = ui.progress
            if (total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        "${PersianCalendar.toPersianDigits(done.toString())}/${PersianCalendar.toPersianDigits(total.toString())}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = { showReminders = !showReminders }) {
                Text(if (showReminders) "بستن یادآورها ▲" else "مدیریت یادآورها ▼")
            }
            if (showReminders) {
                RemindersPanel(dao, context)
                Spacer(Modifier.height(12.dp))
            }
            if (ui.tasks.isEmpty()) {
                EmptyState("برای این روز وظیفه‌ای ثبت نشده — با + اضافه کن")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.tasks, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = task.done,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                dao.setDone(
                                                    task.id,
                                                    checked,
                                                    System.currentTimeMillis(),
                                                )
                                            }
                                            reload(ui.date)
                                        }
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        task.title,
                                        fontWeight = FontWeight.Bold,
                                        style = if (task.done) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyLarge,
                                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (task.description.isNotBlank()) {
                                        Text(task.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    val repeatLabel = when (task.repeatType) {
                                        RepeatType.DAILY -> "تکرار روزانه"
                                        RepeatType.WEEKLY -> "تکرار هفتگی (${PersianCalendar.weekdayName(task.dueDate ?: ui.date)})"
                                        RepeatType.NONE -> ""
                                    }
                                    if (repeatLabel.isNotBlank()) {
                                        Text(
                                            repeatLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (!task.done && task.dueTime != null) {
                                        Text("ساعت ${task.dueTime}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(onClick = { editor = task }) { Icon(Icons.Filled.Edit, "ویرایش") }
                                IconButton(onClick = { deleting = task }) {
                                    Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editor != null) {
        val existing = editor
        TaskEditorDialog(
            initial = existing ?: Task(title = "", dueDate = ui.date),
            onDismiss = { creating = false; editor = null },
            onSave = { task ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (existing == null) dao.insert(task) else dao.update(task)
                    }
                    creating = false; editor = null
                    reload(ui.date)
                }
            },
        )
    }

    deleting?.let { task ->
        ConfirmDialog(
            title = "حذف وظیفه",
            message = "وظیفه «${task.title}» حذف شود؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.delete(task.id) }
                    deleting = null
                    reload(ui.date)
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TaskEditorDialog(
    initial: Task,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var dueDate by remember { mutableStateOf(initial.dueDate?.toString() ?: "") }
    var dueTime by remember { mutableStateOf(initial.dueTime ?: "") }
    var priority by remember { mutableStateOf(initial.priority) }
    var repeat by remember { mutableStateOf(initial.repeatType) }
    var category by remember { mutableStateOf(initial.category) }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "وظیفه جدید" else "ویرایش وظیفه") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersianTextField("عنوان", title, { title = it }, errorMessage = validation)
                PersianTextField("توضیح", description, { description = it }, singleLine = false, minLines = 2)
                PersianTextField(
                    "تاریخ (مثال ۱۴۰۴-۰۷-۱۲ یا خالی برای بدون تاریخ)",
                    dueDate,
                    { dueDate = it },
                )
                PersianTextField("ساعت (مثال 09:30)", dueTime, { dueTime = it })
                PersianTextField("دسته‌بندی", category, { category = it })
                Text("اولویت", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "عادی", 2 to "مهم", 3 to "خیلی مهم").forEach { (value, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(label) },
                        )
                    }
                }
                Text("تکرار", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RepeatType.NONE to "بدون تکرار", RepeatType.DAILY to "روزانه", RepeatType.WEEKLY to "هفتگی").forEach { (value, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = repeat == value,
                            onClick = { repeat = value },
                            label = { Text(label) },
                        )
                    }
                }
                if (validation != null) Text(validation!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { validation = "عنوان الزامی است"; return@TextButton }
                val parsedDate = dueDate.trim().takeIf { it.isNotBlank() }?.let { raw ->
                    parseFlexibleDate(raw)
                }
                if (dueDate.isNotBlank() && parsedDate == null) {
                    validation = "قالب تاریخ نامعتبر است (۱۴۰۴-۰۷-۱۲)"
                    return@TextButton
                }
                val time = dueTime.trim().takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                if (dueTime.isNotBlank() && time == null) {
                    validation = "قالب ساعت نامعتبر است (09:30)"
                    return@TextButton
                }
                onSave(
                    initial.copy(
                        title = title.trim(),
                        description = description.trim(),
                        dueDate = parsedDate ?: initial.dueDate,
                        dueTime = time,
                        priority = priority,
                        repeatType = repeat,
                        category = category.trim(),
                    ),
                )
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** Accepts Gregorian ISO (2025-10-02) or Jalali (1404-07-10, even with Persian digits). */
internal fun parseFlexibleDate(raw: String): LocalDate? {
    val normalized = PersianCalendar.toEnglishDigits(raw.trim()).replace('/', '-')
    val parts = normalized.split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    return if (y in 1300..1500) {
        runCatching {
            PersianCalendar.toGregorian(PersianCalendar.JalaliDate(y, m, d))
        }.getOrNull()
    } else {
        runCatching { LocalDate.of(y, m, d) }.getOrNull()
    }
}
