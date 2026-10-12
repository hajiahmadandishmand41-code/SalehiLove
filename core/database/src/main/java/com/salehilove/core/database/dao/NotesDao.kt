package com.salehilove.core.database.dao

import com.salehilove.core.database.Mappers
import com.salehilove.core.database.Note
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.asInt

class NotesDao(private val db: RawDatabase) {

    private val cols = "id, title, content, category, tags, color, pinned, created_at, updated_at"

    fun insert(note: Note): Long = db.insert(
        "notes",
        mapOf(
            "title" to note.title,
            "content" to note.content,
            "category" to note.category,
            "tags" to Mappers.stringsToJson(note.tags),
            "color" to note.color,
            "pinned" to note.pinned.asInt(),
            "created_at" to Mappers.dateTimeToMillis(note.createdAt),
            "updated_at" to Mappers.dateTimeToMillis(note.updatedAt),
        ),
    )

    fun update(note: Note) = db.update(
        "notes",
        mapOf(
            "title" to note.title,
            "content" to note.content,
            "category" to note.category,
            "tags" to Mappers.stringsToJson(note.tags),
            "color" to note.color,
            "pinned" to note.pinned.asInt(),
            "updated_at" to Mappers.dateTimeToMillis(note.updatedAt),
        ),
        "id = ?",
        listOf(note.id),
    )

    fun delete(id: Long) = db.delete("notes", "id = ?", listOf(id))

    fun byId(id: Long): Note? = db
        .query("SELECT $cols FROM notes WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::note)

    fun all(): List<Note> = db
        .query("SELECT $cols FROM notes ORDER BY pinned DESC, updated_at DESC")
        .map(Mappers::note)

    fun search(query: String, category: String? = null, tag: String? = null): List<Note> {
        var sql = "SELECT $cols FROM notes WHERE (title LIKE ? OR content LIKE ? OR tags LIKE ?)"
        val args = mutableListOf<Any?>("%$query%", "%$query%", "%\"$query\"%")
        if (category != null) {
            sql += " AND category = ?"
            args.add(category)
        }
        if (tag != null) {
            sql += " AND tags LIKE ?"
            args.add("%\"$tag\"%")
        }
        sql += " ORDER BY pinned DESC, updated_at DESC"
        return db.query(sql, args).map(Mappers::note)
    }

    fun categories(): List<String> = db
        .query("SELECT DISTINCT category FROM notes WHERE category != '' ORDER BY category")
        .mapNotNull { it[0] as? String }

    fun allTags(): List<String> {
        val rows = db.query("SELECT tags FROM notes")
        val set = linkedSetOf<String>()
        rows.forEach { row ->
            Mappers.jsonArrayToStrings(row[0] as? String ?: "[]").forEach(set::add)
        }
        return set.toList()
    }

    fun latest(limit: Int): List<Note> = db
        .query("SELECT $cols FROM notes ORDER BY updated_at DESC LIMIT ${limit.coerceAtLeast(0)}")
        .map(Mappers::note)

    fun count(): Int = db.rawQueryForLong("SELECT COUNT(*) FROM notes")?.toInt() ?: 0
}
