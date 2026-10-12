package com.salehilove.core.database.srs

import java.time.Duration
import java.time.LocalDateTime

/**
 * Leitner-style spaced repetition with 5 boxes.
 *
 *   box 1 -> review after 10 minutes (still "learning" today)
 *   box 2 -> 1 day
 *   box 3 -> 3 days
 *   box 4 -> 7 days
 *   box 5 -> 16 days (mastered; still cycles)
 *
 * A correct answer promotes the card one box; a wrong answer sends it back to
 * box 1. Pure logic with an injected clock so it is fully testable.
 */
object LeitnerSrs {

    data class ReviewOutcome(val newBox: Int, val nextReviewAt: LocalDateTime)

    private val intervals = listOf(
        Duration.ofMinutes(10),
        Duration.ofDays(1),
        Duration.ofDays(3),
        Duration.ofDays(7),
        Duration.ofDays(16),
    )

    const val MAX_BOX = 5

    fun onCorrect(currentBox: Int, reviewedAt: LocalDateTime): ReviewOutcome {
        val box = (currentBox + 1).coerceAtMost(MAX_BOX)
        return ReviewOutcome(box, reviewedAt.plus(intervals[box - 1]))
    }

    fun onWrong(currentBox: Int, reviewedAt: LocalDateTime): ReviewOutcome {
        val box = 1
        return ReviewOutcome(box, reviewedAt.plus(intervals[box - 1]))
    }

    fun intervalFor(box: Int): Duration = intervals[(box - 1).coerceIn(0, intervals.size - 1)]
}
