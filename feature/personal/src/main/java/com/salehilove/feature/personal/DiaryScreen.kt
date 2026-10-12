package com.salehilove.feature.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.database.repo.DiaryRepository
import com.salehilove.core.designsystem.components.ConfirmDialog
import com.salehilove.core.designsystem.components.EmptyState
import com.salehilove.core.designsystem.components.PersianTextField
import com.salehilove.core.designsystem.components.PinField
import com.salehilove.core.security.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Diary state machine: locked / unlocked. Keeps the unlocked timestamp so the
 * diary auto-locks after [AUTO_LOCK_MS] of inactivity or when the app goes to
 * the background — the plaintext never survives longer than needed.
 */
class DiaryLockState {
    var unlockedUntil by mutableStateOf(0L)

    fun isUnlocked(now: Long = System.currentTimeMillis()): Boolean = now < unlockedUntil

    fun unlock(windowMs: Long = AUTO_LOCK_MS) {
        unlockedUntil = System.currentTimeMillis() + windowMs
    }

    fun lock() {
        unlockedUntil = 0L
    }

    companion object {
        const val AUTO_LOCK_MS = 2 * 60 * 1000L // 2 minutes
    }
}

/**
 * Private diary behind its own lock: PIN (PBKDF2-hashed, stored as a setting
 * by the app container) and biometric when the device supports it. Bodies are
 * AES-GCM-encrypted via Android Keystore before storage; the UI decrypts a
 * single entry at a time, in memory only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    repo: DiaryRepository,
    lock: DiaryLockState,
    hasPin: () -> Boolean,
    verifyPin: (String) -> Boolean,
    biometric: BiometricHelper,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Auto-lock when the app leaves the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) lock.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!lock.isUnlocked()) {
        DiaryGate(
            hasPin = hasPin(),
            biometricAvailable = biometric.isBiometricAvailable(),
            onPin = { pin -> verifyPin(pin) },
            onBiometric = {
                biometric.authenticate(
                    activityContext = context,
                    title = "بازکردن دفترچه خاطرات",
                    subtitle = "هویت خود را تأیید کن",
                    negativeLabel = "انصراف",
                    onSuccess = { lock.unlock() },
                    onError = { },
                )
            },
            onUnlocked = { lock.unlock() },
        )
    } else {
        DiaryBody(
            repo = repo,
            onLock = { lock.lock() },
        )
    }
}

@Composable
private fun DiaryGate(
    hasPin: Boolean,
    biometricAvailable: Boolean,
    onPin: (String) -> Boolean,
    onBiometric: () -> Unit,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Offer biometric right away when there's no PIN yet or as a shortcut.
        if (biometricAvailable && !hasPin) onBiometric()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Lock, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("دفترچه خاطرات خصوصی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "این بخش با رمزنگاری واقعی (AES-GCM) محافظت می‌شود.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        if (hasPin) {
            PinField(
                label = "رمز عبور دفترچه",
                value = pin,
                onValueChange = { pin = it; error = null },
                errorMessage = error,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                if (pin.length < 4) {
                    error = "رمز عبور حداقل ۴ رقم است"
                    return@TextButton
                }
                if (onPin(pin)) {
                    pin = ""
                    attempts = 0
                    onUnlocked()
                } else {
                    attempts++
                    error = "رمز عبور نادرست است (تلاش ${PersianCalendar.toPersianDigits(attempts.toString())})"
                    pin = ""
                }
            }) { Text("بازکردن") }
            if (biometricAvailable) {
                OutlinedButton(onClick = onBiometric) { Text("بازکردن با اثر انگشت / چهره") }
            }
        } else {
            Text(
                "هنوز رمزی برای دفترچه تنظیم نشده.\nاز تنظیمات یک رمز عبور جداگانه بساز.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (biometricAvailable) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBiometric) { Text("بازکردن با قفل دستگاه (اثر انگشت/چهره)") }
            }
        }
    }
}

private data class DiaryUi(
    val entries: List<DiaryRepository.DecryptedEntry> = emptyList(),
    val query: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryBody(
    repo: DiaryRepository,
    onLock: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(DiaryUi()) }
    var editor by remember { mutableStateOf<DiaryRepository.DecryptedEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DiaryRepository.DecryptedEntry?>(null) }

    fun reload() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (ui.query.isBlank()) repo.all().map { repo.decrypt(it) }
                    else repo.searchTitles(ui.query).map { repo.decrypt(it) }
                }
            }.onSuccess { ui = ui.copy(entries = it) }
        }
    }

    LaunchedEffect(ui.query) { reload() }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("دفترچه خاطرات", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onLock) { Text("قفل کردن") }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "خاطره جدید") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { ui = ui.copy(query = it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("جست‌وجو در عنوان خاطرات…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            if (ui.entries.isEmpty()) {
                EmptyState("خاطره‌ای ثبت نشده — با + اولین خاطره را بنویس")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.entries, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title.ifBlank { "(بی‌عنوان)" }, fontWeight = FontWeight.Bold)
                                    Text(
                                        entry.content.take(60) + if (entry.content.length > 60) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        PersianCalendar.formatDate(entry.entryDate) +
                                            if (entry.mood.isNotBlank()) "  ${entry.mood}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { editor = entry }) { Icon(Icons.Filled.Edit, "ویرایش") }
                                IconButton(onClick = { deleting = entry }) {
                                    Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editor != null) {
        val existing = editor
        DiaryEditorDialog(
            initial = existing,
            onDismiss = { creating = false; editor = null },
            onSave = { title, content, mood, date ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (existing == null) {
                                repo.saveNew(title, content, mood, date)
                            } else {
                                repo.saveUpdate(existing.id, title, content, mood, date, existing.photoPaths)
                            }
                        }
                    }
                    creating = false; editor = null
                    reload()
                }
            },
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = "حذف خاطره",
            message = "خاطره «${entry.title.ifBlank { "(بی‌عنوان)" }}» برای همیشه حذف شود؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.delete(entry.id) }
                    deleting = null
                    reload()
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun DiaryEditorDialog(
    initial: DiaryRepository.DecryptedEntry?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, mood: String, date: java.time.LocalDate) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var mood by remember { mutableStateOf(initial?.mood ?: "") }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "خاطره جدید" else "ویرایش خاطره") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersianTextField("عنوان", title, { title = it })
                PersianTextField("متن خاطره", content, { content = it }, singleLine = false, minLines = 6)
                PersianTextField("حال و هوا (😊 😢 …)", mood, { mood = it })
                if (validation != null) Text(validation!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (content.isBlank()) {
                    validation = "متن خاطره الزامی است"
                    return@TextButton
                }
                onSave(title.trim(), content, mood.trim(), initial?.entryDate ?: java.time.LocalDate.now())
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
