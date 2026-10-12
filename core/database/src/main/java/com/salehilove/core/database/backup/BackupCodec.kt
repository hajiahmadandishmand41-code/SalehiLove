package com.salehilove.core.database.backup

import com.salehilove.core.common.Json
import com.salehilove.core.database.Book
import com.salehilove.core.database.Chapter
import com.salehilove.core.database.DiaryEntry
import com.salehilove.core.database.Mappers
import com.salehilove.core.database.Note
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.Reminder
import com.salehilove.core.database.RepeatType
import com.salehilove.core.database.Task
import com.salehilove.core.database.Word
import com.salehilove.core.database.dao.DiaryDao
import com.salehilove.core.database.dao.GalleryDao
import com.salehilove.core.database.dao.NotesDao
import com.salehilove.core.database.dao.PlannerDao
import com.salehilove.core.database.dao.ReligiousDao
import com.salehilove.core.database.dao.SettingsDao
import com.salehilove.core.database.dao.WordDao
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Serialises the entire database to JSON and back. Diary bodies are handed to
 * the codec as PLAINTEXT by the caller (the whole backup container is then
 * sealed with AES-GCM by [com.salehilove.core.security.BackupCrypto]); on
 * import the caller re-encrypts them with this device's Keystore key. This is
 * what makes backups portable between devices.
 *
 * Photo files ride along as Base64 payloads via [PhotoFileAccess] so a restore
 * recreates both rows and files.
 *
 * Uses the dependency-free [Json] codec from core:common.
 */
class BackupCodec(private val photoAccess: PhotoFileAccess) {

    interface PhotoFileAccess {
        /** Reads the photo file's bytes, or null when the file is gone. */
        fun read(path: String): ByteArray?

        /** Maps a stored path to an app-private file on this device. */
        fun write(path: String, bytes: ByteArray)
    }

    object NoPhotos : PhotoFileAccess {
        override fun read(path: String): ByteArray? = null
        override fun write(path: String, bytes: ByteArray) = Unit
    }

    // ---- Export ---------------------------------------------------------------

    fun export(
        db: RawDatabase,
        decryptDiary: (DiaryEntry) -> String,
    ): String {
        val root = LinkedHashMap<String, Any?>()
        root["app"] = "salehilove"
        root["format"] = FORMAT_VERSION
        root["exported_at"] = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        root["words"] = WordDao(db).getAll().map { w ->
            linkedMapOf<String, Any?>(
                "english" to w.english, "meaning" to w.meaning,
                "example" to w.example, "example_fa" to w.exampleFa,
                "phonetic" to w.phonetic, "pos" to w.partOfSpeech,
                "topic" to w.topic, "box" to w.box,
                "next_review" to w.nextReviewAt.toString(),
                "last_review" to w.lastReviewedAt?.toString(),
                "correct" to w.correctCount, "wrong" to w.wrongCount,
                "favorite" to w.favorite,
            )
        }

        val religious = ReligiousDao(db)
        val bookTitles = HashMap<Long, String>()
        root["books"] = religious.books().map { b ->
            bookTitles[b.id] = b.title
            linkedMapOf<String, Any?>(
                "title" to b.title, "author" to b.author, "description" to b.description,
            )
        }
        root["chapters"] = religious.books().flatMap { religious.chaptersOf(it.id) }.map { c ->
            linkedMapOf<String, Any?>(
                "book_title" to (bookTitles[c.bookId] ?: ""),
                "position" to c.position, "title" to c.title,
                "arabic" to c.arabicText, "translation" to c.translation,
                "explanation" to c.explanation, "summary" to c.examSummary,
                "key_points" to c.keyPoints,
                "page" to c.pageNumber,
                "is_read" to c.isRead, "favorite" to c.favorite,
            )
        }

        root["notes"] = NotesDao(db).all().map { n ->
            linkedMapOf<String, Any?>(
                "title" to n.title, "content" to n.content,
                "category" to n.category, "tags" to n.tags,
                "color" to n.color, "pinned" to n.pinned,
                "created" to n.createdAt.toString(), "updated" to n.updatedAt.toString(),
            )
        }

        root["diary"] = DiaryDao(db).all().map { e ->
            linkedMapOf<String, Any?>(
                "title" to e.title,
                "content" to decryptDiary(e),
                "mood" to e.mood, "date" to e.entryDate.toString(),
                "photos" to e.photoPaths,
                "created" to e.createdAt.toString(), "updated" to e.updatedAt.toString(),
            )
        }

        val planner = PlannerDao(db)
        root["tasks"] = planner.allTasks().map { t ->
            linkedMapOf<String, Any?>(
                "title" to t.title, "description" to t.description,
                "due_date" to t.dueDate?.toString(),
                "due_time" to t.dueTime,
                "priority" to t.priority, "done" to t.done,
                "done_at" to t.doneAt?.toString(),
                "repeat" to t.repeatType.name.lowercase(),
                "weekday" to t.weekday,
                "category" to t.category,
            )
        }
        root["reminders"] = planner.reminders().map { r ->
            linkedMapOf<String, Any?>(
                "label" to r.label, "type" to r.type,
                "hour" to r.hour, "minute" to r.minute, "enabled" to r.enabled,
            )
        }

        val gallery = GalleryDao(db)
        val albumNames = HashMap<Long, String>()
        root["albums"] = gallery.albums().map { a ->
            albumNames[a.id] = a.name
            linkedMapOf<String, Any?>("name" to a.name)
        }
        root["photos"] = gallery.albums().flatMap { gallery.photosOf(it.id) }.map { p ->
            val bytes = photoAccess.read(p.filePath)
            linkedMapOf<String, Any?>(
                "album_name" to (albumNames[p.albumId] ?: ""),
                "path" to p.filePath, "caption" to p.caption,
                "created" to p.createdAt.toString(),
                "data" to bytes?.let { Base64.getEncoder().encodeToString(it) },
            )
        }

        val settings = LinkedHashMap<String, Any?>()
        db.query("SELECT key, value FROM app_settings").forEach { row ->
            (row[0] as? String)?.let { k -> (row[1] as? String)?.let { v -> settings[k] = v } }
        }
        root["settings"] = settings

        return Json.write(root)
    }

    // ---- Import ---------------------------------------------------------------

    fun import(
        db: RawDatabase,
        json: String,
        encryptDiary: (String) -> ByteArray,
    ): ImportResult {
        val root = Json.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("ساختار پشتیبان نامعتبر است")
        check(root["app"] == "salehilove") { "این فایل پشتیبان صالحی‌لاو نیست" }
        val format = (root["format"] as? Long)?.toInt() ?: 0
        check(format in 1..FORMAT_VERSION) { "نسخه پشتیبان پشتیبانی نمی‌شود: $format" }

        return db.transaction {
            db.delete("words", "1=1", emptyList())
            db.delete("chapters", "1=1", emptyList())
            db.delete("books", "1=1", emptyList())
            db.delete("notes", "1=1", emptyList())
            db.delete("diary_entries", "1=1", emptyList())
            db.delete("tasks", "1=1", emptyList())
            db.delete("reminders", "1=1", emptyList())
            db.delete("photos", "1=1", emptyList())
            db.delete("albums", "1=1", emptyList())

            var words = 0; var books = 0; var chapters = 0; var notes = 0
            var diary = 0; var tasks = 0; var reminders = 0; var photos = 0

            fun arr(key: String): List<Map<*, *>> =
                (root[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

            val wordDao = WordDao(db)
            arr("words").forEach { o ->
                wordDao.insert(
                    Word(
                        english = o.str("english"),
                        meaning = o.str("meaning"),
                        example = o.str("example"),
                        exampleFa = o.str("example_fa"),
                        phonetic = o.str("phonetic"),
                        partOfSpeech = o.str("pos"),
                        topic = o.str("topic"),
                        box = o.int("box", 1),
                        nextReviewAt = LocalDateTime.parse(o.str("next_review")),
                        lastReviewedAt = o.str("last_review").takeIf { it.isNotBlank() }?.let(LocalDateTime::parse),
                        correctCount = o.int("correct"),
                        wrongCount = o.int("wrong"),
                        favorite = o.bool("favorite"),
                    ),
                )
                words++
            }

            val religious = ReligiousDao(db)
            val titleToBookId = HashMap<String, Long>()
            arr("books").forEach { o ->
                val id = religious.insertBook(
                    Book(title = o.str("title"), author = o.str("author"), description = o.str("description")),
                )
                titleToBookId[o.str("title")] = id
                books++
            }
            arr("chapters").forEach { o ->
                val bookTitle = o.str("book_title")
                val bookId = titleToBookId[bookTitle]
                    ?: religious.insertBook(Book(title = "$bookTitle (بازیابی‌شده)"))
                religious.insertChapter(
                    Chapter(
                        bookId = bookId,
                        position = o.int("position"),
                        title = o.str("title"),
                        arabicText = o.str("arabic"),
                        translation = o.str("translation"),
                        explanation = o.str("explanation"),
                        examSummary = o.str("summary"),
                        keyPoints = (o["key_points"] as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList(),
                        pageNumber = o.int("page"),
                        isRead = o.bool("is_read"),
                        favorite = o.bool("favorite"),
                    ),
                )
                chapters++
            }

            val notesDao = NotesDao(db)
            arr("notes").forEach { o ->
                notesDao.insert(
                    Note(
                        title = o.str("title"),
                        content = o.str("content"),
                        category = o.str("category"),
                        tags = (o["tags"] as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList(),
                        color = o.str("color"),
                        pinned = o.bool("pinned"),
                        createdAt = runCatching { LocalDateTime.parse(o.str("created")) }.getOrDefault(LocalDateTime.now()),
                        updatedAt = runCatching { LocalDateTime.parse(o.str("updated")) }.getOrDefault(LocalDateTime.now()),
                    ),
                )
                notes++
            }

            val diaryDao = DiaryDao(db)
            arr("diary").forEach { o ->
                diaryDao.insert(
                    DiaryEntry(
                        title = o.str("title"),
                        contentEnc = encryptDiary(o.str("content")),
                        mood = o.str("mood"),
                        entryDate = java.time.LocalDate.parse(o.str("date")),
                        photoPaths = (o["photos"] as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList(),
                        createdAt = runCatching { LocalDateTime.parse(o.str("created")) }.getOrDefault(LocalDateTime.now()),
                        updatedAt = runCatching { LocalDateTime.parse(o.str("updated")) }.getOrDefault(LocalDateTime.now()),
                    ),
                )
                diary++
            }

            val planner = PlannerDao(db)
            arr("tasks").forEach { o ->
                planner.insert(
                    Task(
                        title = o.str("title"),
                        description = o.str("description"),
                        dueDate = o.str("due_date").takeIf { it.isNotBlank() }?.let(java.time.LocalDate::parse),
                        dueTime = o.str("due_time").takeIf { it.isNotBlank() },
                        priority = o.int("priority", 1),
                        repeatType = RepeatType.from(o.str("repeat")),
                        weekday = o["weekday"]?.let { (it as? Long)?.toInt() },
                        category = o.str("category"),
                    ),
                )
                tasks++
            }
            arr("reminders").forEach { o ->
                planner.insertReminder(
                    Reminder(
                        label = o.str("label"),
                        type = o.str("type"),
                        hour = o.int("hour"),
                        minute = o.int("minute"),
                        enabled = o.bool("enabled", default = true),
                    ),
                )
                reminders++
            }

            val gallery = GalleryDao(db)
            val albumNameToId = HashMap<String, Long>()
            arr("albums").forEach { o ->
                val id = gallery.createAlbum(o.str("name"), System.currentTimeMillis())
                albumNameToId[o.str("name")] = id
            }
            arr("photos").forEach { o ->
                val albumName = o.str("album_name")
                val albumId = albumNameToId[albumName]
                    ?: gallery.createAlbum("$albumName (بازیابی‌شده)", System.currentTimeMillis())
                gallery.addPhoto(albumId, o.str("path"), o.str("caption"), System.currentTimeMillis())
                val data = o.str("data")
                if (data.isNotBlank()) {
                    runCatching { photoAccess.write(o.str("path"), Base64.getDecoder().decode(data)) }
                }
                photos++
            }

            (root["settings"] as? Map<*, *>)?.forEach { (k, v) ->
                if (k != null && v != null) {
                    db.execSQL(
                        "INSERT INTO app_settings(key, value) VALUES(?, ?) " +
                            "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                        listOf(k.toString(), v.toString()),
                    )
                }
            }

            ImportResult(words, books, chapters, notes, diary, tasks, reminders, photos)
        }
    }

    data class ImportResult(
        val words: Int,
        val books: Int,
        val chapters: Int,
        val notes: Int,
        val diary: Int,
        val tasks: Int,
        val reminders: Int,
        val photos: Int,
    )

    companion object {
        const val FORMAT_VERSION = 1
    }
}

private fun Map<*, *>.str(key: String): String = this[key]?.toString() ?: ""

private fun Map<*, *>.int(key: String, default: Int = 0): Int =
    (this[key] as? Long)?.toInt() ?: (this[key] as? Number)?.toInt() ?: default

private fun Map<*, *>.bool(key: String, default: Boolean = false): Boolean =
    this[key] as? Boolean ?: default
