package com.salehilove.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Word
import com.salehilove.core.database.repo.WordRepository
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WordsUi(
    val words: List<Word> = emptyList(),
    val topics: List<String> = emptyList(),
    val query: String = "",
    val topic: String? = null,
    val favoritesOnly: Boolean = false,
)

/**
 * Vocabulary manager: full CRUD, search (English + Persian), topic filters,
 * favorites. Every button works against the real repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(
    repo: WordRepository,
    openReview: () -> Unit,
    openQuiz: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(WordsUi()) }
    var editor by remember { mutableStateOf<Word?>(null) }
    var deleting by remember { mutableStateOf<Word?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                runCatching {
                    Triple(
                        repo.search(ui.query, ui.topic, ui.favoritesOnly),
                        repo.topics(),
                        Pair(repo.dueCount(), repo.totalCount()),
                    )
                }
            }
            fresh.getOrNull()?.let { (words, topics, _) ->
                ui = ui.copy(words = words, topics = topics)
            }
            fresh.exceptionOrNull()?.let { error = it.message }
        }
    }

    LaunchedEffect(ui.query, ui.topic, ui.favoritesOnly) { reload() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = Word(english = "", meaning = "") }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن واژه")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { ui = ui.copy(query = it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("جست‌وجو در واژه‌ها و معنی‌ها…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ui.topic == null && !ui.favoritesOnly,
                    onClick = { ui = ui.copy(topic = null, favoritesOnly = false) },
                    label = { Text("همه") },
                )
                FilterChip(
                    selected = ui.favoritesOnly,
                    onClick = { ui = ui.copy(favoritesOnly = !ui.favoritesOnly) },
                    label = { Text("منتخب‌ها") },
                    leadingIcon = { Icon(Icons.Filled.FavoriteBorder, null) },
                )
                ui.topics.forEach { topic ->
                    FilterChip(
                        selected = ui.topic == topic,
                        onClick = { ui = ui.copy(topic = if (ui.topic == topic) null else topic) },
                        label = { Text(topic) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = openReview, enabled = ui.words.isNotEmpty()) { Text("مرور با فاصله زمانی") }
                TextButton(onClick = openQuiz, enabled = ui.words.size >= 4) { Text("آزمون") }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (ui.words.isEmpty()) {
                EmptyState("هنوز واژه‌ای نیست — با دکمه + اولین واژه را اضافه کن")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "واژه انگلیسی",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "معنی فارسی",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                    items(ui.words, key = { it.id }) { word ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            word.english,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                        )
                                        if (word.phonetic.isNotBlank()) {
                                            Text(
                                                word.phonetic,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (word.topic.isNotBlank()) {
                                            Text(
                                                word.topic,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Text(
                                        word.meaning,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.End,
                                    )
                                }
                                if (word.example.isNotBlank() || word.exampleFa.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (word.example.isNotBlank()) {
                                            Text(
                                                word.example,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (word.exampleFa.isNotBlank()) {
                                            Text(
                                                word.exampleFa,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        modifier = Modifier.size(36.dp),
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    runCatching { repo.setFavorite(word.id, !word.favorite) }
                                                }
                                                reload()
                                            }
                                        },
                                    ) {
                                        Icon(
                                            if (word.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = if (word.favorite) "حذف از منتخب‌ها" else "افزودن به منتخب‌ها",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(
                                        modifier = Modifier.size(36.dp),
                                        onClick = { editor = word },
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "ویرایش")
                                    }
                                    IconButton(
                                        modifier = Modifier.size(36.dp),
                                        onClick = { deleting = word },
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editor?.let { editing ->
        WordEditorDialog(
            initial = editing,
            onDismiss = { editor = null },
            onSave = { word ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            if (word.id == 0L) repo.add(word) else repo.update(word)
                        }
                    }
                    result.exceptionOrNull()?.let { error = it.message }
                    editor = null
                    reload()
                }
            },
        )
    }

    deleting?.let { word ->
        ConfirmDialog(
            title = "حذف واژه",
            message = "واژه «${word.english}» برای همیشه حذف شود؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { repo.delete(word.id) } }
                    deleting = null
                    reload()
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun WordEditorDialog(
    initial: Word,
    onDismiss: () -> Unit,
    onSave: (Word) -> Unit,
) {
    var english by remember { mutableStateOf(initial.english) }
    var meaning by remember { mutableStateOf(initial.meaning) }
    var example by remember { mutableStateOf(initial.example) }
    var exampleFa by remember { mutableStateOf(initial.exampleFa) }
    var phonetic by remember { mutableStateOf(initial.phonetic) }
    var pos by remember { mutableStateOf(initial.partOfSpeech) }
    var topic by remember { mutableStateOf(initial.topic) }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "واژه جدید" else "ویرایش واژه") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersianTextField("واژه انگلیسی", english, { english = it }, errorMessage = validation?.takeIf { english.isBlank() })
                PersianTextField("معنی فارسی", meaning, { meaning = it }, errorMessage = validation?.takeIf { meaning.isBlank() })
                PersianTextField("تلفظ (آوانگاری)", phonetic, { phonetic = it })
                PersianTextField("بخش گفتار (اسم/فعل/…)", pos, { pos = it })
                PersianTextField("موضوع", topic, { topic = it })
                PersianTextField("مثال انگلیسی", example, { example = it }, singleLine = false, minLines = 2)
                PersianTextField("ترجمه مثال", exampleFa, { exampleFa = it }, singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (english.isBlank() || meaning.isBlank()) {
                    validation = "واژه و معنی الزامی‌اند"
                    return@TextButton
                }
                onSave(
                    initial.copy(
                        english = english.trim(),
                        meaning = meaning.trim(),
                        example = example.trim(),
                        exampleFa = exampleFa.trim(),
                        phonetic = phonetic.trim(),
                        partOfSpeech = pos.trim(),
                        topic = topic.trim(),
                    ),
                )
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
