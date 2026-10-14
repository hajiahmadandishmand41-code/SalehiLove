package com.salehilove.core.database.dao

import com.salehilove.core.database.Mappers
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.Word
import com.salehilove.core.database.asInt
import com.salehilove.core.database.bool
import com.salehilove.core.database.int
import com.salehilove.core.database.long
import com.salehilove.core.database.string

class WordDao(private val db: RawDatabase) {

    private val cols = "id, english, meaning, example, example_fa, phonetic, part_of_speech, topic, " +
        "box, next_review_at, last_reviewed_at, correct_count, wrong_count, favorite, created_at"

    fun insert(word: Word): Long = db.insert(
        "words",
        mapOf(
            "english" to word.english,
            "meaning" to word.meaning,
            "example" to word.example,
            "example_fa" to word.exampleFa,
            "phonetic" to word.phonetic,
            "part_of_speech" to word.partOfSpeech,
            "topic" to word.topic,
            "box" to word.box,
            "next_review_at" to Mappers.dateTimeToMillis(word.nextReviewAt),
            "last_reviewed_at" to word.lastReviewedAt?.let(Mappers::dateTimeToMillis),
            "correct_count" to word.correctCount,
            "wrong_count" to word.wrongCount,
            "favorite" to word.favorite.asInt(),
            "created_at" to Mappers.dateTimeToMillis(word.createdAt),
        ),
    )

    fun update(word: Word) {
        db.update(
            "words",
            mapOf(
                "english" to word.english,
                "meaning" to word.meaning,
                "example" to word.example,
                "example_fa" to word.exampleFa,
                "phonetic" to word.phonetic,
                "part_of_speech" to word.partOfSpeech,
                "topic" to word.topic,
                "favorite" to word.favorite.asInt(),
            ),
            "id = ?",
            listOf(word.id),
        )
    }

    fun delete(id: Long) = db.delete("words", "id = ?", listOf(id))

    fun getById(id: Long): Word? = db
        .query("SELECT $cols FROM words WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::word)

    fun getAll(orderByNewest: Boolean = true): List<Word> =
        db.query("SELECT $cols FROM words ORDER BY ${if (orderByNewest) "id DESC" else "english ASC"}")
            .map(Mappers::word)

    fun search(query: String, topic: String? = null, favoritesOnly: Boolean = false): List<Word> {
        var sql = """
            SELECT $cols FROM words
            WHERE (
                english LIKE ? OR meaning LIKE ? OR phonetic LIKE ? OR
                part_of_speech LIKE ? OR topic LIKE ? OR example LIKE ? OR example_fa LIKE ?
            )
        """.trimIndent()
        val needle = "%$query%"
        val args = mutableListOf<Any?>(needle, needle, needle, needle, needle, needle, needle)
        if (topic != null) {
            sql += " AND topic = ?"
            args.add(topic)
        }
        if (favoritesOnly) sql += " AND favorite = 1"
        sql += " ORDER BY english ASC"
        return db.query(sql, args).map(Mappers::word)
    }

    fun topics(): List<String> = db
        .query("SELECT DISTINCT topic FROM words WHERE topic != '' ORDER BY topic")
        .mapNotNull { it.string(0) }

    fun dueWords(now: Long, limit: Int = 20): List<Word> = db
        .query(
            "SELECT $cols FROM words WHERE next_review_at <= ? ORDER BY next_review_at ASC LIMIT ${limit.coerceAtLeast(0)}",
            listOf(now),
        )
        .map(Mappers::word)

    fun dueCount(now: Long): Int =
        db.rawQueryForLong("SELECT COUNT(*) FROM words WHERE next_review_at <= ?", listOf(now))?.toInt() ?: 0

    fun totalCount(): Int = db.rawQueryForLong("SELECT COUNT(*) FROM words")?.toInt() ?: 0

    fun setFavorite(id: Long, favorite: Boolean) =
        db.update("words", mapOf("favorite" to favorite.asInt()), "id = ?", listOf(id))

    /** Applies the result of a review round. */
    fun applyReview(id: Long, correct: Boolean, newBox: Int, nextReviewAt: Long, reviewedAt: Long) {
        db.execSQL(
            """UPDATE words SET
                 box = ?,
                 next_review_at = ?,
                 last_reviewed_at = ?,
                 correct_count = correct_count + ?,
                 wrong_count = wrong_count + ?
               WHERE id = ?""",
            listOf(newBox, nextReviewAt, reviewedAt, if (correct) 1 else 0, if (correct) 0 else 1, id),
        )
    }

    fun masteredCount(): Int =
        db.rawQueryForLong("SELECT COUNT(*) FROM words WHERE box >= 5")?.toInt() ?: 0

    fun randomWord(): Word? = db
        .query("SELECT $cols FROM words ORDER BY RANDOM() LIMIT 1")
        .firstOrNull()?.let(Mappers::word)
}
