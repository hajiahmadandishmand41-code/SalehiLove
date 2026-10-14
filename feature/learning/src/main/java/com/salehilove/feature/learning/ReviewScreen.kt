package com.salehilove.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Word
import com.salehilove.core.database.repo.WordRepository
import com.salehilove.core.designsystem.components.AppCard
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.StatColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReviewUi(
    val queue: List<Word> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val correct: Int = 0,
    val wrong: Int = 0,
)

/**
 * Spaced-repetition review session: shows due words one by one; the user
 * reveals the meaning and grades themselves. Every answer is persisted via
 * [WordRepository.submitReview] (Leitner boxes).
 */
@Composable
fun ReviewScreen(
    repo: WordRepository,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(ReviewUi()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val due = withContext(Dispatchers.IO) { runCatching { repo.dueForReview(limit = 20) }.getOrDefault(emptyList()) }
        ui = ui.copy(queue = due)
        loaded = true
    }

    val total = ui.queue.size
    val word = ui.queue.getOrNull(ui.index)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!loaded) {
            Spacer(Modifier.weight(1f))
            Text("در حال بارگذاری…")
            Spacer(Modifier.weight(1f))
            return@Column
        }

        if (total == 0) {
            Spacer(Modifier.weight(1f))
            EmptyState("واژه‌ای برای مرور نیست!\nهمه واژه‌ها مرور شده‌اند یا هنوز سررسید نشده‌اند.")
            OutlinedButton(onClick = onFinished) { Text("بازگشت") }
            Spacer(Modifier.weight(1f))
            return@Column
        }

        if (word == null) {
            Spacer(Modifier.weight(1f))
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "پایان جلسه مرور",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatColumn(PersianCalendar.toPersianDigits(ui.correct.toString()), "پاسخ درست")
                        StatColumn(PersianCalendar.toPersianDigits(ui.wrong.toString()), "پاسخ نادرست")
                    }
                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                        Text("بازگشت به واژگان")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            return@Column
        }

        LinearProgressIndicator(
            progress = { ui.index.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "واژه ${PersianCalendar.toPersianDigits((ui.index + 1).toString())} از ${PersianCalendar.toPersianDigits(total.toString())}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        AppCard {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(word.english, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (word.phonetic.isNotBlank()) {
                    Text("/${word.phonetic}/", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (word.partOfSpeech.isNotBlank()) {
                    Text(word.partOfSpeech, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                if (ui.revealed) {
                    Text(
                        word.meaning,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (word.example.isNotBlank()) {
                        Text(word.example, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (word.exampleFa.isNotBlank()) {
                        Text(
                            word.exampleFa,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "معنی فارسی را به خاطر بیاور و سپس نمایش بده.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))

        if (!ui.revealed) {
            Button(
                onClick = { ui = ui.copy(revealed = true) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("نمایش معنی فارسی") }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { repo.submitReview(word.id, correct = false) } }
                        }
                        ui = ui.copy(index = ui.index + 1, revealed = false, wrong = ui.wrong + 1)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("نمی‌دانستم") }
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { repo.submitReview(word.id, correct = true) } }
                        }
                        ui = ui.copy(index = ui.index + 1, revealed = false, correct = ui.correct + 1)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("بلد بودم") }
            }
            OutlinedButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("پایان جلسه") }
        }
    }
}
