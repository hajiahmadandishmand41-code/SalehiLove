package com.salehilove.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.repo.WordRepository
import com.salehilove.core.designsystem.components.EmptyState
import kotlin.random.Random

private data class QuizQuestion(
    val promptWord: String,
    val phonetic: String,
    val correct: String,
    val options: List<String>,
)

private data class QuizUi(
    val questions: List<QuizQuestion> = emptyList(),
    val index: Int = 0,
    val selected: Int? = null,
    val correctCount: Int = 0,
)

/**
 * Multiple-choice quiz: English word → pick the correct Persian meaning from
 * 4 options. Questions are generated from the real word list.
 */
@Composable
fun QuizScreen(
    repo: WordRepository,
    onFinished: () -> Unit,
) {
    var ui by remember { mutableStateOf(QuizUi()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val all = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { repo.all() }.getOrDefault(emptyList())
        }
        ui = if (all.size >= 4) {
            val rng = Random(all.size * 31L + System.currentTimeMillis() % 1000)
            val picked = all.shuffled(rng).take(10.coerceAtMost(all.size))
            QuizUi(
                questions = picked.mapNotNull { w ->
                    val distractors = all.filter { it.id != w.id }.shuffled(rng).take(3).map { it.meaning }
                    if (distractors.size < 3) return@mapNotNull null
                    QuizQuestion(
                        promptWord = w.english,
                        phonetic = w.phonetic,
                        correct = w.meaning,
                        options = (distractors + w.meaning).shuffled(rng),
                    )
                },
            )
        } else {
            QuizUi()
        }
        loaded = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!loaded) {
            Spacer(Modifier.height(32.dp)); Text("در حال ساخت آزمون…"); return@Column
        }
        if (ui.questions.isEmpty()) {
            Spacer(Modifier.height(32.dp))
            EmptyState("برای آزمون حداقل ۴ واژه لازم است.\nواژه‌های بیشتری اضافه کن!")
            OutlinedButton(onClick = onFinished) { Text("بازگشت") }
            return@Column
        }

        val q = ui.questions.getOrNull(ui.index)
        if (q == null) {
            Spacer(Modifier.height(48.dp))
            Text("پایان آزمون 🎓", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val pct = if (ui.questions.isEmpty()) 0 else ui.correctCount * 100 / ui.questions.size
            Text(
                "امتیاز: ${PersianCalendar.toPersianDigits(ui.correctCount.toString())} از " +
                    PersianCalendar.toPersianDigits(ui.questions.size.toString()) +
                    " (${PersianCalendar.toPersianDigits(pct.toString())}٪)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinished) { Text("بازگشت به واژه‌ها") }
            return@Column
        }

        LinearProgressIndicator(progress = { ui.index.toFloat() / ui.questions.size }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text("سؤال ${PersianCalendar.toPersianDigits((ui.index + 1).toString())} از ${PersianCalendar.toPersianDigits(ui.questions.size.toString())}")
        Spacer(Modifier.height(16.dp))
        Text(q.promptWord, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (q.phonetic.isNotBlank()) Text(q.phonetic, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))

        q.options.forEachIndexed { i, option ->
            val chosen = ui.selected == i
            val isCorrectOption = option == q.correct
            val container = when {
                ui.selected == null -> CardDefaults.cardColors()
                chosen && isCorrectOption -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
                chosen && !isCorrectOption -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
                ui.selected != null && isCorrectOption -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
                else -> CardDefaults.cardColors()
            }
            Card(
                onClick = { if (ui.selected == null) ui = ui.copy(selected = i) },
                colors = container,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    option,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (ui.selected != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (q.options[ui.selected!!] == q.correct) "درست بود ✅" else "پاسخ درست: ${q.correct}",
                color = if (q.options[ui.selected!!] == q.correct) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.index + 1 < ui.questions.size) {
                    Button(
                        onClick = {
                            val wasCorrect = q.options[ui.selected!!] == q.correct
                            ui = ui.copy(
                                index = ui.index + 1,
                                selected = null,
                                correctCount = ui.correctCount + if (wasCorrect) 1 else 0,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("سؤال بعد") }
                } else {
                    Button(
                        onClick = {
                            val wasCorrect = q.options[ui.selected!!] == q.correct
                            ui = ui.copy(
                                index = ui.index + 1,
                                selected = null,
                                correctCount = ui.correctCount + if (wasCorrect) 1 else 0,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("دیدن نتیجه") }
                }
                TextButton(onClick = onFinished) { Text("پایان") }
            }
        }
    }
}
