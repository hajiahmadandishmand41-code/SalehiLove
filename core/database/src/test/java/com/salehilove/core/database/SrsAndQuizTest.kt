package com.salehilove.core.database

import com.salehilove.core.common.FixedTimeProvider
import com.salehilove.core.database.learning.QuizGenerator
import com.salehilove.core.database.repo.WordRepository
import com.salehilove.core.database.seed.SeedData
import com.salehilove.core.database.srs.LeitnerSrs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class LeitnerSrsTest {

    private val t = LocalDateTime.of(2026, 10, 12, 9, 0)

    @Test
    fun `correct answers climb boxes with growing intervals`() {
        val b1 = LeitnerSrs.onCorrect(1, t)
        assertEquals(2, b1.newBox)
        assertEquals(t.plusDays(1), b1.nextReviewAt)

        val b3 = LeitnerSrs.onCorrect(3, t)
        assertEquals(4, b3.newBox)
        assertEquals(t.plusDays(7), b3.nextReviewAt)

        // Box 5 stays at 5.
        val b5 = LeitnerSrs.onCorrect(5, t)
        assertEquals(5, b5.newBox)
        assertEquals(t.plusDays(16), b5.nextReviewAt)
    }

    @Test
    fun `wrong answer resets to box 1 with same-day retry`() {
        val w = LeitnerSrs.onWrong(4, t)
        assertEquals(1, w.newBox)
        assertEquals(t.plusMinutes(10), w.nextReviewAt)
    }
}

class WordRepositorySrsTest {

    private lateinit var db: JdbcRawDatabase
    private lateinit var repo: WordRepository
    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)

    @Before
    fun setUp() {
        db = JdbcRawDatabase.inMemory()
        repo = WordRepository(db, FixedTimeProvider(now))
    }

    @Test
    fun `full review cycle through SRS`() {
        val id = repo.add(Word(english = "love", meaning = "عشق"))
        val w = repo.byId(id)!!
        assertEquals(1, w.box)

        // Correct: box 1 -> 2, due tomorrow.
        val afterCorrect = repo.submitReview(id, correct = true)!!
        assertEquals(2, afterCorrect.box)
        assertEquals(now.plusDays(1), afterCorrect.nextReviewAt)
        assertEquals(1, afterCorrect.correctCount)
        // Not due yet.
        assertEquals(0, repo.dueCount())

        // Wrong: back to box 1, due in 10 minutes.
        val afterWrong = repo.submitReview(id, correct = false)!!
        assertEquals(1, afterWrong.box)
        assertEquals(now.plusMinutes(10), afterWrong.nextReviewAt)
        assertEquals(1, afterWrong.wrongCount)
        // Now due again (10 minutes have "passed"? no - fixed clock, due at 09:10 vs now 09:00).
        assertEquals(0, repo.dueCount())

        // A word whose review date passed is due.
        db.execSQL("UPDATE words SET next_review_at = ?", listOf(Mappers.dateTimeToMillis(now.minusMinutes(1))))
        assertEquals(1, repo.dueCount())
        assertEquals(listOf(id), repo.dueForReview().map { it.id })
    }

    @Test
    fun `validation rejects empty words`() {
        try {
            repo.add(Word(english = "", meaning = "معنی"))
            error("expected rejection")
        } catch (expected: IllegalArgumentException) {}
        try {
            repo.add(Word(english = "ok", meaning = " "))
            error("expected rejection")
        } catch (expected: IllegalArgumentException) {}
    }

    @Test
    fun `search across english and persian`() {
        repo.add(Word(english = "heart", meaning = "دل", topic = "بدن"))
        repo.add(Word(english = "soul", meaning = "روح", topic = "معنویت"))
        assertEquals(listOf("heart"), repo.search("heart").map { it.english })
        assertEquals(listOf("soul"), repo.search("روح").map { it.english })
        assertEquals(2, repo.search("", topic = null).size)
        assertEquals(listOf("بدن", "معنویت"), repo.topics())
    }

    @Test
    fun `seedIfEmpty runs once`() {
        val settings = com.salehilove.core.database.dao.SettingsDao(db)
        assertTrue(repo.seedIfEmpty(SeedData.starterWords(now), settings))
        assertEquals(SeedData.starterWords(now).size, repo.totalCount())
        assertTrue(repo.totalCount() >= 40)
        assertFalse(repo.seedIfEmpty(SeedData.starterWords(now), settings))
        assertEquals(SeedData.starterWords(now).size, repo.totalCount())
    }
}

class QuizGeneratorTest {

    private fun words(n: Int) = (1..n).map {
        Word(english = "w$it", meaning = "معنی$it", nextReviewAt = LocalDateTime.now())
    }

    @Test
    fun `quiz builds with exactly 4 options and one correct`() {
        val gen = QuizGenerator(Random(42))
        val quiz = gen.buildQuiz(words(20), questionCount = 10)!!
        assertEquals(10, quiz.size)
        quiz.forEach { q ->
            assertEquals(QuizGenerator.CHOICES, q.options.size)
            assertEquals(q.options.toSet().size, q.options.size) // distinct options
            assertTrue(q.correctIndex in q.options.indices)
            assertEquals(q.word.meaning, q.options[q.correctIndex])
        }
    }

    @Test
    fun `not enough words returns null`() {
        assertNull(QuizGenerator(Random(1)).buildQuiz(words(3)))
        assertNull(QuizGenerator(Random(1)).buildQuiz(emptyList()))
    }

    @Test
    fun `question count capped by pool size`() {
        val quiz = QuizGenerator(Random(7)).buildQuiz(words(5), questionCount = 10)!!
        assertEquals(5, quiz.size)
    }

    @Test
    fun `same seed produces same quiz`() {
        val a = QuizGenerator(Random(99)).buildQuiz(words(30), 10)!!
        val b = QuizGenerator(Random(99)).buildQuiz(words(30), 10)!!
        assertEquals(a.map { it.word.english }, b.map { it.word.english })
        assertEquals(a.map { it.correctIndex }, b.map { it.correctIndex })
    }
}
