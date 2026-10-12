package com.salehilove.feature.learning

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.salehilove.core.database.Book
import com.salehilove.core.database.Chapter
import com.salehilove.core.database.dao.ReligiousDao
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface RelView {
    data object Books : RelView
    data class Chapters(val book: Book) : RelView
    data class Detail(val book: Book, val chapter: Chapter) : RelView
}

/**
 * Religious studies: books → chapters → reading view with Arabic text,
 * Persian translation, explanation, exam summary, key points and page number.
 * Chapters can be marked read/favourite; books and chapters are fully editable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReligiousScreen(dao: ReligiousDao) {
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<RelView>(RelView.Books) }
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadBooks() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { dao.books() } }
                .onSuccess { books = it }
                .onFailure { error = it.message }
        }
    }

    fun loadChapters(book: Book) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { dao.chaptersOf(book.id) } }
                .onSuccess { chapters = it }
        }
    }

    LaunchedEffect(Unit) { loadBooks() }

    when (val v = view) {
        is RelView.Books -> BooksList(
            books = books,
            error = error,
            onOpen = { book -> view = RelView.Chapters(book).also { loadChapters(book) } },
            onAdd = { title, author, description ->
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { dao.insertBook(Book(title = title, author = author, description = description)) } }
                        .onFailure { error = it.message }
                    loadBooks()
                }
            },
            onDelete = { book ->
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { dao.deleteBook(book.id) } }
                    loadBooks()
                }
            },
        )

        is RelView.Chapters -> ChaptersList(
            book = v.book,
            chapters = chapters,
            onBack = { view = RelView.Books },
            onOpen = { view = RelView.Detail(v.book, it) },
            onToggleRead = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterRead(chapter.id, !chapter.isRead) }
                    loadChapters(v.book)
                }
            },
            onToggleFavorite = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterFavorite(chapter.id, !chapter.favorite) }
                    loadChapters(v.book)
                }
            },
            onAdd = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { dao.insertChapter(chapter) }
                    }.exceptionOrNull()?.let { error = it.message }
                    loadChapters(v.book)
                }
            },
            onDelete = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.deleteChapter(chapter.id) }
                    loadChapters(v.book)
                }
            },
            onEdit = { updated ->
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { dao.updateChapter(updated) } }
                    loadChapters(v.book)
                }
            },
        )

        is RelView.Detail -> ChapterDetail(
            book = v.book,
            chapter = v.chapter,
            onBack = { loadChapters(v.book); view = RelView.Chapters(v.book) },
            onToggleRead = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterRead(v.chapter.id, !v.chapter.isRead) }
                    val fresh = withContext(Dispatchers.IO) { dao.chaptersOf(v.book.id) }
                    chapters = fresh
                    fresh.find { it.id == v.chapter.id }?.let { view = RelView.Detail(v.book, it) }
                }
            },
            onToggleFavorite = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterFavorite(v.chapter.id, !v.chapter.favorite) }
                    val fresh = withContext(Dispatchers.IO) { dao.chaptersOf(v.book.id) }
                    chapters = fresh
                    fresh.find { it.id == v.chapter.id }?.let { view = RelView.Detail(v.book, it) }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BooksList(
    books: List<Book>,
    error: String?,
    onOpen: (Book) -> Unit,
    onAdd: (String, String, String) -> Unit,
    onDelete: (Book) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "افزودن کتاب") } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (books.isEmpty()) {
                EmptyState("کتابی ثبت نشده — با + کتاب اول را اضافه کن")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(books, key = { it.id }) { book ->
                        Card(onClick = { onOpen(book) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(book.title, fontWeight = FontWeight.Bold)
                                    if (book.author.isNotBlank()) {
                                        Text(book.author, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { deleting = book }) {
                                    Icon(Icons.Filled.Delete, "حذف کتاب", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        var title by remember { mutableStateOf("") }
        var author by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var validation by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("کتاب جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersianTextField("عنوان کتاب", title, { title = it }, errorMessage = validation)
                    PersianTextField("نویسنده", author, { author = it })
                    PersianTextField("توضیح", description, { description = it }, singleLine = false, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank()) { validation = "عنوان الزامی است"; return@TextButton }
                    onAdd(title.trim(), author.trim(), description.trim()); adding = false
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("انصراف") } },
        )
    }

    deleting?.let { book ->
        ConfirmDialog(
            title = "حذف کتاب",
            message = "کتاب «${book.title}» و همه فصل‌هایش حذف شوند؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = { onDelete(book); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersList(
    book: Book,
    chapters: List<Chapter>,
    onBack: () -> Unit,
    onOpen: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onToggleFavorite: (Chapter) -> Unit,
    onAdd: (Chapter) -> Unit,
    onDelete: (Chapter) -> Unit,
    onEdit: (Chapter) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Chapter?>(null) }
    var deleting by remember { mutableStateOf<Chapter?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "افزودن فصل") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (chapters.isEmpty()) {
                EmptyState("فصلی ثبت نشده — با + فصل جدید اضافه کن")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters, key = { it.id }) { chapter ->
                        Card(onClick = { onOpen(chapter) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${chapter.position}. ${chapter.title}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (chapter.pageNumber != null) {
                                        Text("صفحه ${chapter.pageNumber}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(onClick = { onToggleFavorite(chapter) }) {
                                    Icon(
                                        if (chapter.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        "منتخب",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { onToggleRead(chapter) }) {
                                    Text(if (chapter.isRead) "خوانده✅" else "خوانده", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { editing = chapter }) { Icon(Icons.Filled.Edit, "ویرایش") }
                                IconButton(onClick = { deleting = chapter }) {
                                    Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        ChapterEditorDialog(
            initial = editing ?: Chapter(bookId = book.id, position = chapters.size + 1, title = ""),
            onDismiss = { adding = false; editing = null },
            onSave = { chapter ->
                if (chapter.id == 0L) onAdd(chapter) else onEdit(chapter)
                adding = false; editing = null
            },
        )
    }

    deleting?.let { chapter ->
        ConfirmDialog(
            title = "حذف فصل",
            message = "فصل «${chapter.title}» حذف شود؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = { onDelete(chapter); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ChapterEditorDialog(
    initial: Chapter,
    onDismiss: () -> Unit,
    onSave: (Chapter) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var position by remember { mutableStateOf(initial.position.toString()) }
    var arabic by remember { mutableStateOf(initial.arabicText) }
    var translation by remember { mutableStateOf(initial.translation) }
    var explanation by remember { mutableStateOf(initial.explanation) }
    var summary by remember { mutableStateOf(initial.examSummary) }
    var keyPoints by remember { mutableStateOf(initial.keyPoints.joinToString("\n")) }
    var page by remember { mutableStateOf(initial.pageNumber?.toString() ?: "") }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "فصل جدید" else "ویرایش فصل") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersianTextField("عنوان فصل", title, { title = it }, errorMessage = validation)
                PersianTextField("شماره فصل", position, { position = it })
                PersianTextField("متن عربی", arabic, { arabic = it }, singleLine = false, minLines = 3)
                PersianTextField("ترجمه فارسی", translation, { translation = it }, singleLine = false, minLines = 3)
                PersianTextField("توضیحات", explanation, { explanation = it }, singleLine = false, minLines = 3)
                PersianTextField("خلاصه امتحان", summary, { summary = it }, singleLine = false, minLines = 2)
                PersianTextField("نکات کلیدی (هر نکته در یک خط)", keyPoints, { keyPoints = it }, singleLine = false, minLines = 3)
                PersianTextField("شماره صفحه", page, { page = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { validation = "عنوان الزامی است"; return@TextButton }
                val pos = position.trim().toIntOrNull() ?: (initial.position)
                onSave(
                    initial.copy(
                        title = title.trim(),
                        position = pos,
                        arabicText = arabic.trim(),
                        translation = translation.trim(),
                        explanation = explanation.trim(),
                        examSummary = summary.trim(),
                        keyPoints = keyPoints.lines().map { it.trim() }.filter { it.isNotBlank() },
                        pageNumber = page.trim().toIntOrNull(),
                    ),
                )
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterDetail(
    book: Book,
    chapter: Chapter,
    onBack: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chapter.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (chapter.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "منتخب",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onToggleRead) {
                        Text(if (chapter.isRead) "خوانده شد ✅" else "علامت خوانده‌شده")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "${book.title} — فصل ${chapter.position}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            chapter.pageNumber?.let {
                Spacer(Modifier.height(4.dp))
                Text("صفحه $it", style = MaterialTheme.typography.labelMedium)
            }
            if (chapter.arabicText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("متن عربی", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        chapter.arabicText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (chapter.translation.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("ترجمه", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(chapter.translation)
            }
            if (chapter.explanation.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("توضیحات", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(chapter.explanation)
            }
            if (chapter.examSummary.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("خلاصه امتحان", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(chapter.examSummary, color = MaterialTheme.colorScheme.primary)
            }
            if (chapter.keyPoints.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("نکات کلیدی", fontWeight = FontWeight.Bold)
                chapter.keyPoints.forEach { point ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text("• ", color = MaterialTheme.colorScheme.primary)
                        Text(point)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
