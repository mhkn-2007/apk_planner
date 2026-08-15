# LifeOS — گزارش کامل باگ‌ها و نواقص نسبت به Master Prompt

این فایل نتیجه‌ی مقایسه‌ی کد فعلی پروژه با متن کامل Master Prompt هست.
هر آیتم وضعیتش مشخص شده: 🔴 بحرانی | 🟠 مهم | 🟡 متوسط | ⚪️ کد مرده/ناقص

آخرین به‌روزرسانی: به‌صورت خودکار توسط Claude، هم‌زمان با پیشرفت فیکس‌ها این فایل آپدیت می‌شه.

---

## بخش ۱: باگ‌های فنی (چیزی که کد فعلی رو خراب می‌کنه)

### 🔴 ۱.۱ — بیلد Gradle Fail می‌شد
`app/build.gradle.kts` ورژن Room رو با `\$room_version` نوشته بود (بک‌اسلش اشتباه) که باعث می‌شد Gradle این رو به‌عنوان رشته‌ی literal بخونه، نه مقدار متغیر.
**وضعیت: فیکس شد.**

### 🔴 ۱.۲ — نبود پرمیشن رانتایم برای نوتیفیکیشن
با اینکه منیفست `POST_NOTIFICATIONS` رو داشت، هیچ‌جا این پرمیشن از کاربر در Android 13+ درخواست نمی‌شد. نتیجه: هیچ نوتیفیکیشنی نمایش داده نمی‌شد و کاربر هم دلیلش رو نمی‌فهمید.
**وضعیت: فیکس شد.**

### 🔴 ۱.۳ — صفحه‌ی «اهداف و پروژه‌ها» غیرقابل‌دسترسی بود
`ProjectsScreen.kt` کامل نوشته شده بود ولی هیچ route‌ای در `LifeOSNavigation.kt` نداشت و در bottom nav هم نبود.
**وضعیت: فیکس شد.**

### 🟠 ۱.۴ — منطق «کارهای امروز» اشتباه بود
`GetTodayTasksUseCase` به‌جای بازه‌ی start-of-day تا end-of-day، از یک پنجره‌ی غلطِ [الان−۲۴ساعت, الان+۲۴ساعت] استفاده می‌کرد.
**وضعیت: فیکس شد.**

### 🟠 ۱.۵ — تبدیل تاریخ جلالی به میلادی غلط بود
`CalendarScreen` به‌جای استفاده از یک تابع تبدیل واقعی، با فرمول تقریبی `year*365 + month*30` تاریخ رو حساب می‌کرد که با کبیسه بودن سال‌ها و طول متفاوت ماه‌ها drift پیدا می‌کرد.
**وضعیت: فیکس شد — تابع `jalaliToGregorian` واقعی به `JalaliCalendarUtil` اضافه شد.**

### 🟡 ۱.۶ — تقویم فقط ماه جاری رو نشون می‌داد
هیچ دکمه‌ی ماه بعد/قبل وجود نداشت و محاسبه‌ی تعداد روزهای اسفند کبیسه بودن رو در نظر نمی‌گرفت.
**وضعیت: فیکس شد.**

### 🟡 ۱.۷ — ناهماهنگی تم روشن/تاریک
`ProjectsScreen`, `AIChatScreen`, و `LifeOSBottomNav` رنگ‌های hardcoded (فقط حالت تاریک) داشتن به‌جای `MaterialTheme.colorScheme`.
**وضعیت: فیکس شد.**

### 🟠 ۱.۸ — عادت‌ها و کارها به هم وصل نبودن
تیک زدن تسکی که از یک عادت ساخته شده بود، هیچ اثری روی streak اون عادت نداشت.
**وضعیت: فیکس شد — تسک‌های ساخته‌شده از عادت الان `habitId`‌شون ذخیره می‌شه و تکمیل‌شون streak رو آپدیت می‌کنه.**

---

## بخش ۲: نواقص بزرگ نسبت به Master Prompt (فیچرهای کامل جا افتاده)

پرامپت اصلی یک Personal OS کامل با ۱۱ بخش ناوبری، سیستم AI Tool Layer، Routines، Goals→Milestones→Projects، Focus Mode، و Analytics توصیف کرده. نسخه‌ی فعلی فقط بخش کوچیکی از این رو داره.

### 🔴 ۲.۱ — بخش ۲۰-۴۱ (سیستم AI): تقریباً هیچ‌کدوم پیاده نشده
- `AIModule` همیشه `MockAIProvider` رو bind می‌کنه؛ هیچ Provider واقعی (Gemini) وجود نداره.
- `AIToolLayer` (که برای اجرای کنترل‌شده‌ی اکشن‌های AI ساخته شده) هیچ‌جا inject/استفاده نمی‌شه.
- هیچ‌کدوم از Read Tools بخش ۲۱ (`get_tasks`, `get_today_tasks`, `get_goals`, ...) وجود نداره.
- هیچ‌کدوم از Action Tools بخش ۲۲ به‌جز `createTask`, `rescheduleTask`, `deleteLowPriorityTasks` پیاده نشده.
- AI Daily/Weekly Planning (بخش ۲۳، ۲۴)، Task Breakdown (۲۶)، Rescheduling (۲۷)، Routine Creation (۲۸) هیچ‌کدوم پیاده نشدن.
- `AIChatScreen` به‌جای استفاده از AI Tool Layer، یک parser دستی بر پایه `contains("تسک")` داره.
**وضعیت: خارج از scope فیکس‌های فوری؛ نیازمند طراحی و پیاده‌سازی مجزا.**

### 🔴 ۲.۲ — بخش ۱۲ (Routines): هیچ UI و DAO‌ای نداره
Entity های `RoutineTemplateEntity` و `RoutineInstanceEntity` در دیتابیس هستن ولی:
- هیچ DAO‌ای براشون نیست.
- هیچ صفحه‌ای برای مدیریت روتین‌ها وجود نداره.
- جدول `RoutineTemplateTask` و `RoutineInstanceTask` که پرامپت خواسته (بخش ۴۳) اصلاً وجود نداره.
**وضعیت: فاز بعدی.**

### 🔴 ۲.۳ — بخش ۹-۱۰ (Subtasks و Multiple Reminders): DAO ندارن
`SubtaskEntity` و `ReminderEntity` در دیتابیس هستن ولی هیچ DAO و UI‌ای براشون نیست. یعنی تسک‌ها نمی‌تونن subtask داشته باشن و سیستم چند-یادآوریِ الزامی بخش ۱۰ اصلاً وجود نداره (فقط یک `alarmTimeMillis` تکی روی هر تسک هست).
**وضعیت: فاز بعدی.**

### 🔴 ۲.۴ — بخش ۱۸ (Focus Mode): اصلاً وجود نداره
هیچ Pomodoro، هیچ `FocusSession` entity، هیچ صفحه‌ای.
**وضعیت: فاز بعدی.**

### 🔴 ۲.۵ — بخش ۱۹ (Analytics): اصلاً وجود نداره
هیچ داشبورد آماری، هیچ `AnalyticsEvent` entity.
**وضعیت: فاز بعدی.**

### 🟠 ۲.۶ — بخش ۱۵-۱۶ (Goals/Projects): ناقصه
- هیچ Milestone (`GoalMilestone`, `ProjectMilestone`) پیاده نشده.
- ارتباط Task↔Goal و Task↔Project در Entity هست ولی هیچ UI‌ای برای وصل کردنشون وجود نداره.
**وضعیت: فاز بعدی.**

### 🟡 ۲.۷ — بخش ۱۱ (Recurring Tasks): وجود نداره
هیچ منطق تکرار (daily/weekly/monthly/custom) پیاده نشده.
**وضعیت: فاز بعدی.**

### 🟡 ۲.۸ — بخش ۵ (ناوبری اصلی): فقط ۵ از ۱۱ بخش خواسته‌شده وجود داره
موجود: Today, Calendar, Habits, AI Chat, Settings
جا افتاده: Tasks (صفحه‌ی مستقل), Goals (مجزا از Projects), Routines, Focus, Analytics
**وضعیت: فاز بعدی — بعد از پیاده‌سازی هر فیچر، باید به ناوبری اضافه بشه.**

### 🟡 ۲.۹ — بخش ۳۲ (Scheduling Engine): نصفه‌کاره و استفاده نشده
`DeterministicPlannerEngine` منطق خوبی داره (sort، workload، conflict detection) ولی:
- هیچ‌جا در UI صدا زده نمی‌شه.
- Time-blocking (بخش ۱۴) و تشخیص تداخل تقویم واقعی روش پیاده نشده.
**وضعیت: بخشی فیکس شد (به `TodayScreen` وصل شد برای نمایش هشدار workload)؛ ادغام کامل با UI زمان‌بندی فاز بعدیه.**

### 🟡 ۲.۱۰ — بخش ۴۴ (Data Model): خیلی از Entity‌ها وجود ندارن
جا افتاده: `User`, `Category`, `Tag`, `RoutineTemplateTask`, `RoutineInstanceTask`, `GoalMilestone`, `ProjectMilestone`, `HabitLog`, `CalendarEvent`, `FocusSession`, `AIConversation`, `AIMessage`, `AIAction`, `UserPreference` (جدا از DataStore فعلی), `AnalyticsEvent`.
**وضعیت: فاز بعدی.**

### ⚪️ ۲.۱۱ — کد مرده (نوشته شده ولی وصل نیست)
- `ReminderWorker` (WorkManager) — هیچ‌جا enqueue نمی‌شه؛ اپ در عمل فقط `AlarmManager` رو مستقیم استفاده می‌کنه.
- `DeterministicPlannerEngine` — تا قبل از این فیکس‌ها هیچ‌جا صدا زده نمی‌شد.
**وضعیت: `DeterministicPlannerEngine` وصل شد؛ `ReminderWorker` چون Alarm-based reminder جایگزینش شده، حذف یا برای فاز بعدی (batch reminders چندتایی) نگه داشته می‌شه.**

### 🟡 ۲.۱۲ — بخش ۴ (زبان): min SDK اشتباه بود
پرامپت `minSdk = 30` (Android 11) خواسته؛ کد `minSdk = 30` داشت — این درست بود، مغایرتی نیست. ✅ تاییدشده صحیح.

### 🟡 ۲.۱۳ — بخش ۳۷ (AI اختیاری بودن): ادعای نادرست در UI
متن Settings می‌گفت با وارد کردن API Key، دستیار «به شبکه متصل می‌شه»، در حالی که هیچ اتصال واقعی وجود نداشت — این دقیقاً همون «Fake AI» ایه که بخش ۶۲ پرامپت صریحاً منع کرده.
**وضعیت: فیکس شد — متن اصلاح شد تا صادقانه بگه فعلاً فقط حالت آفلاین/دستی موجوده.**

---

## خلاصه‌ی اولویت‌بندی کار باقی‌مانده

1. ✅ فاز ۱ (این نوبت): فیکس باگ‌های بحرانی فنی که اپ فعلی رو می‌شکنه — **انجام شد**
2. فاز ۲: DAO های گمشده (Subtask, Reminder, Routine) + اتصال به UI
3. فاز ۳: پیاده‌سازی Routines (Template/Instance) با UI کامل
4. فاز ۴: پیاده‌سازی AI Tool Layer واقعی + Read/Action Tools + اتصال به یک Provider واقعی (نیازمند تصمیم درباره‌ی نحوه‌ی مدیریت کلید API طبق بخش ۳۷-۳۹ پرامپت)
5. فاز ۵: Focus Mode
6. فاز ۶: Analytics
7. فاز ۷: Goals/Projects Milestones + اتصال کامل زنجیره‌ی Goal→Project→Task
8. فاز ۸: Recurring Tasks
9. فاز ۹: تکمیل ناوبری به ۱۱ بخش کامل پرامپت

هر فاز به‌خاطر حجمش باید در یک نوبت کاری جداگانه انجام بشه.
