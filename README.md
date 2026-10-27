# ویدیو به MP3 (VideoToMp3)

اپ اندروید برای **جداسازی صدای ویدیو و تبدیل آن به MP3**، کاملاً **آفلاین** و **بدون سرور**.
تمام پردازش روی خود گوشی با موتور FFmpeg (کدک `libmp3lame`) انجام می‌شود؛ هیچ فایلی آپلود نمی‌شود و به اینترنت نیازی نیست.

## امکانات
- انتخاب هر ویدیویی از حافظهٔ گوشی (mp4, mkv, avi, mov, …)
- خروجی MP3 واقعی با انتخاب کیفیت: ۱۲۸ / ۱۹۲ / ۳۲۰ کیلوبیت
- نمایش درصد پیشرفت
- اشتراک‌گذاری خروجی یا ذخیره در پوشهٔ موزیک
- رابط کاربری فارسی و راست‌چین، پشتیبانی از تم تاریک
- حداقل نسخهٔ اندروید: ۷.۰ (API 24)

---

## چطور APK را بگیرم؟

به‌دلیل محدودیت‌های محیط ساخت، فایل APK کامپایل‌شده همراه این پروژه نیست؛ اما ساختش بسیار ساده است. دو راه دارید:

### راه ۱ (بدون نصب هیچ ابزاری) — ساخت خودکار در GitHub
۱. یک مخزن (repository) خالی در GitHub بسازید.
۲. محتویات همین پوشه را در آن مخزن `push` کنید (یا فایل zip را extract و آپلود کنید).
۳. وارد تب **Actions** شوید؛ workflow با نام **Build APK** به‌صورت خودکار اجرا می‌شود (یا دستی Run workflow را بزنید).
۴. پس از چند دقیقه، در بخش **Artifacts** همان اجرا، فایل **`VideoToMp3-debug-apk`** را دانلود کنید. داخلش `app-debug.apk` است.
۵. APK را روی گوشی نصب کنید (نصب از منابع نامشخص را فعال کنید).

### راه ۲ — ساخت روی سیستم خودتان
نیازمند: **JDK 17** و **Android SDK** (مثلاً از طریق Android Studio).

```bash
# در ریشهٔ پروژه:
./gradlew assembleDebug        # لینوکس / مک
gradlew.bat assembleDebug      # ویندوز
```

APK ساخته‌شده اینجاست:
```
app/build/outputs/apk/debug/app-debug.apk
```

یا پروژه را مستقیماً در **Android Studio** باز کنید و دکمهٔ Run ▶ را بزنید.

> برای ساخت نسخهٔ قابل‌انتشار و امضاشده: `./gradlew assembleRelease` (نیازمند تعریف keystore).

---

## نکتهٔ فنی دربارهٔ وابستگی FFmpeg
این پروژه از بستهٔ `com.arthenica:ffmpeg-kit-audio:6.0-2.LTS` استفاده می‌کند که شامل `libmp3lame` است و روی Maven Central منتشر شده (آرشیو Maven Central دائمی است و حذف نمی‌شود).
اگر زمانی این نسخه resolve نشد، در فایل `app/build.gradle` یکی از این جایگزین‌ها را امتحان کنید:

```gradle
implementation 'com.arthenica:ffmpeg-kit-audio:6.0-2'
// یا بستهٔ کامل‌تر:
implementation 'com.arthenica:ffmpeg-kit-full-gpl:6.0-2.LTS'
```

## ساختار پروژه
```
app/src/main/java/com/appoup/videotomp3/MainActivity.java   ← منطق اصلی تبدیل
app/src/main/res/layout/activity_main.xml                   ← رابط کاربری
app/build.gradle                                            ← وابستگی‌ها
.github/workflows/build.yml                                 ← ساخت خودکار APK
```
