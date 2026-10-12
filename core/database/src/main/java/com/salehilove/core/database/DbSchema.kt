package com.salehilove.core.database

/**
 * Single source of truth for the SQLite schema. DDL statements are plain SQL
 * strings so the exact same schema is created on device (android.sqlite) and in
 * JVM tests (sqlite-jdbc).
 *
 * Migration policy: forward-only, one entry per version bump, applied inside a
 * transaction by the caller. Every step must keep user data intact.
 */
object DbSchema {

    const val VERSION = 1

    private val TABLES: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS words (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            english TEXT NOT NULL,
            meaning TEXT NOT NULL,
            example TEXT NOT NULL DEFAULT '',
            example_fa TEXT NOT NULL DEFAULT '',
            phonetic TEXT NOT NULL DEFAULT '',
            part_of_speech TEXT NOT NULL DEFAULT '',
            topic TEXT NOT NULL DEFAULT '',
            box INTEGER NOT NULL DEFAULT 1,
            next_review_at INTEGER NOT NULL,
            last_reviewed_at INTEGER,
            correct_count INTEGER NOT NULL DEFAULT 0,
            wrong_count INTEGER NOT NULL DEFAULT 0,
            favorite INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_words_next_review ON words(next_review_at)",
        "CREATE INDEX IF NOT EXISTS idx_words_topic ON words(topic)",
        """
        CREATE TABLE IF NOT EXISTS books (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            author TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS chapters (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
            position INTEGER NOT NULL DEFAULT 0,
            title TEXT NOT NULL,
            arabic_text TEXT NOT NULL DEFAULT '',
            translation TEXT NOT NULL DEFAULT '',
            explanation TEXT NOT NULL DEFAULT '',
            exam_summary TEXT NOT NULL DEFAULT '',
            key_points TEXT NOT NULL DEFAULT '[]',
            page_number INTEGER,
            is_read INTEGER NOT NULL DEFAULT 0,
            favorite INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_chapters_book ON chapters(book_id)",
        """
        CREATE TABLE IF NOT EXISTS notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            content TEXT NOT NULL DEFAULT '',
            category TEXT NOT NULL DEFAULT '',
            tags TEXT NOT NULL DEFAULT '[]',
            color TEXT NOT NULL DEFAULT '',
            pinned INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_notes_updated ON notes(updated_at)",
        """
        CREATE TABLE IF NOT EXISTS diary_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL DEFAULT '',
            content_enc BLOB NOT NULL,
            mood TEXT NOT NULL DEFAULT '',
            entry_date TEXT NOT NULL,
            photo_paths TEXT NOT NULL DEFAULT '[]',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_diary_date ON diary_entries(entry_date)",
        """
        CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            due_date TEXT,
            due_time TEXT,
            priority INTEGER NOT NULL DEFAULT 1,
            done INTEGER NOT NULL DEFAULT 0,
            done_at INTEGER,
            repeat_type TEXT NOT NULL DEFAULT 'none',
            weekday INTEGER,
            category TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_tasks_due ON tasks(due_date, done)",
        """
        CREATE TABLE IF NOT EXISTS reminders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            label TEXT NOT NULL,
            type TEXT NOT NULL,
            hour INTEGER NOT NULL,
            minute INTEGER NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS albums (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS photos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            album_id INTEGER NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
            file_path TEXT NOT NULL,
            caption TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_photos_album ON photos(album_id)",
        """
        CREATE TABLE IF NOT EXISTS app_settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """.trimIndent(),
    )

    /**
     * Forward-only migrations: `MIGRATIONS[n]` upgrades schema n -> n+1.
     */
    val MIGRATIONS: Map<Int, List<String>> = emptyMap()

    fun createAll(exec: (String) -> Unit) = TABLES.forEach(exec)

    fun migrate(from: Int, to: Int, exec: (String) -> Unit) {
        var v = from
        while (v < to) {
            val steps = MIGRATIONS[v]
                ?: throw IllegalStateException("No migration path from schema version $v to ${v + 1}")
            steps.forEach(exec)
            v++
        }
    }
}
