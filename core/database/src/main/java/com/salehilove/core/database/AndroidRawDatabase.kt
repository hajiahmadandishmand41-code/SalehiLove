package com.salehilove.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Android implementation of [RawDatabase]. Foreign keys and WAL are enabled on
 * every connection; [DbSchema.MIGRATIONS] describe forward-only upgrades.
 *
 * Note on bind args: Android's cursor APIs (rawQuery/update/delete) only
 * accept String[] whereas execSQL accepts Object[]. We stringify bind args
 * for the String[] APIs — SQLite's column affinity applies the correct
 * comparison semantics, and no query binds blobs (asserted by DAO tests).
 */
class AndroidRawDatabase(
    context: Context,
    dbName: String = "salehilove.db",
) : RawDatabase {

    private val helper = Helper(context.applicationContext, dbName)

    private class Helper(context: Context, name: String) :
        SQLiteOpenHelper(context, name, null, DbSchema.VERSION) {

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
            db.enableWriteAheadLogging()
        }

        override fun onCreate(db: SQLiteDatabase) {
            DbSchema.createAll { sql -> db.execSQL(sql) }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            DbSchema.migrate(oldVersion, newVersion) { sql -> db.execSQL(sql) }
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Downgrades are not supported; protect user data by refusing loudly.
            throw IllegalStateException(
                "Database downgrade from $oldVersion to $newVersion is not supported. " +
                    "Please restore a newer backup or contact support.",
            )
        }
    }

    private fun db(): SQLiteDatabase = helper.writableDatabase

    private fun List<Any?>.toStringArgs(): Array<String?> = map { arg ->
        when (arg) {
            null -> null
            is String -> arg
            is Long -> arg.toString()
            is Int -> arg.toString()
            is Boolean -> if (arg) "1" else "0"
            is Double -> arg.toString()
            is Float -> arg.toString()
            else -> throw IllegalArgumentException(
                "Unsupported query bind type ${arg::class.java} (blobs cannot be query args)",
            )
        }
    }.toTypedArray()

    override fun execSQL(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db().execSQL(sql) else db().execSQL(sql, args.toTypedArray())
    }

    override fun query(sql: String, args: List<Any?>): List<Array<Any?>> {
        db().rawQuery(sql, args.toStringArgs()).use { cursor ->
            val rows = ArrayList<Array<Any?>>(cursor.count)
            while (cursor.moveToNext()) {
                val row = arrayOfNulls<Any?>(cursor.columnCount)
                for (i in 0 until cursor.columnCount) {
                    row[i] = when (cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                        else -> cursor.getString(i)
                    }
                }
                rows.add(row)
            }
            return rows
        }
    }

    override fun insert(table: String, values: Map<String, Any?>): Long {
        return db().insertWithOnConflict(table, null, values.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun update(table: String, values: Map<String, Any?>, where: String, whereArgs: List<Any?>): Int {
        return db().update(table, values.toContentValues(), where, whereArgs.toStringArgs())
    }

    override fun delete(table: String, where: String, whereArgs: List<Any?>): Int {
        return db().delete(table, where, whereArgs.toStringArgs())
    }

    override fun <T> transaction(block: () -> T): T {
        val d = db()
        if (d.inTransaction()) return block()
        d.beginTransaction()
        try {
            val result = block()
            d.setTransactionSuccessful()
            return result
        } finally {
            d.endTransaction()
        }
    }

    override fun rawQueryForLong(sql: String, args: List<Any?>): Long? {
        db().rawQuery(sql, args.toStringArgs()).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else null
        }
    }

    override fun close() = helper.close()

    private fun Map<String, Any?>.toContentValues() = android.content.ContentValues().apply {
        for ((k, v) in this@toContentValues) {
            when (v) {
                null -> putNull(k)
                is Long -> put(k, v)
                is Int -> put(k, v)
                is Boolean -> put(k, if (v) 1L else 0L)
                is Double -> put(k, v)
                is Float -> put(k, v)
                is String -> put(k, v)
                is ByteArray -> put(k, v)
                else -> throw IllegalArgumentException("Unsupported value type ${v::class.java} for column $k")
            }
        }
    }
}
