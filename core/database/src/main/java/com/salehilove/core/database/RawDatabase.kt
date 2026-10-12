package com.salehilove.core.database

/**
 * Minimal database abstraction over SQLite used by every DAO.
 *
 * Two implementations exist:
 *  - [AndroidRawDatabase] on top of android.database.sqlite (device)
 *  - JdbcRawDatabase on top of sqlite-jdbc (JVM unit tests)
 *
 * Keeping this surface tiny is what makes the whole data layer testable on a
 * plain JVM with a real SQLite engine.
 */
interface RawDatabase {
    fun execSQL(sql: String, args: List<Any?> = emptyList())
    fun query(sql: String, args: List<Any?> = emptyList()): List<Array<Any?>>
    fun insert(table: String, values: Map<String, Any?>): Long
    fun update(table: String, values: Map<String, Any?>, where: String, whereArgs: List<Any?>): Int
    fun delete(table: String, where: String, whereArgs: List<Any?>): Int
    fun <T> transaction(block: () -> T): T
    fun rawQueryForLong(sql: String, args: List<Any?> = emptyList()): Long?
    fun close()
}

/** Helpers to read nullable columns out of a result row. */
fun Array<Any?>.long(index: Int): Long? = this[index] as? Long
fun Array<Any?>.int(index: Int): Int? = long(index)?.toInt()
fun Array<Any?>.string(index: Int): String? = this[index] as? String
fun Array<Any?>.blob(index: Int): ByteArray? = this[index] as? ByteArray
fun Array<Any?>.bool(index: Int): Boolean = (long(index) ?: 0L) != 0L

fun Boolean.asInt(): Int = if (this) 1 else 0
