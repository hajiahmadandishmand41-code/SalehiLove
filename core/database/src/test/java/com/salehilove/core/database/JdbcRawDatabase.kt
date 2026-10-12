package com.salehilove.core.database

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * JVM implementation of [RawDatabase] backed by real SQLite via sqlite-jdbc.
 * Lives in the test source set so the driver never ships inside the APK.
 */
class JdbcRawDatabase private constructor(
    private val connection: Connection,
) : RawDatabase {

    companion object {
        fun inMemory(): JdbcRawDatabase {
            val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
            conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            return JdbcRawDatabase(conn).apply {
                DbSchema.createAll { sql ->
                    connection.createStatement().use { it.execute(sql) }
                }
            }
        }
    }

    private fun bind(ps: PreparedStatement, args: List<Any?>) {
        args.forEachIndexed { i, arg ->
            when (arg) {
                null -> ps.setNull(i + 1, java.sql.Types.NULL)
                is Long -> ps.setLong(i + 1, arg)
                is Int -> ps.setLong(i + 1, arg.toLong())
                is Boolean -> ps.setLong(i + 1, if (arg) 1L else 0L)
                is Double -> ps.setDouble(i + 1, arg)
                is String -> ps.setString(i + 1, arg)
                is ByteArray -> ps.setBytes(i + 1, arg)
                else -> throw IllegalArgumentException("Unsupported bind type ${arg::class.java}")
            }
        }
    }

    override fun execSQL(sql: String, args: List<Any?>) {
        if (args.isEmpty()) {
            connection.createStatement().use { it.execute(sql) }
        } else {
            connection.prepareStatement(sql).use { ps ->
                bind(ps, args)
                ps.executeUpdate()
            }
        }
    }

    override fun query(sql: String, args: List<Any?>): List<Array<Any?>> {
        connection.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs ->
                val meta = rs.metaData
                val columns = meta.columnCount
                val rows = ArrayList<Array<Any?>>()
                while (rs.next()) {
                    val row = arrayOfNulls<Any?>(columns)
                    for (i in 1..columns) {
                        row[i - 1] = when (meta.getColumnType(i)) {
                            java.sql.Types.INTEGER, java.sql.Types.BIGINT -> rs.getLong(i).let {
                                if (rs.wasNull()) null else it
                            }
                            java.sql.Types.REAL, java.sql.Types.DOUBLE, java.sql.Types.FLOAT -> rs.getDouble(i).let {
                                if (rs.wasNull()) null else it
                            }
                            java.sql.Types.BLOB, java.sql.Types.BINARY, java.sql.Types.VARBINARY -> rs.getBytes(i)
                            else -> rs.getString(i) // TEXT incl. NULL -> null
                        }
                    }
                    rows.add(row)
                }
                return rows
            }
        }
    }

    override fun insert(table: String, values: Map<String, Any?>): Long {
        val cols = values.keys.joinToString(",")
        val placeholders = values.values.joinToString(",") { "?" }
        val sql = "INSERT INTO $table ($cols) VALUES ($placeholders)"
        connection.prepareStatement(sql).use { ps ->
            bind(ps, values.values.toList())
            ps.executeUpdate()
        }
        connection.createStatement().use {
            it.executeQuery("SELECT last_insert_rowid()").use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    override fun update(table: String, values: Map<String, Any?>, where: String, whereArgs: List<Any?>): Int {
        val sets = values.keys.joinToString(",") { "$it = ?" }
        val sql = "UPDATE $table SET $sets WHERE $where"
        connection.prepareStatement(sql).use { ps ->
            bind(ps, values.values.toList() + whereArgs)
            return ps.executeUpdate()
        }
    }

    override fun delete(table: String, where: String, whereArgs: List<Any?>): Int {
        val sql = "DELETE FROM $table WHERE $where"
        connection.prepareStatement(sql).use { ps ->
            bind(ps, whereArgs)
            return ps.executeUpdate()
        }
    }

    override fun <T> transaction(block: () -> T): T {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    override fun rawQueryForLong(sql: String, args: List<Any?>): Long? {
        connection.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong(1) else null
            }
        }
    }

    override fun close() = connection.close()
}
