package com.salehilove.core.database.dao

import com.salehilove.core.database.Mappers
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.Reminder
import com.salehilove.core.database.Task
import com.salehilove.core.database.asInt
import com.salehilove.core.database.string
import java.time.LocalDate

class PlannerDao(private val db: RawDatabase) {

    private val taskCols =
        "id, title, description, due_date, due_time, priority, done, done_at, repeat_type, weekday, category, created_at"

    fun insert(task: Task): Long = db.insert(
        "tasks",
        mapOf(
            "title" to task.title,
            "description" to task.description,
            "due_date" to task.dueDate?.toString(),
            "due_time" to task.dueTime,
            "priority" to task.priority,
            "done" to task.done.asInt(),
            "done_at" to task.doneAt?.let(Mappers::dateTimeToMillis),
            "repeat_type" to task.repeatType.name.lowercase(),
            "weekday" to task.weekday,
            "category" to task.category,
            "created_at" to Mappers.dateTimeToMillis(task.createdAt),
        ),
    )

    fun update(task: Task) = db.update(
        "tasks",
        mapOf(
            "title" to task.title,
            "description" to task.description,
            "due_date" to task.dueDate?.toString(),
            "due_time" to task.dueTime,
            "priority" to task.priority,
            "repeat_type" to task.repeatType.name.lowercase(),
            "weekday" to task.weekday,
            "category" to task.category,
        ),
        "id = ?",
        listOf(task.id),
    )

    fun delete(id: Long) = db.delete("tasks", "id = ?", listOf(id))

    fun byId(id: Long): Task? = db
        .query("SELECT $taskCols FROM tasks WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::task)

    /** Tasks for a specific date, plus date-less tasks and weekly ones for that weekday. */
    fun tasksForDate(date: LocalDate): List<Task> {
        val weekday = date.dayOfWeek.value // 1 = Monday .. 7 = Sunday
        return db.query(
            """SELECT $taskCols FROM tasks
               WHERE (due_date = ?)
                  OR (due_date IS NULL AND repeat_type = 'none' AND done = 0)
                  OR (repeat_type = 'daily' AND (done = 0 OR due_date != ?))
                  OR (repeat_type = 'weekly' AND weekday = ? AND (done = 0 OR due_date != ?))
               ORDER BY done ASC, priority DESC, due_time ASC, id ASC""",
            listOf(date.toString(), date.toString(), weekday.toLong(), date.toString()),
        ).map(Mappers::task)
    }

    fun allTasks(): List<Task> = db
        .query("SELECT $taskCols FROM tasks ORDER BY id")
        .map(Mappers::task)

    fun tasksInRange(from: LocalDate, to: LocalDate): List<Task> = db.query(
        """SELECT $taskCols FROM tasks
           WHERE due_date IS NOT NULL AND due_date >= ? AND due_date <= ?
           ORDER BY due_date ASC, due_time ASC, id ASC""",
        listOf(from.toString(), to.toString()),
    ).map(Mappers::task)

    fun overdue(date: LocalDate): List<Task> = db.query(
        """SELECT $taskCols FROM tasks
           WHERE done = 0 AND due_date IS NOT NULL AND due_date < ?
           ORDER BY due_date ASC""",
        listOf(date.toString()),
    ).map(Mappers::task)

    fun setDone(id: Long, done: Boolean, at: Long) = db.update(
        "tasks",
        mapOf("done" to done.asInt(), "done_at" to at),
        "id = ?",
        listOf(id),
    )

    fun todayProgress(date: LocalDate): Pair<Int, Int> {
        val tasks = tasksForDate(date)
        return tasks.count { it.done } to tasks.size
    }

    fun upcomingCount(from: LocalDate, to: LocalDate): Int = db.query(
        """SELECT COUNT(*) FROM tasks
           WHERE done = 0 AND due_date >= ? AND due_date <= ?""",
        listOf(from.toString(), to.toString()),
    ).firstOrNull()?.let { (it[0] as? Long)?.toInt() ?: 0 } ?: 0

    // ---- Reminders -----------------------------------------------------------

    fun insertReminder(reminder: Reminder): Long = db.insert(
        "reminders",
        mapOf(
            "label" to reminder.label,
            "type" to reminder.type,
            "hour" to reminder.hour,
            "minute" to reminder.minute,
            "enabled" to reminder.enabled.asInt(),
        ),
    )

    fun updateReminder(reminder: Reminder) = db.update(
        "reminders",
        mapOf(
            "label" to reminder.label,
            "type" to reminder.type,
            "hour" to reminder.hour,
            "minute" to reminder.minute,
            "enabled" to reminder.enabled.asInt(),
        ),
        "id = ?",
        listOf(reminder.id),
    )

    fun deleteReminder(id: Long) = db.delete("reminders", "id = ?", listOf(id))

    fun reminders(): List<Reminder> = db
        .query("SELECT id, label, type, hour, minute, enabled FROM reminders ORDER BY hour, minute")
        .map(Mappers::reminder)

    fun reminderById(id: Long): Reminder? = db
        .query("SELECT id, label, type, hour, minute, enabled FROM reminders WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::reminder)

    fun enabledReminders(): List<Reminder> = db
        .query("SELECT id, label, type, hour, minute, enabled FROM reminders WHERE enabled = 1 ORDER BY hour, minute")
        .map(Mappers::reminder)
}
