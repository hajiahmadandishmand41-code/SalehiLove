package com.salehilove.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.salehilove.core.common.SystemTimeProvider
import com.salehilove.core.database.AndroidRawDatabase
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.dao.GalleryDao
import com.salehilove.core.database.dao.NotesDao
import com.salehilove.core.database.dao.PlannerDao
import com.salehilove.core.database.dao.ReligiousDao
import com.salehilove.core.database.dao.SettingsDao
import com.salehilove.core.database.repo.DiaryRepository
import com.salehilove.core.database.repo.WordRepository
import com.salehilove.core.database.seed.SeedInitializer
import com.salehilove.core.security.CryptoEngine
import com.salehilove.core.security.DiaryVault
import com.salehilove.core.security.KeystoreKeyProvider
import com.salehilove.core.security.PinManager
import com.salehilove.feature.backup.BackupManager
import com.salehilove.feature.personal.PhotoStore
import com.salehilove.feature.planner.ReminderDbProvider
import com.salehilove.feature.planner.ReminderScheduler
import java.io.File

/** Central settings keys. */
object SettingsKeys {
    const val DIARY_PIN = "diary_pin_hash"
    const val THEME_MODE = "theme_mode" // light | dark | system
    const val PROFILE_PHOTO = "profile_photo_path"
    const val BACKGROUND_PHOTO = "background_photo_path"
    const val DIARY_BIOMETRIC = "diary_biometric_enabled"
}

/**
 * Hand-rolled dependency container: one instance per process, owned by
 * [SalehiLoveApp]. No DI framework needed at this size.
 */
class AppContainer(context: Context) : ReminderDbProvider {

    val appContext: Context = context.applicationContext

    // --- Database -----------------------------------------------------------
    val db: RawDatabase = AndroidRawDatabase(appContext)
    val settings = SettingsDao(db)
    val wordRepo = WordRepository(db, SystemTimeProvider())
    val diaryRepo = DiaryRepository(
        db = db,
        encrypt = { plain -> vault.encryptEntry(plain) },
        decrypt = { blob -> vault.decryptEntry(blob) },
    )
    val notesDao = NotesDao(db)
    val religiousDao = ReligiousDao(db)
    val plannerDao = PlannerDao(db)
    val galleryDao = GalleryDao(db)

    // --- Security -----------------------------------------------------------
    val vault = DiaryVault(CryptoEngine(KeystoreKeyProvider()))

    // --- Gallery ------------------------------------------------------------
    val photoStore = PhotoStore(File(appContext.filesDir, "photos"))

    // --- Backup -------------------------------------------------------------
    val backupManager = BackupManager(
        context = appContext,
        db = db,
        diaryRepo = diaryRepo,
        photoStore = object : BackupManager.PhotoStoreAccess {
            override fun read(path: String): ByteArray? = photoStore.read(path)
            override fun write(path: String, bytes: ByteArray) = photoStore.write(path, bytes)
            override fun photosDir(): File = File(appContext.filesDir, "photos")
        },
    )

    // --- Diary PIN ----------------------------------------------------------
    fun hasDiaryPin(): Boolean = settings.getBlob(SettingsKeys.DIARY_PIN) != null

    fun setDiaryPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it in '0'..'9' }) { "رمز باید ۴ تا ۸ رقم باشد" }
        settings.putBlob(SettingsKeys.DIARY_PIN, PinManager.hashPin(pin))
    }

    fun verifyDiaryPin(pin: String): Boolean {
        val stored = settings.getBlob(SettingsKeys.DIARY_PIN) ?: return false
        return PinManager.verifyPin(pin, stored)
    }

    fun clearDiaryPin() = settings.remove(SettingsKeys.DIARY_PIN)

    // --- Appearance ---------------------------------------------------------
    var themeMode by mutableStateOf(settings.getString(SettingsKeys.THEME_MODE) ?: "system")
    private var selectedProfilePhotoPath by mutableStateOf(settings.getString(SettingsKeys.PROFILE_PHOTO))
    private var selectedBackgroundPhotoPath by mutableStateOf(settings.getString(SettingsKeys.BACKGROUND_PHOTO))

    fun applyThemeMode(mode: String) {
        themeMode = mode
        settings.putString(SettingsKeys.THEME_MODE, mode)
    }

    fun profilePhotoPath(): String? = selectedProfilePhotoPath

    fun setProfilePhoto(path: String?) {
        selectedProfilePhotoPath = path
        if (path == null) settings.remove(SettingsKeys.PROFILE_PHOTO)
        else settings.putString(SettingsKeys.PROFILE_PHOTO, path)
    }

    fun backgroundPhotoPath(): String? = selectedBackgroundPhotoPath

    fun setBackgroundPhoto(path: String?) {
        selectedBackgroundPhotoPath = path
        if (path == null) settings.remove(SettingsKeys.BACKGROUND_PHOTO)
        else settings.putString(SettingsKeys.BACKGROUND_PHOTO, path)
    }

    // --- ReminderDbProvider -------------------------------------------------
    override fun plannerDao(): PlannerDao = plannerDao

    fun onAppStart() {
        // Seed starter content once (idempotent) and schedule reminders.
        SeedInitializer.seedAll(db, wordRepo, SystemTimeProvider())
        ReminderScheduler.scheduleAll(appContext, plannerDao)
    }
}

class SalehiLoveApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onAppStart()
    }
}
