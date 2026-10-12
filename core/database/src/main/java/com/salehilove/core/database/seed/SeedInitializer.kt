package com.salehilove.core.database.seed

import com.salehilove.core.common.TimeProvider
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.dao.ReligiousDao
import com.salehilove.core.database.dao.SettingsDao
import java.time.LocalDateTime

/** Applies one-time starter content on first launch (idempotent). */
object SeedInitializer {

    const val KEY_BOOKS_SEEDED = "seed_books_done"
    const val KEY_REMINDERS_SEEDED = "seed_reminders_done"

    fun seedAll(db: RawDatabase, wordRepository: com.salehilove.core.database.repo.WordRepository, timeProvider: TimeProvider) {
        val settings = SettingsDao(db)
        val now = timeProvider.now()
        wordRepository.seedIfEmpty(SeedData.starterWords(now), settings)
        seedBooks(db, settings)
        seedReminders(db, settings)
    }

    fun seedBooks(db: RawDatabase, settings: SettingsDao) {
        if (settings.getBoolean(KEY_BOOKS_SEEDED, false)) return
        val dao = ReligiousDao(db)
        if (dao.books().isNotEmpty()) {
            settings.putBoolean(KEY_BOOKS_SEEDED, true)
            return
        }
        db.transaction {
            SeedData.starterBooks().forEach { seedBook ->
                val bookId = dao.insertBook(seedBook.book)
                seedBook.chapters.forEach { chapter ->
                    dao.insertChapter(chapter.copy(bookId = bookId))
                }
            }
        }
        settings.putBoolean(KEY_BOOKS_SEEDED, true)
    }

    fun seedReminders(db: RawDatabase, settings: SettingsDao) {
        if (settings.getBoolean(KEY_REMINDERS_SEEDED, false)) return
        val planner = com.salehilove.core.database.dao.PlannerDao(db)
        if (planner.reminders().isNotEmpty()) {
            settings.putBoolean(KEY_REMINDERS_SEEDED, true)
            return
        }
        db.transaction {
            planner.insertReminder(
                com.salehilove.core.database.Reminder(
                    label = "یادآور صبحگاهی — برنامه امروز را مرور کن",
                    type = "morning",
                    hour = 8,
                    minute = 0,
                ),
            )
            planner.insertReminder(
                com.salehilove.core.database.Reminder(
                    label = "یادآور ظهر — مرور واژگان",
                    type = "noon",
                    hour = 13,
                    minute = 30,
                ),
            )
            planner.insertReminder(
                com.salehilove.core.database.Reminder(
                    label = "یادآور شب — یادداشت روز و مطالعه دینی",
                    type = "evening",
                    hour = 21,
                    minute = 0,
                ),
            )
        }
        settings.putBoolean(KEY_REMINDERS_SEEDED, true)
    }
}
