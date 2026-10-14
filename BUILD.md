# SalehiLove ❤️ — گزارش ساخت (BUILD.md)

تاریخ: ۲۰۲۶-۱۰-۱۴ (آخرین بیلد و تست موفق)

## نتیجه در یک نگاه

| مورد | وضعیت | مدرک |
|---|---|---|
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL | `apk/app-debug.apk` و `app/build/outputs/apk/debug/app-debug.apk` (۱۵٫۷ MB، ۱۴ dex) |
| `gradle test :app:assembleDebug` | ✅ BUILD SUCCESSFUL | خروجی گریدل در همین سشن |
| تست‌های واحد JVM | ✅ **۶۵ تست، ۰ خطا** | `*/build/test-results/testDebugUnitTest/*.xml` |
| تصویر بنر و لوگوی برنامه | ✅ تصویر واقعی از `HAvoice` | `assets/img/instructor.jpg` (`md5=bae128cbf855ac1cc33fc70f2e117959`) در `drawable-nodpi/salehi_portrait.jpg` و `mipmap-*/ic_launcher*.png` |
| نام و برندینگ برنامه | ✅ `SalehiLove ❤️` | در `strings.xml`، `DashboardScreen`، `MainActivity`، `SettingsScreen`، `BackupScreen` و `ReminderScheduler` |

آمار تست به تفکیک ماژول (از فایل‌های XML نتیجه):

- `core:common` → ۱۴ تست (`JsonTest`: ۸ تست + `PersianCalendarTest`: ۶ تست)
- `core:database` → ۳۰ تست (`DaoTest`: ۱۶ تست، `BackupCodecTest`: ۴ تست، `QuizGeneratorTest`: ۴ تست، `WordRepositorySrsTest`: ۴ تست، `LeitnerSrsTest`: ۲ تست)
- `core:security` → ۱۳ تست (`BackupCryptoTest`: ۵ تست، `CryptoEngineTest`: ۴ تست، `PinManagerTest`: ۴ تست)
- `core:designsystem` → ۸ تست (`AppNavTest`: push، جلوگیری از تکرار مسیر فعلی، pop، popUpTo، replace، resetTo، serialize/deserialize)

## دستور بیلد (محیط sandbox آفلاین)

```bash
cd /home/user/SalehiLove
export JAVA_HOME=/home/user/android-build/tools/jdk-21 \
       ANDROID_HOME=/home/user/android-build/sdk \
       GRADLE_USER_HOME=/home/user/android-build/gradle-home \
       SALEHILOVE_OFFLINE_M2=/home/user/android-build/m2repo \
       SALEHILOVE_JAVAC_SHIM=/home/user/android-build/tools/jdk-21/bin/javac
/home/user/android-build/tools/gradle-8.11.1/bin/gradle --no-daemon --offline test :app:assembleDebug
```

روی ماشین عادی با اینترنت و Android Studio، همین پروژه بدون هیچ‌کدام از این متغیرها بیلد می‌شود (فایل `scripts/offline-conventions.gradle` فقط در حالت آفلاین فعال می‌شود).

## معماری و پشته

- **Kotlin 1.9.22 + Jetpack Compose** (BOM 2024.02.02 / compose 1.6.3 / material3 1.2.1 / compiler-ext 1.5.8)، AGP 8.9.0، minSdk 26، target/compile 35، buildTools 35.0.0
- ماژول‌ها: `app` + `feature/{learning,personal,planner,backup}` + `core/{common,designsystem,database,security}` — تمیز و ماژولار
- ناوبری دست‌ساز `AppNav` با حفظ تاریخچه کامل مسیرها + دکمه بازگشت در `TopAppBar` صفحات داخلی و `BackHandler` سیستمی، لایه‌ی دیتابیس `RawDatabase` روی `SQLiteOpenHelper` خام، DI دستی `AppContainer`
- رابط کاربری فارسی RTL (`LayoutDirection.Rtl`)، فونت Vazirmatn، تم یکدست طلایی (`#B8892E` / `#D9B66C`) با پس‌زمینه ساده و کارت‌های استاندارد `AppCard` و `SectionCard` در حالت روشن/تاریک/سیستم

## ۹ ویژگی — پیاده‌سازی واقعی

1. **صفحه اصلی (`app/DashboardScreen.kt`)** — بنر بالای صفحه با تصویر واقعی استاد حاجی احمد صالحی (`R.drawable.salehi_portrait` از مخزن `HAvoice`)، کارت اختصاصی بخش واژگان، خلاصه‌ی برنامه‌ی امروز + پیشرفت، یادآور بعدی، مطالعات دینی و آخرین یادداشت‌ها؛ همه از دیتابیس زنده
2. **بخش اختصاصی واژگان (`feature/learning/WordsScreen+ReviewScreen+QuizScreen`)** — جدول منظم و هم‌تراز واژگان انگلیسی همراه با تلفظ، نقش دستوری، تلفظ صوتی (TTS)، معنی فارسی، شماره جعبه لایتنر و عملیات سریع (نشان‌دار کردن، ویرایش، حذف)، جست‌وجوی فارسی-انگلیسی، فیلتر موضوع و منتخب‌ها، مرور با فاصله‌ی زمانی (جعبه‌های لایتنر، persisted)، آزمون چهارگزینه‌ای دوطرفه از دیتای واقعی (همراه با ۵۰ واژه پیش‌فرض)
3. **مطالعات دینی (`feature/learning/ReligiousScreen`)** — کتاب/فصل CRUD کامل با مسیرهای مستقل ناوبری (`religious`، `religious/book/{id}`، `religious/chapter/{bookId}/{chapterId}`)، متن عربی + ترجمه + توضیح + خلاصه‌ی امتحان + نکات کلیدی + شماره صفحه، علامت خوانده‌شده/منتخب
4. **یادداشت‌ها (`feature/personal/NotesScreen`)** — CRUD، جست‌وجو (عنوان/متن/برچسب)، دسته‌بندی، برچسب‌ها، سنجاق و کارت‌های یکدست طلایی
5. **دفترچه خاطرات خصوصی (`feature/personal/DiaryScreen` + `core/security`)** — PIN جداگانه (PBKDF2-SHA256، ۱۲۰k تکرار)، **رمزنگاری واقعی Android Keystore + AES-256-GCM** (متن ساده هرگز ذخیره نمی‌شود)، بازکردن با PIN یا BiometricPrompt پلتفرمی، قفل خودکار ۲ دقیقه‌ای + قفل روی خروج از برنامه
6. **برنامه‌ریزی (`feature/planner`)** — وظایف روزانه/هفتگی با تکرار، وضعیت تکمیل، نوار پیشرفت روز، تاریخ شمسی/میلادی در ورودی؛ **یادآورهای محلی صبح/ظهر/شب** با `AlarmManager.setExactAndAllowWhileIdle` + گیرنده‌های BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED
7. **گالری (`feature/personal/GalleryScreen`)** — آلبوم‌ها با مسیر مستقل (`gallery/album/{id}`)، درج عکس از طریق SAF (بدون هیچ مجوز ذخیره‌سازی)، حذف واقعی فایل+ردیف، عکس پروفایل و پس‌زمینه‌ی سفارشی، توضیح عکس
8. **پشتیبان‌گیری (`feature/backup` + `BackupCodec`/`BackupCrypto`)** — خروجی/بازیابی کل دیتابیس در ظرف **AES-256-GCM با کلید مشتق‌شده از رمز (PBKDF2-SHA256 + salt تصادفی)** — قابل انتقال بین دستگاه‌ها؛ بازیابی تراکنشی است
9. **تنظیمات (`app/SettingsScreen`)** — حالت نمایش، تعیین/تغییر/حذف PIN دفترچه، فعال‌سازی بیومتریک، تنظیمات اعلان‌ها، دسترسی پشتیبان‌گیری، درباره `SalehiLove ❤️`

## بازتولید toolchain

کل زنجیره‌ی ساخت آفلاین (گریدل، JRE، m2، SDK، probe) با `scripts/rebuild-toolchain.sh` قابل بازسازی است — جزئیات در آن فایل.
