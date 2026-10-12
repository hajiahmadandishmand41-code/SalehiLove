package com.salehilove.core.database.backup

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
import org.json.JSONArray
import org.json.JSONObject
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
        val root = JSONObject()
        root.put("app", "salehilove")
        root.put("format", FORMAT_VERSION)
        root.put("exported_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        root.put("words", WordDao(db).getAll().toJsonArray { w ->
            JSONObject()
                .put("english", w.english).put("meaning", w.meaning)
                .put("example", w.example).put("example_fa", w.exampleFa)
                .put("phonetic", w.phonetic).put("pos", w.partOfSpeech)
                .put("topic", w.topic).put("box", w.box)
                .put("next_review", w.nextReviewAt.toString())
                .put("last_review", w.lastReviewedAt?.toString())
                .put("correct", w.correctCount).put("wrong", w.wrongCount)
                .put("favorite", w.favorite)
        })

        val religious = ReligiousDao(db)
        root.put("books", religious.books().toJsonArray { b ->
            JSONObject().put("title", b.title).put("author", b.author).put("description", b.description)
        })
        root.put("chapters", religious.books().flatMap { religious.chaptersOf(it.id) }.toJsonArray { c ->
            JSONObject()
                .put("book_title", religious.bookById(c.bookId)?.title ?: "")
                .put("position", c.position).put("title", c.title)
                .put("arabic", c.arabicText).put("translation", c.translation)
                .put("explanation", c.explanation).put("summary", c.examSummary)
                .put("key_points", JSONArray(c.keyPoints))
                .put("page", c.pageNumber ?: JSONObject.NULL)
                .put("is_read", c.isRead).put("favorite", c.favorite)
        })

        root.put("notes", NotesDao(db).all().toJsonArray { n ->
            JSONObject()
                .put("title", n.title).put("content", n.content)
                .put("category", n.category).put("tags", JSONArray(n.tags))
                .put("color", n.color).put("pinned", n.pinned)
                .put("created", n.createdAt.toString()).put("updated", n.updatedAt.toString())
        })

        root.put("diary", DiaryDao(db).all().toJsonArray { e ->
            JSONObject()
                .put("title", e.title)
                .put("content", decryptDiary(e))
                .put("mood", e.mood).put("date", e.entryDate.toString())
                .put("photos", JSONArray(e.photoPaths))
                .put("created", e.createdAt.toString()).put("updated", e.updatedAt.toString())
        })

        val planner = PlannerDao(db)
        root.put("tasks", planner.allTasks().toJsonArray { t ->
            JSONObject()
                .put("title", t.title).put("description", t.description)
                .put("due_date", t.dueDate?.toString() ?: JSONObject.NULL)
                .put("due_time", t.dueTime ?: JSONObject.NULL)
                .put("priority", t.priority).put("done", t.done)
                .put("done_at", t.doneAt?.toString())
                .put("repeat", t.repeatType.name.lowercase())
                .put("weekday", t.weekday ?: JSONObject.NULL)
                .put("category", t.category)
        })
        root.put("reminders", planner.reminders().toJsonArray { r ->
            JSONObject().put("label", r.label).put("type", r.type)
                .put("hour", r.hour).put("minute", r.minute).put("enabled", r.enabled)
        })

        val gallery = GalleryDao(db)
        root.put("albums", gallery.albums().toJsonArray { a ->
            JSONObject().put("name", a.name).put("created", a.createdAt.toString())
        })
        root.put("photos", gallery.albums().flatMap { gallery.photosOf(it.id) }.toJsonArray { p ->
            val bytes = photoAccess.read(p.filePath)
            JSONObject()
                .put("album_name", gallery.albumById(p.albumId)?.name ?: "")
                .put("path", p.filePath).put("caption", p.caption)
                .put("created", p.createdAt.toString())
                .put("data", bytes?.let { Base64.getEncoder().encodeToString(it) } ?: JSONObject.NULL)
        })

        val settings = JSONObject()
        db.query("SELECT key, value FROM app_settings").forEach { row ->
            (row[0] as? String)?.let { k -> (row[1] as? String)?.let { v -> settings.put(k, v) } }
        }
        root.put("settings", settings)

        return root.toString()
    }

    // ---- Import ---------------------------------------------------------------

    fun import(
        db: RawDatabase,
        json: String,
        encryptDiary: (String) -> ByteArray,
    ): ImportResult {
        val root = JSONObject(json)
        check(root.optString("app") == "salehilove") { "این فایل پشتیبان صالحی‌لاو نیست" }
        val format = root.optInt("format", 0)
        check(format in 1..FORMAT_VERSION) { "نسخه پشتیبان پشتیبانی نمی‌شود: $format" }

        return db.transaction {
            // Wipe current data (files for photos are overwritten on write).
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

            val wordDao = WordDao(db)
            root.optJSONArray("words")?.forEachObj { o ->
                wordDao.insert(
                    Word(
                        english = o.getString("english"),
                        meaning = o.getString("meaning"),
                        example = o.optString("example"),
                        exampleFa = o.optString("example_fa"),
                        phonetic = o.optString("phonetic"),
                        partOfSpeech = o.optString("pos"),
                        topic = o.optString("topic"),
                        box = o.optInt("box", 1),
                        nextReviewAt = LocalDateTime.parse(o.getString("next_review")),
                        lastReviewedAt = o.optString("last_review")?.takeIf { it.isNotBlank() }?.let(LocalDateTime::parse),
                        correctCount = o.optInt("correct"),
                        wrongCount = o.optInt("wrong"),
                        favorite = o.optBoolean("favorite"),
                    ),
                )
                words++
            }

            val religious = ReligiousDao(db)
            val titleToBookId = HashMap<String, Long>()
            root.optJSONArray("books")?.forEachObj { o ->
                val id = religious.insertBook(
                    Book(
                        title = o.getString("title"),
                        author = o.optString("author"),
                        description = o.optString("description"),
                    ),
                )
                titleToBookId[o.getString("title")] = id
                books++
            }
            root.optJSONArray("chapters")?.forEachObj { o ->
                val bookId = titleToBookId[o.optString("book_title")]
                    ?: religious.insertBook(Book(title = o.optString("book_title") + " (بازیابی‌شده)"))
                religious.insertChapter(
                    Chapter(
                        bookId = bookId,
                        position = o.optInt("position"),
                        title = o.getString("title"),
                        arabicText = o.optString("arabic"),
                        translation = o.optString("translation"),
                        explanation = o.optString("explanation"),
                        examSummary = o.optString("summary"),
                        keyPoints = o.optJSONArray("key_points")?.toStrings() ?: emptyList(),
                        pageNumber = if (o.isNull("page")) null else o.optInt("page"),
                        isRead = o.optBoolean("is_read"),
                        favorite = o.optBoolean("favorite"),
                    ),
                )
                chapters++
            }

            val notesDao = NotesDao(db)
            root.optJSONArray("notes")?.forEachObj { o ->
                notesDao.insert(
                    Note(
                        title = o.getString("title"),
                        content = o.optString("content"),
                        category = o.optString("category"),
                        tags = o.optJSONArray("tags")?.toStrings() ?: emptyList(),
                        color = o.optString("color"),
                        pinned = o.optBoolean("pinned"),
                        createdAt = runCatching { LocalDateTime.parse(o.getString("created")) }.getOrDefault(LocalDateTime.now()),
                        updatedAt = runCatching { LocalDateTime.parse(o.getString("updated")) }.getOrDefault(LocalDateTime.now()),
                    ),
                )
                notes++
            }

            val diaryDao = DiaryDao(db)
            root.optJSONArray("diary")?.forEachObj { o ->
                diaryDao.insert(
                    DiaryEntry(
                        title = o.optString("title"),
                        contentEnc = encryptDiary(o.optString("content")),
                        mood = o.optString("mood"),
                        entryDate = java.time.LocalDate.parse(o.getString("date")),
                        photoPaths = o.optJSONArray("photos")?.toStrings() ?: emptyList(),
                        createdAt = runCatching { LocalDateTime.parse(o.getString("created")) }.getOrDefault(LocalDateTime.now()),
                        updatedAt = runCatching { LocalDateTime.parse(o.getString("updated")) }.getOrDefault(LocalDateTime.now()),
                    ),
                )
                diary++
            }

            val planner = PlannerDao(db)
            root.optJSONArray("tasks")?.forEachObj { o ->
                planner.insert(
                    Task(
                        title = o.getString("title"),
                        description = o.optString("description"),
                        dueDate = if (o.isNull("due_date")) null else java.time.LocalDate.parse(o.getString("due_date")),
                        dueTime = if (o.isNull("due_time")) null else o.optString("due_time"),
                        priority = o.optInt("priority", 1),
                        repeatType = RepeatType.from(o.optString("repeat")),
                        weekday = if (o.isNull("weekday")) null else o.optInt("weekday"),
                        category = o.optString("category"),
                    ),
                )
                tasks++
            }
            root.optJSONArray("reminders")?.forEachObj { o ->
                planner.insertReminder(
                    Reminder(
                        label = o.getString("label"),
                        type = o.optString("type"),
                        hour = o.optInt("hour"),
                        minute = o.optInt("minute"),
                        enabled = o.optBoolean("enabled", true),
                    ),
                )
                reminders++
            }

            val gallery = GalleryDao(db)
            val albumNameToId = HashMap<String, Long>()
            root.optJSONArray("albums")?.forEachObj { o ->
                val id = gallery.createAlbum(o.getString("name"), System.currentTimeMillis())
                albumNameToId[o.getString("name")] = id
            }
            root.optJSONArray("photos")?.forEachObj { o ->
                val albumId = albumNameToId[o.optString("album_name")]
                    ?: gallery.createAlbum(o.optString("album_name") + " (بازیابی‌شده)", System.currentTimeMillis())
                gallery.addPhoto(albumId, o.optString("path"), o.optString("caption"), System.currentTimeMillis())
                val data = o.optString("data", "")
                if (data.isNotBlank()) {
                    runCatching { photoAccess.write(o.optString("path"), Base64.getDecoder().decode(data)) }
                }
                photos++
            }

            val settings = root.optJSONObject("settings")
            settings?.keys()?.forEach { key ->
                settings.opt(key)?.let { db.execSQL(
                    "INSERT INTO app_settings(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                    listOf(key, it.toString()),
                ) }
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

private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(mapper(it)) }
    return arr
}

private inline fun JSONArray.forEachObj(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) block(getJSONObject(i))
}

private fun JSONArray.toStrings(): List<String> = List(length()) { getString(it) }
