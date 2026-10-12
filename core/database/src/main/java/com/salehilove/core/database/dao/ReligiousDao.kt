package com.salehilove.core.database.dao

import com.salehilove.core.database.Book
import com.salehilove.core.database.Chapter
import com.salehilove.core.database.Mappers
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.asInt

class ReligiousDao(private val db: RawDatabase) {

    // ---- Books ---------------------------------------------------------------

    fun insertBook(book: Book): Long = db.insert(
        "books",
        mapOf(
            "title" to book.title,
            "author" to book.author,
            "description" to book.description,
            "created_at" to Mappers.dateTimeToMillis(book.createdAt),
        ),
    )

    fun updateBook(book: Book) = db.update(
        "books",
        mapOf("title" to book.title, "author" to book.author, "description" to book.description),
        "id = ?",
        listOf(book.id),
    )

    fun deleteBook(id: Long) = db.delete("books", "id = ?", listOf(id))

    fun books(): List<Book> =
        db.query("SELECT id, title, author, description, created_at FROM books ORDER BY id")
            .map(Mappers::book)

    fun bookById(id: Long): Book? = db
        .query("SELECT id, title, author, description, created_at FROM books WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::book)

    // ---- Chapters ------------------------------------------------------------

    private val chapterCols =
        "id, book_id, position, title, arabic_text, translation, explanation, exam_summary, " +
            "key_points, page_number, is_read, favorite, created_at"

    fun insertChapter(chapter: Chapter): Long = db.insert(
        "chapters",
        mapOf(
            "book_id" to chapter.bookId,
            "position" to chapter.position,
            "title" to chapter.title,
            "arabic_text" to chapter.arabicText,
            "translation" to chapter.translation,
            "explanation" to chapter.explanation,
            "exam_summary" to chapter.examSummary,
            "key_points" to Mappers.stringsToJson(chapter.keyPoints),
            "page_number" to chapter.pageNumber,
            "is_read" to chapter.isRead.asInt(),
            "favorite" to chapter.favorite.asInt(),
            "created_at" to Mappers.dateTimeToMillis(chapter.createdAt),
        ),
    )

    fun updateChapter(chapter: Chapter) = db.update(
        "chapters",
        mapOf(
            "title" to chapter.title,
            "arabic_text" to chapter.arabicText,
            "translation" to chapter.translation,
            "explanation" to chapter.explanation,
            "exam_summary" to chapter.examSummary,
            "key_points" to Mappers.stringsToJson(chapter.keyPoints),
            "page_number" to chapter.pageNumber,
        ),
        "id = ?",
        listOf(chapter.id),
    )

    fun deleteChapter(id: Long) = db.delete("chapters", "id = ?", listOf(id))

    fun chaptersOf(bookId: Long): List<Chapter> = db
        .query("SELECT $chapterCols FROM chapters WHERE book_id = ? ORDER BY position, id", listOf(bookId))
        .map(Mappers::chapter)

    fun chapterById(id: Long): Chapter? = db
        .query("SELECT $chapterCols FROM chapters WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::chapter)

    fun setChapterRead(id: Long, read: Boolean) =
        db.update("chapters", mapOf("is_read" to read.asInt()), "id = ?", listOf(id))

    fun setChapterFavorite(id: Long, favorite: Boolean) =
        db.update("chapters", mapOf("favorite" to favorite.asInt()), "id = ?", listOf(id))

    fun chapterCount(bookId: Long): Int =
        db.rawQueryForLong("SELECT COUNT(*) FROM chapters WHERE book_id = ?", listOf(bookId))?.toInt() ?: 0

    fun readCount(bookId: Long): Int =
        db.rawQueryForLong(
            "SELECT COUNT(*) FROM chapters WHERE book_id = ? AND is_read = 1",
            listOf(bookId),
        )?.toInt() ?: 0
}
