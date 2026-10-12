package com.salehilove.feature.personal

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Note
import com.salehilove.core.database.dao.NotesDao
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NotesUi(
    val notes: List<Note> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val categories: List<String> = emptyList(),
)

/**
 * Notes: CRUD + search (title/content/tags) + category filters + pin +
 * tags. Everything hits [NotesDao] for real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(dao: NotesDao) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(NotesUi()) }
    var editor by remember { mutableStateOf<Note?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }

    fun reload() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dao.search(ui.query, ui.category) to dao.categories()
                }
            }.onSuccess { (notes, categories) -> ui = ui.copy(notes = notes, categories = categories) }
        }
    }

    LaunchedEffect(ui.query, ui.category) { reload() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = Note(title = "", content = "") }) {
                Icon(Icons.Filled.Add, "یادداشت جدید")
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
                placeholder = { Text("جست‌وجو در عنوان، متن و برچسب‌ها…") },
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
                    selected = ui.category == null,
                    onClick = { ui = ui.copy(category = null) },
                    label = { Text("همه") },
                )
                ui.categories.forEach { cat ->
                    FilterChip(
                        selected = ui.category == cat,
                        onClick = { ui = ui.copy(category = if (ui.category == cat) null else cat) },
                        label = { Text(cat) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (ui.notes.isEmpty()) {
                EmptyState("یادداشتی نیست — با + یادداشت جدید بنویس")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(ui.notes, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.pinned) {
                                            Icon(
                                                Icons.Filled.PushPin, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 4.dp),
                                            )
                                        }
                                        Text(note.title.ifBlank { "(بی‌عنوان)" }, fontWeight = FontWeight.Bold)
                                    }
                                    if (note.content.isNotBlank()) {
                                        Text(
                                            note.content.take(80) + if (note.content.length > 80) "…" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (note.tags.isNotEmpty()) {
                                        Text(
                                            note.tags.joinToString("، ") { "#$it" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        PersianCalendar.formatDateTime(note.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { dao.update(note.copy(pinned = !note.pinned)) }
                                        reload()
                                    }
                                }) {
                                    Icon(
                                        if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        "سنجاق",
                                    )
                                }
                                IconButton(onClick = { editor = note }) { Icon(Icons.Filled.Edit, "ویرایش") }
                                IconButton(onClick = { deleting = note }) {
                                    Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editor?.let { editing ->
        NoteEditorDialog(
            initial = editing,
            onDismiss = { editor = null },
            onSave = { note ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (note.id == 0L) dao.insert(note) else dao.update(note)
                    }
                    editor = null
                    reload()
                }
            },
        )
    }

    deleting?.let { note ->
        ConfirmDialog(
            title = "حذف یادداشت",
            message = "یادداشت «${note.title.ifBlank { "(بی‌عنوان)" }}» حذف شود؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.delete(note.id) }
                    deleting = null
                    reload()
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun NoteEditorDialog(
    initial: Note,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var content by remember { mutableStateOf(initial.content) }
    var category by remember { mutableStateOf(initial.category) }
    var tags by remember { mutableStateOf(initial.tags.joinToString("، ")) }
    var color by remember { mutableStateOf(initial.color) }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "یادداشت جدید" else "ویرایش یادداشت") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersianTextField("عنوان", title, { title = it })
                PersianTextField("متن", content, { content = it }, singleLine = false, minLines = 5)
                PersianTextField("دسته‌بندی", category, { category = it })
                PersianTextField("برچسب‌ها (با «،» جدا کن)", tags, { tags = it })
                Text("رنگ کارت", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "" to "پیش‌فرض",
                        "green" to "سبز",
                        "gold" to "طلایی",
                        "cream" to "کرم",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = color == value,
                            onClick = { color = value },
                            label = { Text(label) },
                        )
                    }
                }
                if (validation != null) {
                    Text(validation!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank() && content.isBlank()) {
                    validation = "عنوان یا متن را بنویس"
                    return@TextButton
                }
                val now = java.time.LocalDateTime.now()
                onSave(
                    initial.copy(
                        title = title.trim(),
                        content = content.trim(),
                        category = category.trim(),
                        tags = tags.split('،', ',').map { it.trim() }.filter { it.isNotBlank() },
                        color = color,
                        createdAt = if (initial.id == 0L) now else initial.createdAt,
                        updatedAt = now,
                    ),
                )
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
