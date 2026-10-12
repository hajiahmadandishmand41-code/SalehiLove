package com.salehilove.core.database.dao

import com.salehilove.core.database.DiaryEntry
import com.salehilove.core.database.Mappers
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.string

/**
 * Diary DAO. The `content_enc` column only ever receives AES-GCM ciphertext —
 * plaintext diary bodies never touch the database.
 */
class DiaryDao(private val db: RawDatabase) {

    private val cols = "id, title, content_enc, mood, entry_date, photo_paths, created_at, updated_at"

    fun insert(entry: DiaryEntry): Long = db.insert(
        "diary_entries",
        mapOf(
            "title" to entry.title,
            "content_enc" to entry.contentEnc,
            "mood" to entry.mood,
            "entry_date" to entry.entryDate.toString(),
            "photo_paths" to Mappers.stringsToJson(entry.photoPaths),
            "created_at" to Mappers.dateTimeToMillis(entry.createdAt),
            "updated_at" to Mappers.dateTimeToMillis(entry.updatedAt),
        ),
    )

    fun update(entry: DiaryEntry) = db.update(
        "diary_entries",
        mapOf(
            "title" to entry.title,
            "content_enc" to entry.contentEnc,
            "mood" to entry.mood,
            "entry_date" to entry.entryDate.toString(),
            "photo_paths" to Mappers.stringsToJson(entry.photoPaths),
            "updated_at" to Mappers.dateTimeToMillis(entry.updatedAt),
        ),
        "id = ?",
        listOf(entry.id),
    )

    fun delete(id: Long) = db.delete("diary_entries", "id = ?", listOf(id))

    fun byId(id: Long): DiaryEntry? = db
        .query("SELECT $cols FROM diary_entries WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::diaryEntry)

    fun all(): List<DiaryEntry> = db
        .query("SELECT $cols FROM diary_entries ORDER BY entry_date DESC, id DESC")
        .map(Mappers::diaryEntry)

    fun searchTitles(query: String): List<DiaryEntry> = db
        .query(
            "SELECT $cols FROM diary_entries WHERE title LIKE ? ORDER BY entry_date DESC, id DESC",
            listOf("%$query%"),
        )
        .map(Mappers::diaryEntry)

    fun count(): Int = db.rawQueryForLong("SELECT COUNT(*) FROM diary_entries")?.toInt() ?: 0

    fun latest(limit: Int): List<DiaryEntry> = db
        .query("SELECT $cols FROM diary_entries ORDER BY updated_at DESC LIMIT ${limit.coerceAtLeast(0)}")
        .map(Mappers::diaryEntry)

    fun dates(): List<String> = db
        .query("SELECT DISTINCT entry_date FROM diary_entries ORDER BY entry_date DESC")
        .mapNotNull { it.string(0) }
}
