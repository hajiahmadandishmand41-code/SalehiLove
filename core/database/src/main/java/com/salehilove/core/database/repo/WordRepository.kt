package com.salehilove.core.database.repo

import com.salehilove.core.common.TimeProvider
import com.salehilove.core.database.Mappers
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.Word
import com.salehilove.core.database.dao.SettingsDao
import com.salehilove.core.database.dao.WordDao
import com.salehilove.core.database.srs.LeitnerSrs
import java.time.LocalDateTime

/**
 * All word-list operations incl. spaced repetition. Pure logic, no Android
 * dependency — testable on the JVM with a JdbcRawDatabase.
 */
class WordRepository(
    private val db: RawDatabase,
    private val timeProvider: TimeProvider,
) {
    private val dao = WordDao(db)

    fun now(): LocalDateTime = timeProvider.now()

    fun add(word: Word): Long {
        require(word.english.isNotBlank()) { "کلمه انگلیسی نمی‌تواند خالی باشد" }
        require(word.meaning.isNotBlank()) { "معنی فارسی نمی‌تواند خالی باشد" }
        return dao.insert(word.copy(nextReviewAt = now().plusMinutes(10)))
    }

    fun update(word: Word) {
        require(word.id != 0L) { "شناسه واژه نامعتبر است" }
        require(word.english.isNotBlank() && word.meaning.isNotBlank()) { "واژه و معنی الزامی‌اند" }
        dao.update(word)
    }

    fun delete(id: Long) = dao.delete(id)

    fun byId(id: Long): Word? = dao.getById(id)

    fun all(): List<Word> = dao.getAll()

    fun search(query: String, topic: String? = null, favoritesOnly: Boolean = false): List<Word> =
        if (query.isBlank() && topic == null && !favoritesOnly) all()
        else dao.search(query.trim(), topic, favoritesOnly)

    fun topics(): List<String> = dao.topics()

    fun dueForReview(limit: Int = 20): List<Word> = dao.dueWords(Mappers.dateTimeToMillis(now()), limit)

    fun dueCount(): Int = dao.dueCount(Mappers.dateTimeToMillis(now()))

    fun totalCount(): Int = dao.totalCount()

    fun masteredCount(): Int = dao.masteredCount()

    fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /** Records a review answer and applies the Leitner box transitions. */
    fun submitReview(wordId: Long, correct: Boolean): Word? {
        val word = dao.getById(wordId) ?: return null
        val reviewedAt = now()
        val outcome = if (correct) {
            LeitnerSrs.onCorrect(word.box, reviewedAt)
        } else {
            LeitnerSrs.onWrong(word.box, reviewedAt)
        }
        dao.applyReview(
            id = wordId,
            correct = correct,
            newBox = outcome.newBox,
            nextReviewAt = Mappers.dateTimeToMillis(outcome.nextReviewAt),
            reviewedAt = Mappers.dateTimeToMillis(reviewedAt),
        )
        return dao.getById(wordId)
    }

    companion object {
        const val KEY_SEEDED = "seed_words_done"
    }

    /** Seeds starter words exactly once (guarded by a settings row). */
    fun seedIfEmpty(seedWords: List<Word>, settings: SettingsDao): Boolean {
        if (settings.getBoolean(KEY_SEEDED, false)) return false
        if (dao.totalCount() > 0) {
            settings.putBoolean(KEY_SEEDED, true)
            return false
        }
        db.transaction {
            seedWords.forEach { dao.insert(it) }
        }
        settings.putBoolean(KEY_SEEDED, true)
        return true
    }
}
