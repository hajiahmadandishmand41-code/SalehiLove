package com.salehilove.core.database

import com.salehilove.core.common.FixedTimeProvider
import com.salehilove.core.database.dao.DiaryDao
import com.salehilove.core.database.dao.GalleryDao
import com.salehilove.core.database.dao.NotesDao
import com.salehilove.core.database.dao.PlannerDao
import com.salehilove.core.database.dao.ReligiousDao
import com.salehilove.core.database.dao.SettingsDao
import com.salehilove.core.database.dao.WordDao
import com.salehilove.core.database.seed.SeedData
import com.salehilove.core.database.seed.SeedInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Runs against a real SQLite database (sqlite-jdbc) using the exact DDL that
 * ships on the device. If these pass, the schema and DAOs are correct SQLite.
 */
class DaoTest {

    private lateinit var db: JdbcRawDatabase
    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)

    @Before
    fun setUp() {
        db = JdbcRawDatabase.inMemory()
    }

    // ---- Words ---------------------------------------------------------------

    @Test
    fun `word crud and search`() {
        val dao = WordDao(db)
        val id = dao.insert(
            Word(
                english = "peace",
                meaning = "آرامش",
                example = "We value peace.",
                exampleFa = "ما برای صلح ارزش قائلیم.",
                phonetic = "piːs",
                partOfSpeech = "noun",
                topic = "فضیلت‌ها",
                nextReviewAt = now,
            ),
        )
        assertTrue(id > 0)

        val loaded = dao.getById(id)!!
        assertEquals("peace", loaded.english)
        assertEquals("آرامش", loaded.meaning)
        assertFalse(loaded.favorite)

        dao.setFavorite(id, true)
        assertTrue(dao.getById(id)!!.favorite)

        assertEquals(listOf("peace"), dao.search("peace").map { it.english })
        assertEquals(listOf("peace"), dao.search("آرامش").map { it.english })
        assertEquals(listOf("peace"), dao.search("value").map { it.english })
        assertEquals(listOf("peace"), dao.search("piːs").map { it.english })
        assertEquals(listOf("peace"), dao.search("فضیلت‌ها").map { it.english })
        assertEquals(listOf("فضیلت‌ها"), dao.topics())

        dao.update(
            loaded.copy(
                english = "peaceful",
                meaning = "صلح‌آمیز",
                example = "A peaceful place.",
                exampleFa = "یک جای آرام.",
                phonetic = "ˈpiːsfəl",
                partOfSpeech = "adjective",
                topic = "صفات",
            ),
        )
        val reopened = WordDao(db).getById(id)!!
        assertEquals("A peaceful place.", reopened.example)
        assertEquals("یک جای آرام.", reopened.exampleFa)
        assertEquals("ˈpiːsfəl", reopened.phonetic)
        assertEquals("adjective", reopened.partOfSpeech)
        assertEquals("صفات", reopened.topic)
        assertTrue(reopened.favorite)
        assertEquals(listOf("peaceful"), dao.search("peaceful place").map { it.english })
        assertEquals(listOf("peaceful"), dao.search("صفات").map { it.english })

        dao.delete(id)
        assertNull(dao.getById(id))
    }

    @Test
    fun `due words respect time`() {
        val dao = WordDao(db)
        val past = dao.insert(Word(english = "old", meaning = "قدیمی", nextReviewAt = now.minusHours(1)))
        val future = dao.insert(Word(english = "new", meaning = "جدید", nextReviewAt = now.plusDays(2)))
        val t = Mappers.dateTimeToMillis(now)
        assertEquals(listOf("old"), dao.dueWords(t).map { it.english })
        assertEquals(1, dao.dueCount(t))
        assertEquals(0, dao.masteredCount())
        assertTrue(past > 0 && future > 0)
    }

    @Test
    fun `review counters update`() {
        val dao = WordDao(db)
        val id = dao.insert(Word(english = "try", meaning = "تلاش", box = 2, nextReviewAt = now.minusMinutes(1)))
        dao.applyReview(id, correct = true, newBox = 3, nextReviewAt = Mappers.dateTimeToMillis(now.plusDays(3)), reviewedAt = Mappers.dateTimeToMillis(now))
        val w = dao.getById(id)!!
        assertEquals(3, w.box)
        assertEquals(1, w.correctCount)
        assertEquals(0, w.wrongCount)
        assertTrue(w.lastReviewedAt != null)
        assertEquals(0, dao.masteredCount())
        dao.applyReview(id, correct = false, newBox = 1, nextReviewAt = Mappers.dateTimeToMillis(now.plusMinutes(10)), reviewedAt = Mappers.dateTimeToMillis(now))
        assertEquals(1, dao.getById(id)!!.wrongCount)
    }

    // ---- Books & chapters ------------------------------------------------------

    @Test
    fun `books chapters cascade delete`() {
        val dao = ReligiousDao(db)
        val bookId = dao.insertBook(Book(title = "نهج‌البلاغه"))
        dao.insertChapter(Chapter(bookId = bookId, title = "حکمت ۱", arabicText = "العلم خير من المال", pageNumber = 552))
        dao.insertChapter(Chapter(bookId = bookId, title = "حکمت ۲"))
        assertEquals(2, dao.chapterCount(bookId))
        assertEquals(0, dao.readCount(bookId))
        dao.setChapterRead(dao.chaptersOf(bookId)[0].id, true)
        assertEquals(1, dao.readCount(bookId))

        dao.deleteBook(bookId)
        assertEquals(0, dao.chapterCount(bookId))
        assertEquals(0L, db.query("SELECT COUNT(*) FROM chapters").first()[0])
    }

    // ---- Notes -----------------------------------------------------------------

    @Test
    fun `notes search by tag and category`() {
        val dao = NotesDao(db)
        dao.insert(Note(title = "ایده", content = "محتوا", category = "شخصی", tags = listOf("آینده", "کار")))
        dao.insert(Note(title = "خرید", content = "لیست خرید", category = "روزمره", tags = listOf("خانه")))
        assertEquals(2, dao.all().size)
        assertEquals(1, dao.search("", category = "شخصی").size)
        assertEquals(1, dao.search("", tag = "کار").size)
        assertEquals(1, dao.search("خرید").size)
        assertEquals(listOf("روزمره", "شخصی"), dao.categories())
        assertEquals(setOf("آینده", "کار", "خانه"), dao.allTags().toSet())
    }

    @Test
    fun `pinned notes come first`() {
        val dao = NotesDao(db)
        dao.insert(Note(title = "عادی", updatedAt = now))
        dao.insert(Note(title = "سنجاق‌شده", pinned = true, updatedAt = now.minusDays(1)))
        assertEquals("سنجاق‌شده", dao.all().first().title)
    }

    // ---- Diary -----------------------------------------------------------------

    @Test
    fun `diary entries store blob ciphertext`() {
        val dao = DiaryDao(db)
        val cipher = ByteArray(64) { (it * 7).toByte() } // pretend ciphertext
        val id = dao.insert(
            DiaryEntry(
                title = "یادداشت محرمانه",
                contentEnc = cipher,
                entryDate = LocalDate.of(2026, 10, 12),
                mood = "خوشحال",
                photoPaths = listOf("IMG_001.jpg"),
            ),
        )
        val loaded = dao.byId(id)!!
        assertTrue(loaded.contentEnc.contentEquals(cipher))
        assertEquals(LocalDate.of(2026, 10, 12), loaded.entryDate)
        assertEquals(listOf("IMG_001.jpg"), loaded.photoPaths)
        // The database must never contain the plaintext of a diary body.
        val rawRows = db.query("SELECT content_enc FROM diary_entries WHERE id = ?", listOf(id))
        assertTrue(rawRows.first()[0] is ByteArray)
        assertEquals(1, dao.count())
    }

    // ---- Tasks & reminders -------------------------------------------------------

    @Test
    fun `tasks for date include dateless and repeating`() {
        val dao = PlannerDao(db)
        val today = LocalDate.of(2026, 10, 12)
        dao.insert(Task(title = "ملاقات", dueDate = today, dueTime = "14:00", priority = 2))
        dao.insert(Task(title = "بدون تاریخ"))
        dao.insert(Task(title = "روزانه", repeatType = RepeatType.DAILY))
        dao.insert(Task(title = "هفتگی", repeatType = RepeatType.WEEKLY, weekday = today.dayOfWeek.value))
        dao.insert(Task(title = "هفتگی غیرامروز", repeatType = RepeatType.WEEKLY, weekday = (today.dayOfWeek.value % 7) + 1))
        dao.insert(Task(title = "دیروز مانده", dueDate = today.minusDays(1)))
        dao.insert(Task(title = "فردا", dueDate = today.plusDays(1)))

        val titles = dao.tasksForDate(today).map { it.title }
        assertTrue(titles.contains("ملاقات"))
        assertTrue(titles.contains("بدون تاریخ"))
        assertTrue(titles.contains("روزانه"))
        assertTrue(titles.contains("هفتگی"))
        assertFalse(titles.contains("هفتگی غیرامروز"))
        assertFalse(titles.contains("فردا"))

        assertEquals(1, dao.overdue(today).size)
        assertEquals(1, dao.tasksInRange(today, today.plusDays(1)).count { it.title == "فردا" })

        val done = dao.tasksForDate(today).first { it.title == "ملاقات" }
        dao.setDone(done.id, true, 100L)
        assertTrue(dao.byId(done.id)!!.done)
        val (doneCount, total) = dao.todayProgress(today)
        assertEquals(1, doneCount)
        assertEquals(4, total)
    }

    @Test
    fun `reminders crud`() {
        val dao = PlannerDao(db)
        val id = dao.insertReminder(Reminder(label = "صبح", type = "morning", hour = 8, minute = 0))
        assertEquals(1, dao.reminders().size)
        dao.updateReminder(Reminder(id = id, label = "صبح", type = "morning", hour = 7, minute = 30, enabled = false))
        assertEquals(0, dao.enabledReminders().size)
        dao.deleteReminder(id)
        assertEquals(0, dao.reminders().size)
    }

    // ---- Gallery ---------------------------------------------------------------

    @Test
    fun `albums and photos with cascade`() {
        val dao = GalleryDao(db)
        val albumId = dao.createAlbum("سفر", 1000L)
        dao.addPhoto(albumId, "files/photo1.jpg", "کوه", 1001L)
        dao.addPhoto(albumId, "files/photo2.jpg", "", 1002L)
        assertEquals(2, dao.albumPhotoCount(albumId))
        assertEquals(2, dao.photosOf(albumId).size)
        val removed = dao.deleteAlbum(albumId)
        assertEquals(listOf("files/photo1.jpg", "files/photo2.jpg"), removed)
        assertEquals(0L, db.query("SELECT COUNT(*) FROM photos").first()[0])
    }

    // ---- Settings --------------------------------------------------------------

    @Test
    fun `settings upsert and typed getters`() {
        val dao = SettingsDao(db)
        assertNull(dao.getString("missing"))
        assertEquals("fallback", dao.getString("missing", "fallback"))
        dao.putString("theme", "dark")
        dao.putString("theme", "light") // upsert
        assertEquals("light", dao.getString("theme"))
        dao.putInt("count", 5)
        assertEquals(5, dao.getInt("count", 0))
        dao.putBoolean("flag", true)
        assertTrue(dao.getBoolean("flag", false))
        val blob = byteArrayOf(1, 2, 3, -4)
        dao.putBlob("pin", blob)
        assertTrue(dao.getBlob("pin")!!.contentEquals(blob))
        dao.remove("theme")
        assertNull(dao.getString("theme"))
    }

    // ---- Schema ------------------------------------------------------------------

    @Test
    fun `migration map is gapless and versions match`() {
        // For VERSION = 1 there are no migrations yet; invariant must hold anyway.
        assertEquals(1, DbSchema.VERSION)
        var v = 1
        while (v < DbSchema.VERSION) {
            assertTrue("missing migration $v -> ${v + 1}", DbSchema.MIGRATIONS.containsKey(v))
            v++
        }
    }

    @Test
    fun `fresh schema creates every expected table`() {
        val tables = db
            .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
            .mapNotNull { it[0] as? String }
        assertEquals(
            listOf(
                "albums", "app_settings", "books", "chapters", "diary_entries",
                "notes", "photos", "reminders", "tasks", "words",
            ),
            tables,
        )
    }

    // ---- Seeds ---------------------------------------------------------------------

    @Test
    fun `seed words are complete and well-formed`() {
        val words = SeedData.starterWords(now)
        assertTrue("expected a rich starter set, got ${words.size}", words.size >= 40)
        words.forEach { w ->
            assertTrue("blank english: $w", w.english.isNotBlank())
            assertTrue("blank meaning for ${w.english}", w.meaning.isNotBlank())
            assertTrue("no example for ${w.english}", w.example.isNotBlank())
            assertTrue("no fa example for ${w.english}", w.exampleFa.isNotBlank())
            assertTrue("no phonetic for ${w.english}", w.phonetic.isNotBlank())
            assertTrue("no topic for ${w.english}", w.topic.isNotBlank())
        }
        assertTrue(words.map { it.english }.distinct().size == words.size)
    }

    @Test
    fun `seed religious content is accurate and non-empty`() {
        val books = SeedData.starterBooks()
        assertEquals(3, books.size)
        books.forEach { seed ->
            assertTrue(seed.chapters.isNotEmpty())
            seed.chapters.forEach { c ->
                assertTrue("blank arabic in ${c.title}", c.arabicText.isNotBlank())
                assertTrue("blank translation in ${c.title}", c.translation.isNotBlank())
                assertTrue("no key points in ${c.title}", c.keyPoints.isNotEmpty())
                assertTrue("no summary in ${c.title}", c.examSummary.isNotBlank())
            }
        }
        assertTrue(
            "The Ghadir narration must not be labeled as Silsilat al-Dhahab",
            books.flatMap { it.chapters }.any {
                it.title == "حدیث غدیر" && it.arabicText.contains("مَنْ كُنْتُ مَوْلَاهُ")
            },
        )
    }

    @Test
    fun `known Ghadir narration label is corrected without replacing content`() {
        val dao = ReligiousDao(db)
        val bookId = dao.insertBook(Book(title = "احادیث نبوی — گزیده"))
        val arabic = "مَنْ كُنْتُ مَوْلَاهُ فَهَذَا عَلِيٌّ مَوْلَاهُ."
        val chapterId = dao.insertChapter(
            Chapter(
                bookId = bookId,
                title = "حدیث سلسلة‌الذهب",
                arabicText = arabic,
                explanation = "شرح موجود باید حفظ شود.",
            ),
        )

        SeedInitializer.correctKnownContentLabels(db, SettingsDao(db))

        val corrected = dao.chapterById(chapterId)!!
        assertEquals("حدیث غدیر", corrected.title)
        assertEquals(arabic, corrected.arabicText)
        assertEquals("شرح موجود باید حفظ شود.", corrected.explanation)
    }

    @Test
    fun `seed initializer is idempotent`() {
        val settings = SettingsDao(db)
        val time = FixedTimeProvider(now)
        SeedInitializer.seedAll(db, com.salehilove.core.database.repo.WordRepository(db, time), time)

        val wordsAfterFirst = WordDao(db).totalCount()
        val booksAfterFirst = ReligiousDao(db).books().size
        val remindersAfterFirst = PlannerDao(db).reminders().size
        assertTrue(wordsAfterFirst >= 40)
        assertEquals(3, booksAfterFirst)
        assertEquals(3, remindersAfterFirst)

        // Second run must not duplicate anything.
        SeedInitializer.seedAll(db, com.salehilove.core.database.repo.WordRepository(db, time), time)
        assertEquals(wordsAfterFirst, WordDao(db).totalCount())
        assertEquals(booksAfterFirst, ReligiousDao(db).books().size)
        assertEquals(remindersAfterFirst, PlannerDao(db).reminders().size)
        assertNotNull(db.rawQueryForLong("SELECT COUNT(*) FROM diary_entries"))
    }
}
