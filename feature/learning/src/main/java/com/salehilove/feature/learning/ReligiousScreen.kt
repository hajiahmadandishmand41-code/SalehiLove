package com.salehilove.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Book
import com.salehilove.core.database.Chapter
import com.salehilove.core.database.dao.ReligiousDao
import com.salehilove.core.designsystem.components.AppCard
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import com.salehilove.core.designsystem.components.Pill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Religious studies: books → chapters → reading view with Arabic text,
 * Persian translation, explanation, exam summary, key points and page number.
 * UsesAppNav routes (`religious`, `religious/book/{id}`, `religious/chapter/{bookId}/{chapterId}`)
 * when callbacks are provided so the single app TopAppBar and system Back
 * preserve full navigation history without duplicate headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReligiousScreen(
    dao: ReligiousDao,
    bookId: Long? = null,
    chapterId: Long? = null,
    onOpenBook: ((Book) -> Unit)? = null,
    onOpenChapter: ((Book, Chapter) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var internalBook by remember { mutableStateOf<Book?>(null) }
    var internalChapter by remember { mutableStateOf<Chapter?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadBooks() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { dao.books() } }
                .onSuccess { books = it }
                .onFailure { error = it.message }
        }
    }

    fun loadChapters(targetBookId: Long) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { dao.chaptersOf(targetBookId) } }
                .onSuccess { chapters = it }
        }
    }

    LaunchedEffect(bookId, chapterId) {
        loadBooks()
        val activeBookId = bookId ?: internalBook?.id
        if (activeBookId != null) {
            loadChapters(activeBookId)
        }
    }

    val activeBook = if (bookId != null) books.find { it.id == bookId } else internalBook
    val activeChapter = if (chapterId != null) chapters.find { it.id == chapterId } else internalChapter

    when {
        activeBook != null && activeChapter != null -> ChapterDetail(
            book = activeBook,
            chapter = activeChapter,
            onToggleRead = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterRead(activeChapter.id, !activeChapter.isRead) }
                    loadChapters(activeBook.id)
                }
            },
            onToggleFavorite = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterFavorite(activeChapter.id, !activeChapter.favorite) }
                    loadChapters(activeBook.id)
                }
            },
        )

        activeBook != null -> ChaptersList(
            book = activeBook,
            chapters = chapters,
            onOpen = { ch ->
                if (onOpenChapter != null) onOpenChapter(activeBook, ch)
                else internalChapter = ch
            },
            onToggleRead = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterRead(chapter.id, !chapter.isRead) }
                    loadChapters(activeBook.id)
                }
            },
            onToggleFavorite = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setChapterFavorite(chapter.id, !chapter.favorite) }
                    loadChapters(activeBook.id)
                }
            },
            onAdd = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { dao.insertChapter(chapter) }
                    }.exceptionOrNull()?.let { error = it.message }
                    loadChapters(activeBook.id)
                }
            },
            onDelete = { chapter ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.deleteChapter(chapter.id) }
                    loadChapters(activeBook.id)
                }
            },
            onEdit = { updated ->
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { dao.updateChapter(updated) } }
                    loadChapters(activeBook.id)
                }
            },
        )

        else -> BooksList(
            books = books,
            error = error,
            onOpen = { book ->
                if (onOpenBook != null) onOpenBook(book)
                else {
                    internalBook = book
                    loadChapters(book.id)
                }
            },
            onAdd = { title, author, description ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            dao.insertBook(Book(title = title, author = author, description = description))
                        }
                    }.onFailure { error = it.message }
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
    }
}

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
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, "افزودن کتاب")
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
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (books.isEmpty()) {
                EmptyState("کتابی ثبت نشده — با دکمه + کتاب اول را اضافه کن")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(books, key = { it.id }) { book ->
                        AppCard(onClick = { onOpen(book) }) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        book.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (book.author.isNotBlank() && book.author != "—") {
                                        Text(
                                            book.author,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (book.description.isNotBlank()) {
                                        Text(
                                            book.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
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
            title = { Text("کتاب جدید", fontWeight = FontWeight.Bold) },
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
                }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
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

@Composable
private fun ChaptersList(
    book: Book,
    chapters: List<Chapter>,
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
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, "افزودن فصل")
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
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (book.description.isNotBlank()) {
                        Text(
                            book.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            if (chapters.isEmpty()) {
                EmptyState("فصلی ثبت نشده — با دکمه + فصل جدید اضافه کن")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(chapters, key = { it.id }) { chapter ->
                        AppCard(onClick = { onOpen(chapter) }) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        "${PersianCalendar.toPersianDigits((chapter.position + 1).toString())}. ${chapter.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (chapter.pageNumber != null) {
                                        Text(
                                            "صفحه ${PersianCalendar.toPersianDigits(chapter.pageNumber.toString())}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { onToggleFavorite(chapter) }) {
                                    Icon(
                                        if (chapter.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        "منتخب",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                TextButton(onClick = { onToggleRead(chapter) }) {
                                    Text(
                                        if (chapter.isRead) "خوانده‌شده" else "ثبت مطالعه",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (chapter.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { editing = chapter }) {
                                    Icon(Icons.Filled.Edit, "ویرایش", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
            initial = editing ?: Chapter(bookId = book.id, position = chapters.size, title = ""),
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
        title = { Text(if (initial.id == 0L) "فصل جدید" else "ویرایش فصل", fontWeight = FontWeight.Bold) },
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
                val pos = position.trim().toIntOrNull() ?: initial.position
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
            }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ChapterDetail(
    book: Book,
    chapter: Chapter,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    chapter.pageNumber?.let {
                        Pill("صفحه ${PersianCalendar.toPersianDigits(it.toString())}")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (chapter.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "منتخب",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    OutlinedButton(onClick = onToggleRead) {
                        Text(if (chapter.isRead) "خوانده شد" else "ثبت به‌عنوان خوانده‌شده")
                    }
                }
            }
        }

        if (chapter.arabicText.isNotBlank()) {
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "متن عربی",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        chapter.arabicText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (chapter.translation.isNotBlank()) {
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "ترجمه فارسی",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(chapter.translation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (chapter.explanation.isNotBlank()) {
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "توضیحات",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(chapter.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (chapter.examSummary.isNotBlank() || chapter.keyPoints.isNotEmpty()) {
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (chapter.examSummary.isNotBlank()) {
                        Text(
                            "خلاصه امتحانی",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(chapter.examSummary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (chapter.keyPoints.isNotEmpty()) {
                        Text(
                            "نکات کلیدی",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        chapter.keyPoints.forEach { point ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(point, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
