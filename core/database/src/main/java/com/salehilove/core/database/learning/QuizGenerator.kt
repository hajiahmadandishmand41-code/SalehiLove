package com.salehilove.core.database.learning

import com.salehilove.core.database.Word
import kotlin.random.Random

/**
 * Builds multiple-choice vocabulary quizzes from real word entries.
 * Options are the correct Persian meaning plus three distractors sampled from
 * the same pool; a seeded [Random] makes the whole generator deterministic and
 * therefore unit-testable.
 */
class QuizGenerator(private val random: Random = Random.Default) {

    data class Question(
        val word: Word,
        val options: List<String>,
        val correctIndex: Int,
    )

    /** Returns null when there are not enough words to build a quiz. */
    fun buildQuiz(words: List<Word>, questionCount: Int = 10): List<Question>? {
        if (words.size < MIN_POOL) return null
        val meanings = words.map { it.meaning }.distinct()
        if (meanings.size < MIN_POOL) return null
        val shuffled = words.shuffled(random)
        val count = questionCount.coerceAtMost(shuffled.size)
        return List(count) { i ->
            val word = shuffled[i]
            val distractors = meanings.filter { it != word.meaning }.shuffled(random).take(CHOICES - 1)
            val options = (distractors + word.meaning).shuffled(random)
            Question(word, options, options.indexOf(word.meaning))
        }
    }

    companion object {
        const val CHOICES = 4
        const val MIN_POOL = 4
    }
}
