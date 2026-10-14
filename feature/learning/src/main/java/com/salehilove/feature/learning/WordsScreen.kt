package com.salehilove.feature.learning

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Word
import com.salehilove.core.database.repo.WordRepository
import com.salehilove.core.designsystem.components.AppCard
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import com.salehilove.core.designsystem.components.StatColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WordsUi(
    val words: List<Word> = emptyList(),
    val topics: List<String> = emptyList(),
    val query: String = "",
    val topic: String? = null,
    val favoritesOnly: Boolean = false,
    val dueCount: Int = 0,
    val totalCount: Int = 0,
)

/**
 * Dedicated Vocabulary section: displays existing and user-added vocabulary in a
 * clean, aligned table with English words, phonetics/part-of-speech, Persian
 * meanings, topics, examples, and full CRUD / Leitner review / Quiz actions.
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
            fresh.getOrNull()?.let { (words, topics, counts) ->
                ui = ui.copy(
                    words = words,
                    topics = topics,
                    dueCount = counts.first,
                    totalCount = counts.second,
                )
            }
            fresh.exceptionOrNull()?.let { error = it.message }
        }
    }

    LaunchedEffect(ui.query, ui.topic, ui.favoritesOnly) { reload() }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editor = Word(english = "", meaning = "") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن واژه")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary & practice actions card
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatColumn(
                            value = PersianCalendar.toPersianDigits(ui.totalCount.toString()),
                            caption = "کل واژگان",
                        )
                        StatColumn(
                            value = PersianCalendar.toPersianDigits(ui.dueCount.toString()),
                            caption = "آماده مرور",
                        )
                        StatColumn(
                            value = PersianCalendar.toPersianDigits(ui.words.size.toString()),
                            caption = "نمایش در جدول",
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = openReview,
                            enabled = ui.totalCount > 0,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("مرور لایتنر")
                        }
                        OutlinedButton(
                            onClick = openQuiz,
                            enabled = ui.totalCount >= 4,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("آزمون چهارگزینه‌ای")
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = ui.query,
                onValueChange = { ui = ui.copy(query = it) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("جست‌وجو در واژه، معنی، تلفظ، موضوع و مثال…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            // Topic & favorite filter chips
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
                    leadingIcon = {
                        Icon(
                            if (ui.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                ui.topics.forEach { topic ->
                    FilterChip(
                        selected = ui.topic == topic,
                        onClick = { ui = ui.copy(topic = if (ui.topic == topic) null else topic) },
                        label = { Text(topic) },
                    )
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (ui.words.isEmpty()) {
                EmptyState("واژه‌ای مطابق فیلتر فعلی یافت نشد — با دکمه + واژه جدید اضافه کن")
            } else {
                // Table header with fixed aligned column weights
                AppCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "واژه انگلیسی",
                            modifier = Modifier.weight(1.1f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "معنی فارسی",
                            modifier = Modifier.weight(1.1f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "عملیات",
                            modifier = Modifier.width(104.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Table rows using the same card style and aligned column weights
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(ui.words, key = { it.id }) { word ->
                        AppCard {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Column 1: English word + phonetic / POS
                                    Column(
                                        Modifier.weight(1.1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            word.english,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        val meta = listOfNotNull(
                                            word.phonetic.takeIf { it.isNotBlank() }?.let { "/$it/" },
                                            word.partOfSpeech.takeIf { it.isNotBlank() },
                                        ).joinToString(" • ")
                                        if (meta.isNotBlank()) {
                                            Text(
                                                meta,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    // Column 2: Persian meaning + topic
                                    Column(
                                        Modifier.weight(1.1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            word.meaning,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        if (word.topic.isNotBlank()) {
                                            Text(
                                                word.topic,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    // Column 3: Aligned actions
                                    Row(
                                        modifier = Modifier.width(104.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconButton(
                                            modifier = Modifier.size(32.dp),
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
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(32.dp),
                                            onClick = { editor = word },
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "ویرایش",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(32.dp),
                                            onClick = { deleting = word },
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "حذف",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }

                                if (word.example.isNotBlank() || word.exampleFa.isNotBlank()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (word.example.isNotBlank()) {
                                            Text(
                                                word.example,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (word.exampleFa.isNotBlank()) {
                                            Text(
                                                word.exampleFa,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        title = { Text(if (initial.id == 0L) "واژه جدید" else "ویرایش واژه", fontWeight = FontWeight.Bold) },
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
            }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
