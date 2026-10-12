package com.salehilove.core.database.dao

import com.salehilove.core.database.Album
import com.salehilove.core.database.Mappers
import com.salehilove.core.database.Photo
import com.salehilove.core.database.RawDatabase

class GalleryDao(private val db: RawDatabase) {

    fun createAlbum(name: String, createdAt: Long): Long =
        db.insert("albums", mapOf("name" to name, "created_at" to createdAt))

    fun renameAlbum(id: Long, name: String) =
        db.update("albums", mapOf("name" to name), "id = ?", listOf(id))

    /** Deletes the album row; photo rows cascade. Caller must delete files. */
    fun deleteAlbum(id: Long): List<String> {
        val paths = db
            .query("SELECT file_path FROM photos WHERE album_id = ?", listOf(id))
            .mapNotNull { it[0] as? String }
        db.delete("albums", "id = ?", listOf(id))
        return paths
    }

    fun albums(): List<Album> =
        db.query("SELECT id, name, created_at FROM albums ORDER BY name").map(Mappers::album)

    fun albumById(id: Long): Album? = db
        .query("SELECT id, name, created_at FROM albums WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::album)

    fun albumPhotoCount(albumId: Long): Int =
        db.rawQueryForLong("SELECT COUNT(*) FROM photos WHERE album_id = ?", listOf(albumId))?.toInt() ?: 0

    fun addPhoto(albumId: Long, filePath: String, caption: String, createdAt: Long): Long =
        db.insert(
            "photos",
            mapOf(
                "album_id" to albumId,
                "file_path" to filePath,
                "caption" to caption,
                "created_at" to createdAt,
            ),
        )

    fun photosOf(albumId: Long): List<Photo> = db
        .query(
            "SELECT id, album_id, file_path, caption, created_at FROM photos WHERE album_id = ? ORDER BY id DESC",
            listOf(albumId),
        )
        .map(Mappers::photo)

    fun photoById(id: Long): Photo? = db
        .query("SELECT id, album_id, file_path, caption, created_at FROM photos WHERE id = ?", listOf(id))
        .firstOrNull()?.let(Mappers::photo)

    fun deletePhoto(id: Long): String? {
        val path = photoById(id)?.filePath
        db.delete("photos", "id = ?", listOf(id))
        return path
    }

    fun updateCaption(id: Long, caption: String) =
        db.update("photos", mapOf("caption" to caption), "id = ?", listOf(id))
}
