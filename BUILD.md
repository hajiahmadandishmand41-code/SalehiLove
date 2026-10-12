# صالحی‌لاو — گزارش ساخت (BUILD.md)

تاریخ: ۲۰۲۶-۱۰-۱۲ (آخرین بیلد موفق در همین روز)

## نتیجه در یک نگاه

| مورد | وضعیت | مدرک |
|---|---|---|
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL | `apk/app-debug.apk` (۱۵٫۷ MB، ۱۵۷ ورودی، ۱۴ dex) |
| `gradle build` (همه ماژول‌ها + تست‌ها) | ✅ BUILD SUCCESSFUL | خروجی گریدل در همین سشن |
| تست‌های واحد JVM | ✅ **۶۴ تست، ۰ خطا** | `*/build/test-results/testDebugUnitTest/*.xml` |
| امضای APK | ✅ v2 signing block | magic «APK Sig Block 42» + شناسه‌ی scheme `0x7109871a` |
| APK release (unsigned) | ✅ ساخته شد | `app/build/outputs/apk/release/app-release-unsigned.apk` |

آمار تست به تفکیک ماژول (از فایل‌های XML نتیجه):

- `core:common` → ۱۴ تست (Json دست‌ساز: roundtrip، فارسی، escape، اعداد، خطاها + PersianCalendar)
- `core:database` → ۳۰ تست (DAOها، SRS، BackupCodec v1 با Json جدید، schema)
- `core:security` → ۱۳ تست (AES-GCM vault، PIN PBKDF2، BackupCrypto)
- `core:designsystem` → ۷ تست (AppNav: push/pop/popUpTo/serialize)

## دستور بیلد (محیط sandbox آفلاین)

```bash
cd /home/user/SalehiLove
export JAVA_HOME=/home/user/android-build/tools/jdk-21 \
       GRADLE_USER_HOME=/home/user/android-build/gradle-home \
       SALEHILOVE_OFFLINE_M2=/home/user/android-build/m2repo \
       SALEHILOVE_JAVAC_SHIM=/home/user/android-build/tools/jdk-21/bin/javac
/home/user/android-build/tools/gradle-8.11.1/bin/gradle --no-daemon --offline :app:assembleDebug
```

روی ماشین عادی با اینترنت و Android Studio، همین پروژه بدون هیچ‌کدام از این متغیرها بیلد می‌شود (فایل `scripts/offline-conventions.gradle` فقط در حالت آفلاین فعال می‌شود؛ lint هم فقط در sandbox غیرفعال است چون `lint-gradle:31.9.0` در mirror نیست).

## معماری و پشته

- **Kotlin 1.9.22 + Jetpack Compose** (BOM 2024.02.02 / compose 1.6.3 / material3 1.2.1 / compiler-ext 1.5.8)، AGP 8.9.0، minSdk 26، target/compile 35، buildTools 35.0.0
- ماژول‌ها: `app` + `feature/{learning,personal,planner,backup}` + `core/{common,designsystem,database,security}` — تمیز و ماژولار
- **بدون** androidx.navigation / Room / Hilt / appcompat — به‌جایشان: ناوبری دست‌ساز `AppNav` (تست‌شده)، لایه‌ی دیتابیس `RawDatabase` روی `SQLiteOpenHelper` خام (تست‌شده با JDBC در JVM)، DI دستی `AppContainer`
- رابط کاربری فارسی RTL (اجبار `LayoutDirection.Rtl` مستقل از زبان دستگاه)، فونت Vazirmatn، پالت سبز تیره + کرم + طلایی، حالت روشن/تاریک/سیستم

## ۹ ویژگی — پیاده‌سازی واقعی

1. **داشبورد** (`app/DashboardScreen.kt`) — خلاصه‌ی برنامه‌ی امروز + پیشرفت، یادآور بعدی، آمار واژه/مرور/خاطره، آخرین یادداشت‌ها؛ همه از دیتابیس زنده
2. **یادگیری زبان** (`feature/learning/WordsScreen+ReviewScreen+QuizScreen`) — CRUD کامل واژه (واژه/معنی/مثال+ترجمه/تلفظ/بخش گفتار/موضوع)، جست‌وجوی فارسی-انگلیسی، فیلتر موضوع و منتخب‌ها، مرور با فاصله‌ی زمانی (جعبه‌های لایتنر، persisted)، آزمون چهارگزینه‌ای از دیتای واقعی
3. **مطالعات دینی** (`feature/learning/ReligiousScreen`) — کتاب/فصل CRUD کامل، متن عربی + ترجمه + توضیح + خلاصه‌ی امتحان + نکات کلیدی + شماره صفحه، علامت خوانده‌شده/منتخب
4. **یادداشت‌ها** (`feature/personal/NotesScreen`) — CRUD، جست‌وجو (عنوان/متن/برچسب)، دسته‌بندی، برچسب‌ها، رنگ، سنجاق
5. **دفترچه خاطرات خصوصی** (`feature/personal/DiaryScreen` + `core/security`) — PIN جداگانه (PBKDF2-SHA256، ۱۲۰k تکرار)، **رمزنگاری واقعی Android Keystore + AES-256-GCM** (متن ساده هرگز ذخیره نمی‌شود)، بازکردن با PIN یا BiometricPrompt پلتفرمی، قفل خودکار ۲ دقیقه‌ای + قفل روی خروج از برنامه
6. **برنامه‌ریزی** (`feature/planner`) — وظایف روزانه/هفتگی با تکرار، وضعیت تکمیل، نوار پیشرفت روز، تاریخ شمسی/میلادی در ورودی؛ **یادآورهای محلی صبح/ظهر/شب** با `AlarmManager.setExactAndAllowWhileIdle` + گیرنده‌های BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED (باززمان‌بندی مقاوم در برابر ریبوت/تغییر ساعت) + کانال نوتیفیکیشن
7. **گالری** (`feature/personal/GalleryScreen`) — آلبوم‌ها، درج عکس از طریق SAF (بدون هیچ مجوز ذخیره‌سازی)، حذف واقعی فایل+ردیف، عکس پروفایل و پس‌زمینه‌ی سفارشی، توضیح عکس
8. **پشتیبان‌گیری** (`feature/backup` + `BackupCodec`/`BackupCrypto`) — خروجی/بازیابی کل دیتابیس (واژه، کتاب، فصل، یادداشت، خاطره، وظیفه، یادآور، آلبوم، عکس‌ها به‌صورت Base64 + تنظیمات) در ظرف **AES-256-GCM با کلید مشتق‌شده از رمز (PBKDF2-SHA256 + salt تصادفی)** — قابل انتقال بین دستگاه‌ها؛ بازیابی تراکنشی است (خطا = بدون تغییر داده)؛ تشخیص فایل نامعتبر و رمز غلط
9. **تنظیمات** (`app/SettingsScreen`) — حالت نمایش، تعیین/تغییر/حذف PIN دفترچه، فعال‌سازی بیومتریک، تنظیمات اعلان‌ها، دسترسی پشتیبان‌گیری، درباره

## امنیت و مجوزها

- مجوزها فقط: `POST_NOTIFICATIONS`، `RECEIVE_BOOT_COMPLETED`، `SCHEDULE_EXACT_ALARM`، `USE_BIOMETRIC`، `VIBRATE` — بدون اینترنت، بدون ذخیره‌سازی، بدون حساب کاربری
- خاطرات: فقط ciphertext (Keystore AES-GCM) در DB؛ کلید هرگز خارج از Keystore نیست
- پشتیبان: کل فایل رمزنگاری‌شده؛ رمز ذخیره نمی‌شود؛ `allowBackup=false`
- اعتبارسنجی ورودی در همه‌ی فرم‌ها (عنوان الزامی، قالب تاریخ/ساعت، طول PIN، رمز پشتیبان)

## چیزهایی که صادقانه آزمایش‌نشده/ناقص‌اند

1. **تست روی دستگاه/امولاتور واقعی انجام نشده** — همه‌ی تست‌ها JVM واحد هستند؛ رفتان روی UI (Compose UI test،Espresso) و مسیرهای Android واقعی (Keystore سخت‌افزاری، BiometricPrompt، AlarmManager در ریبوت واقعی، SAF روی اندروید واقعی) فقط کدنویسی شده‌اند و روی گوشی آزمایش نشده‌اند. دلیل: sandbox بدون emulator.
2. **lint اجرا نمی‌شود در sandbox** (نبود `lint-gradle:31.9.0` در mirror آفلاین؛ روی ماشین آنلاین اجرا خواهد شد).
3. **لوگو موقت**: آیکون فعلی تولیدی است (`art/logo-master.png` → mipmaps). لوگوی اصلی کاربر (فایل پیوست قبلی) با ریست sandbox از دست رفت؛ به‌محض ارسال مجدد، جایگزین و بیلد تازه می‌شود.
4. **آزمون Quiz** به ≥۴ واژه نیاز دارد و از حافظه‌ی کوتاه‌مدت سؤال می‌سازد (persist نمی‌شود) — طراحی، نه باگ.
5. **یادآورها در حالت حذف SCHEDULE_EXACT_ALARM** به آلارم غیردقیق fallback می‌کنند (رفتار درست، ولی باید روی دستگاه تست شود).
6. **رشته‌های UI**: برچسب‌های اصلی (نام اپ، ناوبری، متن یادآورها) در `values/strings.xml` (فارسی) و `values-en/` (انگلیسی) هستند؛ متن داخل صفحات فارسی hard-code شده — i18n کامل صفحات انجام نشده.
7. **نسخه‌ی release فقط unsigned** ساخته شده (کلید امضای release از کاربر لازم است).

## بازتولید toolchain

کل زنجیره‌ی ساخت آفلاین (گریدل، JRE، m2، SDK، probe) با `scripts/rebuild-toolchain.sh` قابل بازسازی است — جزئیات در آن فایل.
