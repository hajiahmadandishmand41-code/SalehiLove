package com.salehilove.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.salehilove.core.designsystem.components.SectionCard
import com.salehilove.core.designsystem.navigation.AppNav
import com.salehilove.core.designsystem.navigation.rememberAppNav
import com.salehilove.core.designsystem.theme.SalehiLoveTheme
import com.salehilove.core.designsystem.theme.ThemeMode
import com.salehilove.feature.backup.BackupScreen
import com.salehilove.feature.learning.QuizScreen
import com.salehilove.feature.learning.ReligiousScreen
import com.salehilove.feature.learning.ReviewScreen
import com.salehilove.feature.learning.WordsScreen
import com.salehilove.feature.personal.DiaryLockState
import com.salehilove.feature.personal.DiaryScreen
import com.salehilove.feature.personal.GalleryScreen
import com.salehilove.feature.personal.NotesScreen
import com.salehilove.feature.planner.PlannerScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SalehiLoveApp).container

        // Exact alarms: try to opt in on API 31+.
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(android.app.AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                runCatching {
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:$packageName"),
                        ),
                    )
                }
            }
        }

        setContent {
            val themeMode = when (container.themeMode) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            SalehiLoveTheme(themeMode = themeMode) {
                // Persian-first UI: always lay out right-to-left, regardless of
                // the device language, while Arabic/English text renders correctly.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    AppRoot(container)
                }
            }
        }
    }
}

private enum class Tab(val route: String, val label: String) {
    HOME("home", "خانه"),
    LEARN("words", "واژگان"),
    PLANNER("planner", "برنامه"),
    PERSONAL("personal", "شخصی"),
    MORE("settings", "تنظیمات"),
}

private fun tabForRoute(route: String): Tab = when {
    route == "home" -> Tab.HOME
    route.startsWith("words") || route.startsWith("review") || route.startsWith("quiz") || route.startsWith("religious") -> Tab.LEARN
    route.startsWith("planner") -> Tab.PLANNER
    route.startsWith("personal") || route.startsWith("notes") || route.startsWith("diary") || route.startsWith("gallery") -> Tab.PERSONAL
    route.startsWith("settings") || route.startsWith("backup") -> Tab.MORE
    else -> Tab.HOME
}

/** Hub for personal and study sections. */
@Composable
private fun PersonalHub(
    openNotes: () -> Unit,
    openDiary: () -> Unit,
    openGallery: () -> Unit,
    openReligious: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "یادداشت‌ها",
            subtitle = "نوشته‌ها با جست‌وجو، برچسب، دسته‌بندی و سنجاق",
            icon = Icons.Filled.EditNote,
            onClick = openNotes,
        )
        SectionCard(
            title = "دفترچه خاطرات خصوصی",
            subtitle = "محافظت‌شده با رمز عبور اختصاصی و رمزنگاری واقعی AES-256-GCM",
            icon = Icons.Filled.Lock,
            onClick = openDiary,
        )
        SectionCard(
            title = "گالری تصاویر",
            subtitle = "آلبوم‌ها، عکس پروفایل و پس‌زمینه سفارشی بدون نیاز به مجوز حافظه",
            icon = Icons.Filled.PhotoLibrary,
            onClick = openGallery,
        )
        SectionCard(
            title = "مطالعات دینی",
            subtitle = "نهج‌البلاغه، قرآن کریم و احادیث نبوی همراه با ترجمه و نکات کلیدی",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = openReligious,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(container: AppContainer) {
    val nav = rememberAppNav()
    val diaryLock = remember { DiaryLockState() }

    val current = nav.current
    val activeTab = tabForRoute(current)
    val isHome = current == AppNav.HOME

    // Navigate while preserving full back-stack history so the Back button on
    // inner screens and Android system Back always return to the previous screen.
    fun navigateTo(route: String) {
        if (route == AppNav.HOME) {
            if (current != AppNav.HOME) {
                if (nav.state.contains(AppNav.HOME)) {
                    nav.popUpTo(AppNav.HOME)
                } else {
                    nav.resetTo(AppNav.HOME)
                }
            }
        } else if (current != route) {
            nav.push(route)
        }
    }

    // Handle Android system Back identically to the in-app back button.
    BackHandler(enabled = nav.canPop) { nav.pop() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!isHome) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                titleFor(current),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (!nav.pop()) {
                                        nav.resetTo(AppNav.HOME)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "بازگشت",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { navigateTo(tab.route) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.LEARN -> Icons.Filled.School
                                    Tab.PLANNER -> Icons.Filled.Schedule
                                    Tab.PERSONAL -> Icons.Filled.PhotoLibrary
                                    Tab.MORE -> Icons.Filled.Settings
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val bgPath = container.backgroundPhotoPath()
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (bgPath != null) {
                val bmp = remember(bgPath) { android.graphics.BitmapFactory.decodeFile(bgPath) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = 0.2f,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
                    )
                }
            }
            Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    AppRoute(
                        route = current,
                        container = container,
                        nav = nav,
                        diaryLock = diaryLock,
                    )
                }
            }
        }
    }
}

private fun titleFor(route: String): String = when {
    route.startsWith("quiz") -> "آزمون واژگان"
    route.startsWith("review") -> "مرور واژگان"
    route.startsWith("religious/chapter/") -> "مطالعه فصل"
    route.startsWith("religious/book/") -> "فصل‌های کتاب"
    route.startsWith("religious") -> "مطالعات دینی"
    route.startsWith("diary") -> "دفترچه خاطرات"
    route.startsWith("gallery/album/") -> "آلبوم تصاویر"
    route.startsWith("gallery") -> "گالری تصاویر"
    route.startsWith("backup") -> "پشتیبان‌گیری و بازیابی"
    route.startsWith("planner") -> "برنامه‌ریز روزانه"
    route.startsWith("words") -> "واژگان"
    route.startsWith("notes") -> "یادداشت‌ها"
    route.startsWith("personal") -> "بخش شخصی"
    route.startsWith("settings") -> "تنظیمات"
    else -> "SalehiLove ❤️"
}

@Composable
private fun AppRoute(
    route: String,
    container: AppContainer,
    nav: AppNav,
    diaryLock: DiaryLockState,
) {
    val context = LocalContext.current
    when {
        route == "home" -> DashboardScreen(
            container = container,
            openWords = { nav.push("words") },
            openReview = { nav.push("review") },
            openPlanner = { nav.push("planner") },
            openNotes = { nav.push("notes") },
            openDiary = { nav.push("diary") },
            openReligious = { nav.push("religious") },
        )

        route == "words" -> WordsScreen(
            repo = container.wordRepo,
            openReview = { nav.push("review") },
            openQuiz = { nav.push("quiz") },
        )

        route == "review" -> ReviewScreen(
            repo = container.wordRepo,
            onFinished = { nav.pop() },
        )

        route == "quiz" -> QuizScreen(
            repo = container.wordRepo,
            onFinished = { nav.pop() },
        )

        route.startsWith("religious/chapter/") -> {
            val parts = route.removePrefix("religious/chapter/").split("/")
            val bId = parts.getOrNull(0)?.toLongOrNull()
            val cId = parts.getOrNull(1)?.toLongOrNull()
            ReligiousScreen(
                dao = container.religiousDao,
                bookId = bId,
                chapterId = cId,
            )
        }

        route.startsWith("religious/book/") -> {
            val bId = route.removePrefix("religious/book/").toLongOrNull()
            ReligiousScreen(
                dao = container.religiousDao,
                bookId = bId,
                onOpenChapter = { book, chapter ->
                    nav.push("religious/chapter/${book.id}/${chapter.id}")
                },
            )
        }

        route == "religious" -> ReligiousScreen(
            dao = container.religiousDao,
            onOpenBook = { book -> nav.push("religious/book/${book.id}") },
        )

        route == "planner" -> PlannerScreen(dao = container.plannerDao, context = context)

        route == "notes" -> NotesScreen(dao = container.notesDao)

        route == "personal" -> PersonalHub(
            openNotes = { nav.push("notes") },
            openDiary = { nav.push("diary") },
            openGallery = { nav.push("gallery") },
            openReligious = { nav.push("religious") },
        )

        route == "diary" -> DiaryScreen(
            repo = container.diaryRepo,
            lock = diaryLock,
            hasPin = { container.hasDiaryPin() },
            verifyPin = { container.verifyDiaryPin(it) },
            biometric = com.salehilove.core.security.BiometricHelper(context),
        )

        route.startsWith("gallery/album/") -> {
            val aId = route.removePrefix("gallery/album/").toLongOrNull()
            GalleryScreen(
                dao = container.galleryDao,
                store = container.photoStore,
                profilePhotoPath = { container.profilePhotoPath() },
                onSetProfilePhoto = { container.setProfilePhoto(it) },
                backgroundPhotoPath = { container.backgroundPhotoPath() },
                onSetBackground = { container.setBackgroundPhoto(it) },
                albumId = aId,
            )
        }

        route == "gallery" -> GalleryScreen(
            dao = container.galleryDao,
            store = container.photoStore,
            profilePhotoPath = { container.profilePhotoPath() },
            onSetProfilePhoto = { container.setProfilePhoto(it) },
            backgroundPhotoPath = { container.backgroundPhotoPath() },
            onSetBackground = { container.setBackgroundPhoto(it) },
            onOpenAlbum = { album -> nav.push("gallery/album/${album.id}") },
        )

        route == "backup" -> BackupScreen(manager = container.backupManager)

        route == "settings" -> SettingsScreen(
            container = container,
            openBackup = { nav.push("backup") },
        )

        else -> DashboardScreen(
            container = container,
            openWords = { nav.push("words") },
            openReview = { nav.push("review") },
            openPlanner = { nav.push("planner") },
            openNotes = { nav.push("notes") },
            openDiary = { nav.push("diary") },
            openReligious = { nav.push("religious") },
        )
    }
}
