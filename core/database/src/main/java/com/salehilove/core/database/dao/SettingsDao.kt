package com.salehilove.core.database.dao

import com.salehilove.core.database.RawDatabase

/**
 * Typed key/value settings store. Values that must never appear in plaintext
 * (the diary PIN hash) are stored as Base64 blobs and handled by the security
 * layer; this DAO is just a dumb, robust map.
 */
class SettingsDao(private val db: RawDatabase) {

    fun getString(key: String, default: String? = null): String? = db
        .query("SELECT value FROM app_settings WHERE key = ?", listOf(key))
        .firstOrNull()
        ?.let { it[0] as? String }
        ?: default

    fun putString(key: String, value: String) {
        db.execSQL(
            "INSERT INTO app_settings(key, value) VALUES(?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            listOf(key, value),
        )
    }

    fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default

    fun putInt(key: String, value: Int) = putString(key, value.toString())

    fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key)?.let { it == "1" } ?: default

    fun putBoolean(key: String, value: Boolean) = putString(key, if (value) "1" else "0")

    fun getBlob(key: String): ByteArray? = db
        .query("SELECT value FROM app_settings WHERE key = ?", listOf(key))
        .firstOrNull()
        ?.let { it[0] as? String }
        ?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }

    fun putBlob(key: String, value: ByteArray) {
        putString(key, java.util.Base64.getEncoder().encodeToString(value))
    }

    fun remove(key: String) = db.delete("app_settings", "key = ?", listOf(key))
}
