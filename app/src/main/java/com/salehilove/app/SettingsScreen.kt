package com.salehilove.app

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salehilove.core.designsystem.components.PinField
import com.salehilove.core.designsystem.components.SectionCard

/**
 * Settings: theme, diary lock (PIN change/remove), notification permission,
 * custom background reset, backup shortcuts and about.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    openBackup: () -> Unit,
) {
    val context = LocalContext.current
    var pinDialog by remember { mutableStateOf<String?>(null) } // set | change | remove
    var message by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(container.settings.getBoolean(SettingsKeys.DIARY_BIOMETRIC, true)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "ظاهر و حالت نمایش",
            subtitle = "پوسته طلایی هماهنگ در حالت روشن و تاریک",
            icon = Icons.Filled.Palette,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = container.themeMode == "light",
                    onClick = { container.applyThemeMode("light") },
                    label = { Text("روشن") },
                )
                FilterChip(
                    selected = container.themeMode == "dark",
                    onClick = { container.applyThemeMode("dark") },
                    label = { Text("تاریک") },
                )
                FilterChip(
                    selected = container.themeMode == "system",
                    onClick = { container.applyThemeMode("system") },
                    label = { Text("هماهنگ با سیستم") },
                )
            }
            if (container.backgroundPhotoPath() != null) {
                OutlinedButton(
                    onClick = {
                        container.setBackgroundPhoto(null)
                        message = "پس‌زمینه سفارشی حذف شد"
                    },
                ) {
                    Text("حذف پس‌زمینه سفارشی")
                }
            }
        }

        SectionCard(
            title = "قفل دفترچه خاطرات",
            subtitle = if (container.hasDiaryPin()) "رمز عبور اختصاصی فعال است" else "رمزی تنظیم نشده است",
            icon = Icons.Filled.Lock,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (container.hasDiaryPin()) {
                    Button(onClick = { pinDialog = "change" }) { Text("تغییر رمز") }
                    OutlinedButton(onClick = { pinDialog = "remove" }) { Text("حذف رمز") }
                } else {
                    Button(onClick = { pinDialog = "set" }) { Text("تعیین رمز") }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("بازکردن سریع با اثر انگشت / چهره", Modifier.weight(1f))
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { enabled ->
                        biometricEnabled = enabled
                        container.settings.putBoolean(SettingsKeys.DIARY_BIOMETRIC, enabled)
                    },
                )
            }
        }

        SectionCard(
            title = "پشتیبان‌گیری و بازیابی",
            subtitle = "خروجی رمزنگاری‌شده از همه داده‌ها و بازیابی روی هر دستگاهی",
            icon = Icons.Filled.Backup,
            onClick = openBackup,
        ) {
            Button(onClick = openBackup, modifier = Modifier.fillMaxWidth()) {
                Text("مدیریت پشتیبان‌گیری و بازیابی")
            }
        }

        SectionCard(
            title = "اعلان‌ها و یادآورها",
            subtitle = "برای نمایش به‌موقع یادآورها، اعلان‌های برنامه را فعال نگه دارید",
            icon = Icons.Filled.Notifications,
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("تنظیمات اعلان‌های دستگاه")
            }
        }

        SectionCard(
            title = "درباره SalehiLove ❤️",
            subtitle = "نسخه ۱٫۰ — ساخته‌شده با ❤️ برای یادگیری، برنامه‌ریزی و رشد روزانه",
            icon = Icons.Filled.Favorite,
        ) {
            Text(
                "همه داده‌ها به‌صورت کاملاً آفلاین و امن روی همین دستگاه ذخیره می‌شوند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    pinDialog?.let { mode ->
        var pin by remember(mode) { mutableStateOf("") }
        var confirm by remember(mode) { mutableStateOf("") }
        var current by remember(mode) { mutableStateOf("") }
        var error by remember(mode) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { pinDialog = null },
            title = {
                Text(
                    when (mode) {
                        "set" -> "تعیین رمز دفترچه"
                        "change" -> "تغییر رمز دفترچه"
                        else -> "حذف رمز دفترچه"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode == "change") {
                        PinField("رمز فعلی", current, { current = it; error = null }, errorMessage = error)
                    }
                    if (mode != "remove") {
                        PinField("رمز جدید (۴ تا ۸ رقم)", pin, { pin = it; error = null }, errorMessage = error)
                        PinField("تکرار رمز جدید", confirm, { confirm = it; error = null }, errorMessage = error)
                    } else {
                        Text("با حذف رمز، دفترچه خاطرات بدون رمز عبور باز می‌شود.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        when (mode) {
                            "set" -> {
                                require(pin.length in 4..8) { "رمز باید ۴ تا ۸ رقم باشد" }
                                require(pin == confirm) { "رمزها یکسان نیستند" }
                                container.setDiaryPin(pin)
                                message = "رمز دفترچه تنظیم شد"
                            }
                            "change" -> {
                                require(pin.length in 4..8) { "رمز باید ۴ تا ۸ رقم باشد" }
                                require(pin == confirm) { "رمزها یکسان نیستند" }
                                if (!container.verifyDiaryPin(current)) error = "رمز فعلی نادرست است"
                                else {
                                    container.setDiaryPin(pin)
                                    message = "رمز دفترچه تغییر کرد"
                                }
                            }
                            "remove" -> {
                                container.clearDiaryPin()
                                message = "رمز دفترچه حذف شد"
                            }
                        }
                        if (error == null) pinDialog = null
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                }) { Text("تأیید", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pinDialog = null }) { Text("انصراف") } },
        )
    }
}
