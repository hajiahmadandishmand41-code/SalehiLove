package com.salehilove.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salehilove.core.common.AppResult
import com.salehilove.core.common.PersianCalendar
import com.salehilove.core.designsystem.components.PinField
import com.salehilove.core.designsystem.components.SectionCard
import kotlinx.coroutines.launch

private data class BackupUi(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Backup & restore screen. Both flows ask for a password first; the actual
 * file is picked/created with SAF so the app needs zero storage permissions.
 * Restore is transactional — on any error nothing changes.
 */
@Composable
fun BackupScreen(manager: BackupManager) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(BackupUi()) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingExport
        pendingExport = null
        if (uri == null || password == null) return@rememberLauncherForActivityResult
        ui = ui.copy(busy = true, message = null)
        scope.launch {
            when (val result = manager.exportTo(uri, password.toCharArray())) {
                is AppResult.Success ->
                    ui = ui.copy(
                        busy = false,
                        message = "پشتیبان ساخته شد (${PersianCalendar.toPersianDigits(formatKb(result.value))} کیلوبایت)",
                    )
                is AppResult.Failure ->
                    ui = ui.copy(busy = false, message = "خطا در پشتیبان‌گیری: ${result.error.message}", isError = true)
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val password = pendingRestore
        pendingRestore = null
        if (uri == null || password == null) return@rememberLauncherForActivityResult
        ui = ui.copy(busy = true, message = null)
        scope.launch {
            when (val result = manager.restoreFrom(uri, password.toCharArray())) {
                is AppResult.Success -> {
                    val r = result.value
                    ui = ui.copy(
                        busy = false,
                        message = "بازیابی کامل شد: " + listOf(
                            "واژه ${r.words}",
                            "کتاب ${r.books}",
                            "فصل ${r.chapters}",
                            "یادداشت ${r.notes}",
                            "خاطره ${r.diary}",
                            "وظیفه ${r.tasks}",
                            "یادآور ${r.reminders}",
                            "عکس ${r.photos}",
                        ).joinToString("، "),
                    )
                }
                is AppResult.Failure ->
                    ui = ui.copy(
                        busy = false,
                        message = when {
                            result.error.message?.contains("password", ignoreCase = true) == true ->
                                "رمز پشتیبان نادرست است"
                            else -> "خطا در بازیابی: ${result.error.message}"
                        },
                        isError = true,
                    )
            }
        }
    }

    var passwordDialogFor by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "همه داده‌های SalehiLove ❤️ (واژه‌ها، مطالعات دینی، یادداشت‌ها، خاطرات، برنامه‌ها و عکس‌ها) در یک فایل رمزنگاری‌شده " +
                "با AES-256-GCM ذخیره می‌شود و با همان رمز روی هر دستگاهی قابل بازیابی است.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(
            title = "ساخت نسخه پشتیبان",
            subtitle = "خروجی رمزنگاری‌شده روی حافظه دستگاه یا هر فضای دلخواه",
            icon = Icons.Filled.Backup,
        ) {
            Button(
                onClick = { passwordDialogFor = "export" },
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ساخت فایل پشتیبان") }
        }

        SectionCard(
            title = "بازیابی از نسخه پشتیبان",
            subtitle = "بازیابی تراکنشی داده‌ها از فایل پشتیبان",
            icon = Icons.Filled.Restore,
        ) {
            Text(
                "توجه: بازیابی، داده‌های فعلی را با محتوای فایل پشتیبان جایگزین می‌کند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = { passwordDialogFor = "restore" },
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("انتخاب فایل و بازیابی") }
        }

        if (ui.busy) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }
        ui.message?.let {
            Text(
                it,
                color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    passwordDialogFor?.let { mode ->
        var password by remember(mode) { mutableStateOf("") }
        var confirm by remember(mode) { mutableStateOf("") }
        var error by remember(mode) { mutableStateOf<String?>(null) }
        val isExport = mode == "export"
        AlertDialog(
            onDismissRequest = { passwordDialogFor = null },
            title = { Text(if (isExport) "رمز نسخه پشتیبان" else "رمز فایل پشتیبان", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinField(
                        label = "رمز عبور (۴ تا ۸ رقم)",
                        value = password,
                        onValueChange = { password = it; error = null },
                        errorMessage = error,
                    )
                    if (isExport) {
                        PinField(
                            label = "تکرار رمز عبور",
                            value = confirm,
                            onValueChange = { confirm = it; error = null },
                            errorMessage = error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (password.length < 4) {
                        error = "رمز حداقل ۴ رقم باشد"
                        return@TextButton
                    }
                    if (isExport && password != confirm) {
                        error = "رمزها یکسان نیستند"
                        return@TextButton
                    }
                    passwordDialogFor = null
                    if (isExport) {
                        pendingExport = password
                        createFile.launch("salehilove-backup-${System.currentTimeMillis()}.slbk")
                    } else {
                        pendingRestore = password
                        confirmRestore = true
                    }
                }) { Text("ادامه", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { passwordDialogFor = null }) { Text("انصراف") } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("تأیید بازیابی", fontWeight = FontWeight.Bold) },
            text = {
                Text("داده‌های فعلی با محتوای فایل پشتیبان جایگزین می‌شود. مطمئن هستید؟")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    if (pendingRestore != null) openFile.launch(arrayOf("*/*"))
                }) { Text("انتخاب فایل و بازیابی", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false; pendingRestore = null }) { Text("انصراف") }
            },
        )
    }
}

private fun formatKb(bytes: Long): String =
    ((bytes + 1023) / 1024).toString()
