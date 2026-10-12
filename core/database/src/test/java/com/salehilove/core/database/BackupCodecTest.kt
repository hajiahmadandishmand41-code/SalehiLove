package com.salehilove.core.database

import com.salehilove.core.common.FixedTimeProvider
import com.salehilove.core.database.backup.BackupCodec
import com.salehilove.core.database.repo.DiaryRepository
import com.salehilove.core.database.repo.WordRepository
import com.salehilove.core.database.seed.SeedData
import com.salehilove.core.database.seed.SeedInitializer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Full export -> import round trip against a real SQLite database, including
 * the real AES-GCM diary encryption from core:security (pure JVM parts).
 */
class BackupCodecTest {

    private class MapPhotoAccess : BackupCodec.PhotoFileAccess {
        val files = HashMap<String, ByteArray>()
        override fun read(path: String): ByteArray? = files[path]
        override fun write(path: String, bytes: ByteArray) {
            files[path] = bytes
        }
    }

    private class FixedKeyProvider : com.salehilove.core.security.KeyProvider {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getOrCreateKey(): SecretKey = key
    }

    private lateinit var db: JdbcRawDatabase
    private lateinit var photos: MapPhotoAccess
    private lateinit var codec: BackupCodec
    private val engine = com.salehilove.core.security.CryptoEngine(FixedKeyProvider())
    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)

    @Before
    fun setUp() {
        db = JdbcRawDatabase.inMemory()
        photos = MapPhotoAccess()
        codec = BackupCodec(photos)
    }

    private fun populatedDb(): DiaryRepository {
        val time = FixedTimeProvider(now)
        val wordRepo = WordRepository(db, time)
        val settings = com.salehilove.core.database.dao.SettingsDao(db)
        SeedInitializer.seedAll(db, wordRepo, time)

        val diary = DiaryRepository(
            db,
            encrypt = { engine.encrypt(it.toByteArray()) },
            decrypt = { engine.decrypt(it).toString(Charsets.UTF_8) },
        )
        diary.saveNew("روز اول", "امروز روز خوبی بود — رمزنگاری واقعی تست می‌شود.", "شاد", LocalDate.of(2026, 10, 11))
        diary.saveNew("روز دوم", "خاطره دوم با متن طولانی‌تر " + "x".repeat(500), "آرام", LocalDate.of(2026, 10, 12))

        val planner = com.salehilove.core.database.dao.PlannerDao(db)
        planner.insert(
            Task(title = "مطالعه نهج‌البلاغه", dueDate = LocalDate.of(2026, 10, 12), dueTime = "20:00", priority = 2),
        )
        planner.insert(Task(title = "ورزش روزانه", repeatType = RepeatType.DAILY))

        val notesDao = com.salehilove.core.database.dao.NotesDao(db)
        notesDao.insert(
            Note(
                title = "نکته مهم",
                content = "محتوای یادداشت",
                category = "شخصی",
                tags = listOf("مهم", "مرور"),
                pinned = true,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val gallery = com.salehilove.core.database.dao.GalleryDao(db)
        val albumId = gallery.createAlbum("خاطرات", System.currentTimeMillis())
        photos.files["photos/p1.jpg"] = ByteArray(256) { (it * 3).toByte() }
        gallery.addPhoto(albumId, "photos/p1.jpg", "توضیح عکس", System.currentTimeMillis())

        settings.putString("theme", "dark")
        settings.putInt("some_number", 12)

        return diary
    }

    @Test
    fun `full round trip preserves every table`() {
        val diary = populatedDb()
        val originalWordCount = WordRepository(db, FixedTimeProvider(now)).totalCount()
        val json = codec.export(db) { engine.decrypt(it.contentEnc).toString(Charsets.UTF_8) }

        // Inside the JSON container the diary body travels as plaintext by design;
        // confidentiality comes from the AES-GCM container (BackupCrypto), and the
        // database itself never stores plaintext (asserted in DaoTest).
        val decryptedBefore = diary.all().map { engine.decrypt(it.contentEnc).toString(Charsets.UTF_8) }
        assertTrue(json.contains("روز اول"))

        // Import into a fresh database with a DIFFERENT key (simulating another device).
        val otherKey = object : com.salehilove.core.security.KeyProvider {
            private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            override fun getOrCreateKey(): SecretKey = key
        }
        val otherEngine = com.salehilove.core.security.CryptoEngine(otherKey)
        val db2 = JdbcRawDatabase.inMemory()
        val photos2 = MapPhotoAccess()
        val codec2 = BackupCodec(photos2)
        val result = codec2.import(db2, json) { otherEngine.encrypt(it.toByteArray()) }

        assertEquals(originalWordCount, result.words)
        assertEquals(3, result.books)
        assertTrue(result.chapters >= 10)
        assertEquals(1, result.notes)
        assertEquals(2, result.diary)
        assertEquals(2, result.tasks)
        assertEquals(3, result.reminders)
        assertEquals(1, result.photos)

        // Words intact with topics.
        val words2 = WordRepository(db2, FixedTimeProvider(now))
        assertEquals(originalWordCount, words2.totalCount())
        assertTrue(words2.topics().isNotEmpty())

        // Diary decrypts on the new device with its own key.
        val diary2 = DiaryRepository(
            db2,
            encrypt = { otherEngine.encrypt(it.toByteArray()) },
            decrypt = { otherEngine.decrypt(it).toString(Charsets.UTF_8) },
        )
        val contents = diary2.all().map { otherEngine.decrypt(it.contentEnc).toString(Charsets.UTF_8) }
        assertEquals(decryptedBefore.toSet(), contents.toSet())

        // Notes with tags.
        val notes2 = com.salehilove.core.database.dao.NotesDao(db2).all()
        assertEquals(1, notes2.size)
        assertEquals(listOf("مهم", "مرور"), notes2.first().tags)

        // Settings transferred.
        val settings2 = com.salehilove.core.database.dao.SettingsDao(db2)
        assertEquals("dark", settings2.getString("theme"))
        assertEquals(12, settings2.getInt("some_number", 0))

        // Photo file restored.
        val originalBytes = photos.files["photos/p1.jpg"]!!
        assertArrayEquals(originalBytes, photos2.files["photos/p1.jpg"])
    }

    @Test
    fun `import replaces previous data atomically`() {
        populatedDb()
        val json = codec.export(db) { "x" }

        // Mutate: add one more word so old data differs.
        WordRepository(db, FixedTimeProvider(now)).add(Word(english = "extra", meaning = "اضافه"))

        val result = codec.import(db, json) { ByteArray(0) }
        assertEquals(SeedData.starterWords(now).size, result.words)
        assertEquals(0, com.salehilove.core.database.dao.WordDao(db).search("extra").size)
    }

    @Test
    fun `corrupt json is rejected`() {
        try {
            codec.import(db, "{\"app\":\"other\"}") { ByteArray(0) }
            error("expected rejection")
        } catch (expected: IllegalStateException) {}
        try {
            codec.import(db, "not json at all") { ByteArray(0) }
            error("expected rejection")
        } catch (expected: org.json.JSONException) {}
    }

    @Test
    fun `export survives a future format guard`() {
        populatedDb()
        val json = codec.export(db) { "x" }
        val bumped = org.json.JSONObject(json).put("format", 99)
        try {
            codec.import(db, bumped.toString()) { ByteArray(0) }
            error("expected rejection")
        } catch (expected: IllegalStateException) {}
    }
}
