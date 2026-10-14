package com.salehilove.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Note
import com.salehilove.core.database.Task
import com.salehilove.core.designsystem.components.AppCard
import com.salehilove.core.designsystem.components.SectionCard
import com.salehilove.core.designsystem.components.StatColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class DashboardUi(
    val dueWords: Int = 0,
    val totalWords: Int = 0,
    val todayTasks: List<Task> = emptyList(),
    val doneTasks: Int = 0,
    val recentNotes: List<Note> = emptyList(),
    val diaryCount: Int = 0,
    val nextReminder: String? = null,
)

/**
 * Home dashboard: prominent instructor portrait banner at the top, dedicated
 * vocabulary section shortcut, today's plan summary, reminders, religious
 * studies, and latest notes — all live data, refreshed on every visit.
 */
@Composable
fun DashboardScreen(
    container: AppContainer,
    openWords: () -> Unit,
    openReview: () -> Unit,
    openPlanner: () -> Unit,
    openNotes: () -> Unit,
    openDiary: () -> Unit,
    openReligious: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(DashboardUi()) }

    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val today = LocalDate.now()
                    val tasks = container.plannerDao.tasksForDate(today)
                    val progress = container.plannerDao.todayProgress(today)
                    DashboardUi(
                        dueWords = container.wordRepo.dueCount(),
                        totalWords = container.wordRepo.totalCount(),
                        todayTasks = tasks.filter { !it.done }.take(4),
                        doneTasks = progress.first,
                        recentNotes = container.notesDao.latest(3),
                        diaryCount = container.diaryRepo.count(),
                        nextReminder = container.plannerDao.enabledReminders()
                            .minByOrNull { it.hour * 60 + it.minute }
                            ?.let { "%02d:%02d — %s".format(it.hour, it.minute, it.label) },
                    )
                }.getOrNull()
            }?.let { ui = it }
        }
    }

    val today = LocalDate.now()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Prominent header banner displaying the verified HAvoice portrait clearly.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1F190F),
                            Color(0xFF14100A),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFC7A45C).copy(alpha = 0.45f),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "SalehiLove ❤️",
                        color = Color(0xFFFFD783),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "علم، عشق و رشد روزانه",
                        color = Color(0xFFFFFBF2),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Surface(
                        color = Color(0x33C7A45C),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            "${PersianCalendar.weekdayName(today)}، ${PersianCalendar.formatDate(today)}",
                            color = Color(0xFFF2E5C5),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Image(
                    painter = painterResource(id = R.drawable.salehi_portrait),
                    contentDescription = "عکس مدرس حاجی احمد صالحی",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .size(124.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFFD9B66C),
                            shape = RoundedCornerShape(18.dp),
                        ),
                )
            }
        }

        // Dedicated Vocabulary section card
        SectionCard(
            title = "واژگان",
            subtitle = "جدول واژه‌های انگلیسی با معنی فارسی، مرور لایتنر و آزمون",
            icon = Icons.Filled.School,
            onClick = openWords,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn(PersianCalendar.toPersianDigits(ui.totalWords.toString()), "کل واژگان")
                StatColumn(PersianCalendar.toPersianDigits(ui.dueWords.toString()), "آماده مرور")
                StatColumn(PersianCalendar.toPersianDigits(ui.diaryCount.toString()), "خاطرات")
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = openWords,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("جدول واژگان")
                }
                OutlinedButton(
                    onClick = openReview,
                    enabled = ui.dueWords > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("شروع مرور")
                }
            }
        }

        // Today's Planner summary card
        SectionCard(
            title = "خلاصه برنامه امروز",
            subtitle = "وظایف روزانه و پیگیری پیشرفت",
            icon = Icons.Filled.Schedule,
            onClick = openPlanner,
        ) {
            if (ui.todayTasks.isEmpty() && ui.doneTasks == 0) {
                Text(
                    "برنامه‌ای برای امروز ثبت نشده است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (ui.todayTasks.isEmpty()) {
                    Text(
                        "همه وظایف امروز انجام شد!",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    ui.todayTasks.forEach { task ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (task.dueTime != null) {
                                Text(
                                    task.dueTime!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Text(
                    "انجام‌شده امروز: ${PersianCalendar.toPersianDigits(ui.doneTasks.toString())} وظیفه",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = openPlanner) {
                    Text("مشاهده برنامه‌ریز")
                }
            }
        }

        // Reminders summary card
        SectionCard(
            title = "یادآورها",
            subtitle = "یادآورهای روزانه صبح، ظهر و شب",
            icon = Icons.Filled.Schedule,
            onClick = openPlanner,
        ) {
            Text(
                ui.nextReminder ?: "یادآوری فعال نیست",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "یادآورها حتی پس از خاموش و روشن شدن گوشی به کار خود ادامه می‌دهند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Religious studies section card
        SectionCard(
            title = "مطالعات دینی",
            subtitle = "نهج‌البلاغه، قرآن کریم و احادیث نبوی همراه با ترجمه و نکات",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = openReligious,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = openReligious) {
                    Text("ورود به مطالعات دینی")
                }
            }
        }

        // Notes & Diary section card
        SectionCard(
            title = "یادداشت‌ها و خاطرات",
            subtitle = "آخرین نوشته‌ها و دفترچه خاطرات رمزنگاری‌شده",
            icon = Icons.Filled.EditNote,
            onClick = openNotes,
        ) {
            if (ui.recentNotes.isEmpty()) {
                Text(
                    "هنوز یادداشتی ثبت نشده است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ui.recentNotes.forEach { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            note.title.ifBlank { "(بی‌عنوان)" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            PersianCalendar.formatDateTime(note.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = openNotes, modifier = Modifier.weight(1f)) {
                    Text("یادداشت‌ها")
                }
                OutlinedButton(onClick = openDiary, modifier = Modifier.weight(1f)) {
                    Text("دفترچه خاطرات")
                }
            }
        }

        // Inspirational footer card
        AppCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "«علم، راهپیمایی آرام به سوی نور است.» — SalehiLove ❤️",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
