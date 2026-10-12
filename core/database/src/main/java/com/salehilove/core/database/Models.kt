package com.salehilove.core.database

import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

data class Word(
    val id: Long = 0,
    val english: String,
    val meaning: String,
    val example: String = "",
    val exampleFa: String = "",
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val topic: String = "",
    val box: Int = 1,
    val nextReviewAt: LocalDateTime = LocalDateTime.now(),
    val lastReviewedAt: LocalDateTime? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val favorite: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val description: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class Chapter(
    val id: Long = 0,
    val bookId: Long,
    val position: Int = 0,
    val title: String,
    val arabicText: String = "",
    val translation: String = "",
    val explanation: String = "",
    val examSummary: String = "",
    val keyPoints: List<String> = emptyList(),
    val pageNumber: Int? = null,
    val isRead: Boolean = false,
    val favorite: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val color: String = "",
    val pinned: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
)

/** Diary entries keep their body encrypted; this model carries it as a blob. */
data class DiaryEntry(
    val id: Long = 0,
    val title: String = "",
    val contentEnc: ByteArray,
    val mood: String = "",
    val entryDate: LocalDate,
    val photoPaths: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
)

enum class RepeatType { NONE, DAILY, WEEKLY;

    companion object {
        fun from(raw: String?): RepeatType = when (raw) {
            "daily" -> DAILY
            "weekly" -> WEEKLY
            else -> NONE
        }
    }
}

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: String? = null,
    val priority: Int = 1,
    val done: Boolean = false,
    val doneAt: LocalDateTime? = null,
    val repeatType: RepeatType = RepeatType.NONE,
    val weekday: Int? = null,
    val category: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class Reminder(
    val id: Long = 0,
    val label: String,
    val type: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
)

data class Album(
    val id: Long = 0,
    val name: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class Photo(
    val id: Long = 0,
    val albumId: Long,
    val filePath: String,
    val caption: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

// ---------------------------------------------------------------------------
// Row mappers (shared by DAOs and the backup serializer)
// ---------------------------------------------------------------------------

object Mappers {

    fun jsonArrayToStrings(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { arr.getString(it) }
    }

    fun stringsToJson(list: List<String>): String {
        val arr = JSONArray()
        list.forEach(arr::put)
        return arr.toString()
    }

    fun millisToDateTime(millis: Long?): LocalDateTime? =
        millis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }

    fun dateTimeToMillis(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun word(row: Array<Any?>): Word = Word(
        id = row.long(0) ?: 0,
        english = row.string(1).orEmpty(),
        meaning = row.string(2).orEmpty(),
        example = row.string(3).orEmpty(),
        exampleFa = row.string(4).orEmpty(),
        phonetic = row.string(5).orEmpty(),
        partOfSpeech = row.string(6).orEmpty(),
        topic = row.string(7).orEmpty(),
        box = row.int(8) ?: 1,
        nextReviewAt = millisToDateTime(row.long(9)) ?: LocalDateTime.now(),
        lastReviewedAt = millisToDateTime(row.long(10)),
        correctCount = row.int(11) ?: 0,
        wrongCount = row.int(12) ?: 0,
        favorite = row.bool(13),
        createdAt = millisToDateTime(row.long(14)) ?: LocalDateTime.now(),
    )

    fun book(row: Array<Any?>): Book = Book(
        id = row.long(0) ?: 0,
        title = row.string(1).orEmpty(),
        author = row.string(2).orEmpty(),
        description = row.string(3).orEmpty(),
        createdAt = millisToDateTime(row.long(4)) ?: LocalDateTime.now(),
    )

    fun chapter(row: Array<Any?>): Chapter = Chapter(
        id = row.long(0) ?: 0,
        bookId = row.long(1) ?: 0,
        position = row.int(2) ?: 0,
        title = row.string(3).orEmpty(),
        arabicText = row.string(4).orEmpty(),
        translation = row.string(5).orEmpty(),
        explanation = row.string(6).orEmpty(),
        examSummary = row.string(7).orEmpty(),
        keyPoints = jsonArrayToStrings(row.string(8) ?: "[]"),
        pageNumber = row.int(9),
        isRead = row.bool(10),
        favorite = row.bool(11),
        createdAt = millisToDateTime(row.long(12)) ?: LocalDateTime.now(),
    )

    fun note(row: Array<Any?>): Note = Note(
        id = row.long(0) ?: 0,
        title = row.string(1).orEmpty(),
        content = row.string(2).orEmpty(),
        category = row.string(3).orEmpty(),
        tags = jsonArrayToStrings(row.string(4) ?: "[]"),
        color = row.string(5).orEmpty(),
        pinned = row.bool(6),
        createdAt = millisToDateTime(row.long(7)) ?: LocalDateTime.now(),
        updatedAt = millisToDateTime(row.long(8)) ?: LocalDateTime.now(),
    )

    fun diaryEntry(row: Array<Any?>): DiaryEntry = DiaryEntry(
        id = row.long(0) ?: 0,
        title = row.string(1).orEmpty(),
        contentEnc = row.blob(2) ?: ByteArray(0),
        mood = row.string(3).orEmpty(),
        entryDate = row.string(4)?.let(LocalDate::parse) ?: LocalDate.now(),
        photoPaths = jsonArrayToStrings(row.string(5) ?: "[]"),
        createdAt = millisToDateTime(row.long(6)) ?: LocalDateTime.now(),
        updatedAt = millisToDateTime(row.long(7)) ?: LocalDateTime.now(),
    )

    fun task(row: Array<Any?>): Task = Task(
        id = row.long(0) ?: 0,
        title = row.string(1).orEmpty(),
        description = row.string(2).orEmpty(),
        dueDate = row.string(3)?.let(LocalDate::parse),
        dueTime = row.string(4),
        priority = row.int(5) ?: 1,
        done = row.bool(6),
        doneAt = millisToDateTime(row.long(7)),
        repeatType = RepeatType.from(row.string(8)),
        weekday = row.int(9),
        category = row.string(10).orEmpty(),
        createdAt = millisToDateTime(row.long(11)) ?: LocalDateTime.now(),
    )

    fun reminder(row: Array<Any?>): Reminder = Reminder(
        id = row.long(0) ?: 0,
        label = row.string(1).orEmpty(),
        type = row.string(2).orEmpty(),
        hour = row.int(3) ?: 0,
        minute = row.int(4) ?: 0,
        enabled = row.bool(5),
    )

    fun album(row: Array<Any?>): Album = Album(
        id = row.long(0) ?: 0,
        name = row.string(1).orEmpty(),
        createdAt = millisToDateTime(row.long(2)) ?: LocalDateTime.now(),
    )

    fun photo(row: Array<Any?>): Photo = Photo(
        id = row.long(0) ?: 0,
        albumId = row.long(1) ?: 0,
        filePath = row.string(2).orEmpty(),
        caption = row.string(3).orEmpty(),
        createdAt = millisToDateTime(row.long(4)) ?: LocalDateTime.now(),
    )
}
