package com.salehilove.feature.personal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.Album
import com.salehilove.core.database.Photo
import com.salehilove.core.database.dao.GalleryDao
import com.salehilove.core.designsystem.components.AppCard
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Gallery without storage permissions: photos are imported via the system
 * file picker (SAF) and copied into the app's private storage, so no
 * READ_EXTERNAL_STORAGE is ever requested. Supports albums, captions,
 * profile-photo and background selection.
 */
class PhotoStore(private val privateDir: File) {

    init { privateDir.mkdirs() }

    /** Copies the picked document into private storage; returns the new file path. */
    fun import(bytes: ByteArray, extension: String = ".jpg"): String {
        val file = File(privateDir, "img_${UUID.randomUUID()}$extension")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun read(path: String): ByteArray? = File(path).takeIf { it.exists() }?.readBytes()

    /** Restores a photo file at its original path (backup import). */
    fun write(path: String, bytes: ByteArray) {
        File(path).parentFile?.mkdirs()
        File(path).writeBytes(bytes)
    }

    fun delete(path: String): Boolean = File(path).takeIf { it.exists() }?.delete() ?: false
}

private data class GalleryUi(
    val albums: List<Album> = emptyList(),
    val counts: Map<Long, Int> = emptyMap(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    dao: GalleryDao,
    store: PhotoStore,
    profilePhotoPath: () -> String?,
    onSetProfilePhoto: (String?) -> Unit,
    backgroundPhotoPath: () -> String?,
    onSetBackground: (String?) -> Unit,
    albumId: Long? = null,
    onOpenAlbum: ((Album) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var internalAlbum by remember { mutableStateOf<Album?>(null) }
    var ui by remember { mutableStateOf(GalleryUi()) }
    var creatingAlbum by remember { mutableStateOf(false) }
    var deletingAlbum by remember { mutableStateOf<Album?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val albums = dao.albums()
                    albums.associate { it.id to dao.albumPhotoCount(it.id) }.let { albums to it }
                }
            }.onSuccess { (albums, counts) ->
                ui = GalleryUi(albums, counts)
                error = null
            }.onFailure { error = it.message ?: "بارگذاری گالری ناموفق بود" }
        }
    }

    LaunchedEffect(albumId) { reload() }

    BackHandler(enabled = albumId == null && internalAlbum != null) {
        internalAlbum = null
        reload()
    }

    val activeAlbum = if (albumId != null) ui.albums.find { it.id == albumId } else internalAlbum
    if (activeAlbum == null) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { creatingAlbum = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, "آلبوم جدید")
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (ui.albums.isEmpty()) {
                    EmptyState("آلبومی نیست — با دکمه + آلبوم بساز و عکس‌ها را اضافه کن")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        items(ui.albums, key = { it.id }) { a ->
                            AppCard(
                                onClick = {
                                    if (onOpenAlbum != null) onOpenAlbum(a)
                                    else internalAlbum = a
                                },
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            a.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "${PersianCalendar.toPersianDigits((ui.counts[a.id] ?: 0).toString())} عکس",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { deletingAlbum = a }) {
                                        Icon(Icons.Filled.Delete, "حذف آلبوم", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (creatingAlbum) {
            var name by remember { mutableStateOf("") }
            var validation by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { creatingAlbum = false },
                title = { Text("آلبوم جدید", fontWeight = FontWeight.Bold) },
                text = {
                    PersianTextField("نام آلبوم", name, { name = it }, errorMessage = validation)
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isBlank()) { validation = "نام الزامی است"; return@TextButton }
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.createAlbum(name.trim(), System.currentTimeMillis()) }
                            creatingAlbum = false
                            reload()
                        }
                    }) { Text("ساخت", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { creatingAlbum = false }) { Text("انصراف") } },
            )
        }

        deletingAlbum?.let { a ->
            ConfirmDialog(
                title = "حذف آلبوم",
                message = "آلبوم «${a.name}» و عکس‌هایش حذف شوند؟",
                confirmLabel = "حذف",
                dismissLabel = "انصراف",
                onConfirm = {
                    scope.launch {
                        val paths = withContext(Dispatchers.IO) { dao.deleteAlbum(a.id) }
                        if (profilePhotoPath()?.let { it in paths } == true) onSetProfilePhoto(null)
                        if (backgroundPhotoPath()?.let { it in paths } == true) onSetBackground(null)
                        withContext(Dispatchers.IO) { paths.forEach { store.delete(it) } }
                        deletingAlbum = null
                        reload()
                    }
                },
                onDismiss = { deletingAlbum = null },
            )
        }
    } else {
        AlbumView(
            dao = dao,
            store = store,
            album = activeAlbum,
            isProfile = { profilePhotoPath() != null && it == profilePhotoPath() },
            onSetProfile = onSetProfilePhoto,
            isBackground = { backgroundPhotoPath() != null && it == backgroundPhotoPath() },
            onSetBackground = onSetBackground,
        )
    }
}

@Composable
private fun AlbumView(
    dao: GalleryDao,
    store: PhotoStore,
    album: Album,
    isProfile: (String) -> Boolean,
    onSetProfile: (String?) -> Unit,
    isBackground: (String) -> Boolean,
    onSetBackground: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var deleting by remember { mutableStateOf<Photo?>(null) }
    var captioning by remember { mutableStateOf<Photo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { dao.photosOf(album.id) } }
                .onSuccess {
                    photos = it
                    error = null
                }
                .onFailure { error = it.message ?: "بارگذاری عکس‌ها ناموفق بود" }
        }
    }

    LaunchedEffect(album.id) { reload() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("خواندن عکس ممکن نشد")
                    val path = store.import(bytes)
                    dao.addPhoto(album.id, path, "", System.currentTimeMillis())
                }
            }
            result.exceptionOrNull()?.let { error = it.message ?: "وارد کردن عکس ناموفق بود" }
            if (result.isSuccess) error = null
            reload()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { importLauncher.launch("image/*") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, "افزودن عکس")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        album.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${PersianCalendar.toPersianDigits(photos.size.toString())} عکس",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (photos.isEmpty()) {
                EmptyState("این آلبوم خالی است — با دکمه + عکس اضافه کن")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoCell(
                            photo = photo,
                            isProfile = isProfile(photo.filePath),
                            isBackground = isBackground(photo.filePath),
                            onCaption = { captioning = photo },
                            onDelete = { deleting = photo },
                            onSetProfile = { onSetProfile(photo.filePath) },
                            onSetBackground = { onSetBackground(photo.filePath) },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { photo ->
        ConfirmDialog(
            title = "حذف عکس",
            message = "این عکس از گالری حذف شود؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    val path = withContext(Dispatchers.IO) { dao.deletePhoto(photo.id) }
                    if (path != null) {
                        if (isProfile(path)) onSetProfile(null)
                        if (isBackground(path)) onSetBackground(null)
                        withContext(Dispatchers.IO) { store.delete(path) }
                    }
                    deleting = null
                    reload()
                }
            },
            onDismiss = { deleting = null },
        )
    }

    captioning?.let { photo ->
        var caption by remember(photo.id) { mutableStateOf(photo.caption) }
        AlertDialog(
            onDismissRequest = { captioning = null },
            title = { Text("توضیح عکس", fontWeight = FontWeight.Bold) },
            text = { PersianTextField("توضیح", caption, { caption = it }) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.updateCaption(photo.id, caption.trim()) }
                        captioning = null
                        reload()
                    }
                }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { captioning = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun PhotoCell(
    photo: Photo,
    isProfile: Boolean,
    isBackground: Boolean,
    onCaption: () -> Unit,
    onDelete: () -> Unit,
    onSetProfile: () -> Unit,
    onSetBackground: () -> Unit,
) {
    val photoState = rememberPrivatePhotoState(photo.filePath, maxDimension = 512)
    val bitmap = photoState.bitmap
    AppCard {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = photo.caption.ifBlank { "عکس" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photoState.isLoaded) {
                            Text("عکس در دسترس نیست")
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isProfile) {
                        Icon(
                            Icons.Filled.Person,
                            "عکس پروفایل",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (isBackground) {
                        Icon(
                            Icons.Filled.Wallpaper,
                            "پس‌زمینه",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (photo.caption.isNotBlank()) {
                Text(
                    photo.caption,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSetProfile) {
                    Icon(Icons.Filled.Person, "تنظیم به‌عنوان عکس پروفایل", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onSetBackground) {
                    Icon(Icons.Filled.Wallpaper, "تنظیم به‌عنوان پس‌زمینه", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCaption) {
                    Icon(Icons.Filled.PhotoLibrary, "توضیح", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
