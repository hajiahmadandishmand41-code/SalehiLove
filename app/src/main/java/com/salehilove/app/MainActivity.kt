package com.salehilove.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    LEARN("words", "یادگیری"),
    PLANNER("planner", "برنامه"),
    PERSONAL("personal", "شخصی"),
    MORE("settings", "تنظیمات"),
}

/** Hub for the personal features: notes, private diary and gallery. */
@Composable
private fun PersonalHub(
    openNotes: () -> Unit,
    openDiary: () -> Unit,
    openGallery: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "بخش شخصی",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        com.salehilove.core.designsystem.components.SectionCard(
            title = "یادداشت‌ها",
            subtitle = "نوشته‌ها با جست‌وجو، برچسب و دسته‌بندی",
            icon = Icons.Filled.MenuBook,
            onClick = openNotes,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        com.salehilove.core.designsystem.components.SectionCard(
            title = "دفترچه خاطرات خصوصی",
            subtitle = "محافظت‌شده با رمز و رمزنگاری واقعی",
            icon = Icons.Filled.Schedule,
            onClick = openDiary,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        com.salehilove.core.designsystem.components.SectionCard(
            title = "گالری",
            subtitle = "آلبوم‌ها، عکس پروفایل و پس‌زمینه سفارشی",
            icon = Icons.Filled.PhotoLibrary,
            onClick = openGallery,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(container: AppContainer) {
    val nav = rememberAppNav()
    val diaryLock = remember { DiaryLockState() }
    var activeTab by remember { mutableStateOf(Tab.HOME) }

    // Navigate on tab switch; back stack resets to the tab root.
    fun selectTab(tab: Tab) {
        activeTab = tab
        nav.resetTo(tab.route)
    }

    val current = nav.current
    val topLevel = current == activeTab.route

    Scaffold(
        topBar = {
            if (!topLevel) {
                TopAppBar(
                    title = { Text(titleFor(current)) },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab && topLevel,
                        onClick = { selectTab(tab) },
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
        // Custom background photo behind the content, if set.
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
                        alpha = 0.25f,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    )
                }
            }
            Surface(color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    AppRoute(
                        route = current,
                        container = container,
                        nav = nav,
                        diaryLock = diaryLock,
                        selectTab = { selectTab(it) },
                    )
                }
            }
        }
    }
}

private fun titleFor(route: String): String = when {
    route.startsWith("quiz") -> "آزمون واژگان"
    route.startsWith("review") -> "مرور واژگان"
    route.startsWith("religious") -> "مطالعات دینی"
    route.startsWith("diary") -> "دفترچه خاطرات"
    route.startsWith("gallery") -> "گالری"
    route.startsWith("backup") -> "پشتیبان‌گیری"
    route.startsWith("planner") -> "برنامه‌ریز"
    route.startsWith("words") -> "واژه‌ها"
    route.startsWith("notes") -> "یادداشت‌ها"
    else -> "صالحی‌لاو"
}

@Composable
private fun AppRoute(
    route: String,
    container: AppContainer,
    nav: AppNav,
    diaryLock: DiaryLockState,
    selectTab: (Tab) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    when {
        route == "home" -> DashboardScreen(
            container = container,
            openWords = { selectTab(Tab.LEARN) },
            openReview = { nav.push("review") },
            openPlanner = { selectTab(Tab.PLANNER) },
            openNotes = { selectTab(Tab.PERSONAL) },
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

        route == "religious" -> ReligiousScreen(dao = container.religiousDao)

        route == "planner" -> PlannerScreen(dao = container.plannerDao, context = context)

        route == "notes" -> NotesScreen(dao = container.notesDao)

        route == "personal" -> PersonalHub(
            openNotes = { nav.push("notes") },
            openDiary = { nav.push("diary") },
            openGallery = { nav.push("gallery") },
        )

        route == "diary" -> DiaryScreen(
            repo = container.diaryRepo,
            lock = diaryLock,
            hasPin = { container.hasDiaryPin() },
            verifyPin = { container.verifyDiaryPin(it) },
            biometric = com.salehilove.core.security.BiometricHelper(context),
        )

        route == "gallery" -> GalleryScreen(
            dao = container.galleryDao,
            store = container.photoStore,
            profilePhotoPath = { container.profilePhotoPath() },
            onSetProfilePhoto = { container.setProfilePhoto(it) },
            backgroundPhotoPath = { container.backgroundPhotoPath() },
            onSetBackground = { container.setBackgroundPhoto(it) },
        )

        route == "backup" -> BackupScreen(manager = container.backupManager)

        route == "settings" -> SettingsScreen(
            container = container,
            openBackup = { nav.push("backup") },
        )

        else -> DashboardScreen(
            container = container,
            openWords = { selectTab(Tab.LEARN) },
            openReview = { nav.push("review") },
            openPlanner = { selectTab(Tab.PLANNER) },
            openNotes = { selectTab(Tab.PERSONAL) },
            openDiary = { nav.push("diary") },
            openReligious = { nav.push("religious") },
        )
    }
}
