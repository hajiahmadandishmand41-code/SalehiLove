package com.salehilove.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Note
import com.salehilove.core.database.Task
import com.salehilove.core.database.dao.NotesDao
import com.salehilove.core.database.dao.PlannerDao
import com.salehilove.core.database.repo.WordRepository
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
 * Home dashboard: today's plan summary, reminders, vocabulary review status
 * and the latest notes — all live data, refreshed on every visit.
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // Branded hero: the real instructor portrait is a bundled local resource.
        Box(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            Image(
                painter = painterResource(id = R.drawable.salehi_portrait),
                contentDescription = "عکس مدرس",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0x4416120B),
                                Color(0x7716120B),
                                Color(0xCC16120B),
                            ),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "SalehiLove ❤️",
                    color = Color(0xFFFFD783),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "علم، عشق و رشد روزانه",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${PersianCalendar.weekdayName(LocalDate.now())}، ${PersianCalendar.formatDate(LocalDate.now())}",
                    color = Color(0xFFF2E5C5),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        SectionCard(title = "خلاصه برنامه امروز", icon = Icons.Filled.Schedule) {
            if (ui.todayTasks.isEmpty() && ui.doneTasks == 0) {
                Text("برنامه‌ای برای امروز ثبت نشده.", style = MaterialTheme.typography.bodyMedium)
            } else {
                if (ui.todayTasks.isEmpty()) {
                    Text("همه وظایف امروز انجام شد! 🎉", color = MaterialTheme.colorScheme.primary)
                } else {
                    ui.todayTasks.forEach { task ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("• ", color = MaterialTheme.colorScheme.primary)
                            Text(task.title, Modifier.weight(1f))
                            if (task.dueTime != null) {
                                Text(task.dueTime!!, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "انجام‌شده امروز: ${PersianCalendar.toPersianDigits(ui.doneTasks.toString())} وظیفه",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = openPlanner) {
                Text("برنامه‌ریز")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
            }
        }

        SectionCard(title = "یادآورها", icon = Icons.Filled.Schedule) {
            Text(ui.nextReminder ?: "یادآوری فعال نیست")
            Text(
                "یادآورها حتی پس از خاموش/روشن شدن گوشی کار می‌کنند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "مرور واژگان", icon = Icons.Filled.School) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn("واژه‌ها", PersianCalendar.toPersianDigits(ui.totalWords.toString()))
                StatColumn("آماده مرور", PersianCalendar.toPersianDigits(ui.dueWords.toString()))
                StatColumn("خاطرات", PersianCalendar.toPersianDigits(ui.diaryCount.toString()))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = openReview, enabled = ui.dueWords > 0) { Text("شروع مرور") }
                TextButton(onClick = openWords) { Text("واژه‌ها") }
            }
        }

        SectionCard(title = "آخرین یادداشت‌ها", icon = Icons.Filled.EditNote) {
            if (ui.recentNotes.isEmpty()) {
                Text("یادداشتی نوشته نشده.", style = MaterialTheme.typography.bodyMedium)
            } else {
                ui.recentNotes.forEach { note ->
                    Text(
                        note.title.ifBlank { "(بی‌عنوان)" },
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        PersianCalendar.formatDateTime(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = openNotes) { Text("یادداشت‌ها") }
                TextButton(onClick = openDiary) { Text("دفترچه خاطرات") }
                TextButton(onClick = openReligious) { Text("مطالعات دینی") }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.padding(4.dp))
                Text(
                    "«علم، راهپیمایی آرام به سوی نور است.»",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
