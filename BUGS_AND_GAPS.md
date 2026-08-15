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
**وضعیت: بخش اصلی فیکس شد (فاز ۴).**
- `GeminiProvider` به‌عنوان Provider واقعی (بخش ۳۶: AIProvider abstraction) اضافه شد؛ از طریق OkHttp مستقیم با REST API جمنای (`generateContent` + function calling) صحبت می‌کنه. `AIModule` الان این رو bind می‌کنه (نه `MockAIProvider` رو).
- `AIReadTools` اضافه شد: تمام ابزارهای خواندنی بخش ۲۱ (`get_tasks`, `get_today_tasks`, `get_unfinished_tasks`, `get_goals`, `get_projects`, `get_habits`, `get_routines`, `get_productivity_history`) — همه فقط از طریق DAO/Repository موجود می‌خونن، دسترسی خام به دیتابیس ندارن.
- `AIToolLayer` گسترش پیدا کرد: `createTask`, `updateTask`, `completeTask`, `deleteTask`, `scheduleTask`, `rescheduleTask`, `createSubtask`, `createReminder`, `updateReminder`, `deleteReminder`, `createRoutine`, `updateRoutine`, `createGoal`, `createProject`, و عملیات پرتاثیر (`previewDeleteTasks`/`confirmDeleteTasks`, `previewMoveTasks`/`confirmMoveTasks`) که طبق بخش ۳۵ نیازمند تأیید کاربرن.
- `AIToolCatalog` اضافه شد: تعریف schema ابزارها برای function-calling جمنای + dispatch کردن هر function call مدل به `AIReadTools`/`AIToolLayer` — این لایه‌ایه که مدل هیچ‌وقت مستقیم به دیتابیس دسترسی نداره (بخش ۲۱/۲۲).
- `AIChatScreen`/`AIChatViewModel` کامل بازنویسی شد: parser دستی `contains("تسک")` حذف شد، حالا با یک حلقه‌ی function-calling واقعی با جمنای صحبت می‌کنه و یک UI پیش‌نمایش/تأیید («اعمال کن» / «انصراف») برای اکشن‌های پرتاثیر داره (بخش ۳۵).
- شکست‌های AI (بدون کلید، خطای شبکه، خطای سرویس) به‌صورت پیام خطای واضح به کاربر نشون داده می‌شن و بقیه‌ی برنامه دست‌نخورده باقی می‌مونه (بخش ۳۹).
- کلید API طبق تصمیم گرفته‌شده، فعلاً توسط خود کاربر در Settings وارد می‌شه (راه‌حل ساده‌تر ولی خلاف روح کامل بخش ۳۷-۳۹ برای production؛ مدیریت کلید سمت بک‌اند یک تصمیم معماری جداست که هنوز گرفته نشده).
- **هنوز پیاده نشده:** AI Daily/Weekly Planning (بخش ۲۳-۲۴ — یک جریان مکالمه‌ی چندمرحله‌ای اختصاصی که از `DeterministicPlannerEngine` هم استفاده کنه)، Task Breakdown خودکار (۲۶)، AI Rescheduling اختصاصی (۲۷، فراتر از ابزار عمومی `reschedule_task`)، AI Daily Review (۳۰)، حافظه‌ی مکالمه‌ی پایدار بین نشست‌ها (بخش ۶۰ — الان تاریخچه فقط در حافظه‌ی ViewModel هست و با بستن اپ از بین می‌ره).

### 🔴 ۲.۲ — بخش ۱۲ (Routines): هیچ UI و DAO‌ای نداره
Entity های `RoutineTemplateEntity` و `RoutineInstanceEntity` در دیتابیس هستن ولی:
- هیچ DAO‌ای براشون نیست.
- هیچ صفحه‌ای برای مدیریت روتین‌ها وجود نداره.
- جدول `RoutineTemplateTask` و `RoutineInstanceTask` که پرامپت خواسته (بخش ۴۳) اصلاً وجود نداره.
**وضعیت: فیکس شد — `RoutineDao` اضافه شد، جدول‌های `RoutineTemplateTask`/`RoutineInstanceTask` ساخته شدن، `RoutinesScreen` با ViewModel کامل (Template/Instance) پیاده و به ناوبری وصل شد.**

### 🔴 ۲.۳ — بخش ۹-۱۰ (Subtasks و Multiple Reminders): DAO ندارن
`SubtaskEntity` و `ReminderEntity` در دیتابیس هستن ولی هیچ DAO و UI‌ای براشون نیست. یعنی تسک‌ها نمی‌تونن subtask داشته باشن و سیستم چند-یادآوریِ الزامی بخش ۱۰ اصلاً وجود نداره (فقط یک `alarmTimeMillis` تکی روی هر تسک هست).
**وضعیت: فیکس شد — `SubtaskDao` و `ReminderDao` اضافه شدن، `TaskRepository` باهاشون ادغام شد، و مدیریت subtask/چند-یادآوری (هرکدوم با alarm جدا از طریق `AlarmScheduler`) توی دیالوگ ادیت تسک اضافه شد.**

### 🔴 ۲.۴ — بخش ۱۸ (Focus Mode): اصلاً وجود نداره
هیچ Pomodoro، هیچ `FocusSession` entity، هیچ صفحه‌ای.
**وضعیت: فاز بعدی.**

### 🔴 ۲.۵ — بخش ۱۹ (Analytics): اصلاً وجود نداره
هیچ داشبورد آماری، هیچ `AnalyticsEvent` entity.
**وضعیت: فاز بعدی.**

### 🟠 ۲.۶ — بخش ۱۵-۱۶ (Goals/Projects): ناقصه
- هیچ Milestone (`GoalMilestone`, `ProjectMilestone`) پیاده نشده.
- ارتباط Task↔Goal و Task↔Project در Entity هست ولی هیچ UI‌ای برای وصل کردنشون وجود نداره.
**وضعیت: فیکس شد — `GoalMilestoneEntity`/`ProjectMilestoneEntity` + `MilestoneDao` اضافه شدن، `ProjectsScreen` مایلستون‌های هرکدوم رو با UI کامل (افزودن/تیک زدن/حذف) نشون می‌ده، و دیالوگ ادیت تسک الان چیپ برای وصل‌کردن تسک به هدف/پروژه داره.**

### 🟡 ۲.۷ — بخش ۱۱ (Recurring Tasks): وجود نداره
هیچ منطق تکرار (daily/weekly/monthly/custom) پیاده نشده.
**وضعیت: فیکس شد — `recurrenceRule`/`recurrenceGroupId` روی `TaskEntity`، `GenerateRecurringTaskOccurrencesUseCase` برای تولید idempotent instance‌ها (بدون تکرار غیرکنترل‌شده)، و UI انتخاب تکرار توی دیالوگ افزودن تسک.**

### 🟡 ۲.۸ — بخش ۵ (ناوبری اصلی): فقط ۵ از ۱۱ بخش خواسته‌شده وجود داره
موجود: Today, Calendar, Habits, AI Chat, Settings
جا افتاده: Tasks (صفحه‌ی مستقل), Goals (مجزا از Projects), Routines, Focus, Analytics
**وضعیت: فاز بعدی — بعد از پیاده‌سازی هر فیچر، باید به ناوبری اضافه بشه.**

### 🟡 ۲.۹ — بخش ۳۲ (Scheduling Engine): نصفه‌کاره و استفاده نشده
`DeterministicPlannerEngine` منطق خوبی داره (sort، workload، conflict detection) ولی:
- هیچ‌جا در UI صدا زده نمی‌شه.
- Time-blocking (بخش ۱۴) و تشخیص تداخل تقویم واقعی روش پیاده نشده.
**وضعیت: فیکس شد — به `TodayScreen` وصل شد و هشدار workload/تداخل نمایش داده می‌شه. ادغام کامل با یک UI اختصاصی time-blocking (بخش ۱۴) همچنان فاز بعدیه.**

### 🟡 ۲.۱۰ — بخش ۴۴ (Data Model): خیلی از Entity‌ها وجود ندارن
جا افتاده بود: `User`, `Category`, `Tag`, `RoutineTemplateTask`, `RoutineInstanceTask`, `GoalMilestone`, `ProjectMilestone`, `HabitLog`, `CalendarEvent`, `FocusSession`, `AIConversation`, `AIMessage`, `AIAction`, `UserPreference` (جدا از DataStore فعلی), `AnalyticsEvent`.
**وضعیت: بخشی فیکس شد.**
- `RoutineTemplateTask`, `RoutineInstanceTask`, `GoalMilestone`, `ProjectMilestone` قبلاً در فاز‌های Routines/Milestones اضافه شده بودن.
- `HabitLogEntity` + `HabitLogDao` این نوبت اضافه شد (تاریخچه‌ی روزانه‌ی تکمیل هر عادت، هم از چک‌این دستی در `HabitsScreen` و هم از تکمیل تسک‌های وصل‌شده به عادت در `TodayScreen`) — پایه‌ی داده‌ای لازم برای آمار هفتگی/ماهانه‌ی بخش ۱۷.
- `UserPreference` عمداً به‌صورت Entity جدا اضافه نشد چون همین الان با `PreferencesManager`/DataStore پیاده‌سازی شده (معماری درست‌تر از یک جدول Room برای key-value ساده).
- `CalendarEvent`, `FocusSession`, `AIConversation`, `AIMessage`, `AIAction`, `AnalyticsEvent` هنوز جا افتاده‌ن چون به فیچرهای پیاده‌نشده‌ی خودشون وابسته‌ن (Focus Mode = فاز ۵، Analytics = فاز ۶، AI Tool Layer = فاز ۴)؛ ساختن Entity بدون اون فیچرها طبق اصل «no fake functionality» بی‌فایده‌ست.
- `Category`/`Tag` عمداً اضافه نشدن: در هیچ صفحه‌ای استفاده نمی‌شن، پس یک Entity/DAO بی‌UI فقط کد مرده‌ی جدید می‌سازه؛ باید همراه با UI فیلتر/دسته‌بندی تسک به‌عنوان یک فیچر مجزا کار بشه.
- `User` نیازمند تصمیم معماری احراز هویت (local-only vs backend) هست، در بخش ۵۱ پرامپت هم به‌عنوان «authentication-ready» (نه لزوماً پیاده‌شده) خواسته شده؛ فاز بعدی.

### ⚪️ ۲.۱۱ — کد مرده (نوشته شده ولی وصل نیست)
- `ReminderWorker` (WorkManager) — هیچ‌جا enqueue نمی‌شد؛ اپ در عمل فقط `AlarmManager` رو مستقیم استفاده می‌کرد.
- `DeterministicPlannerEngine` — تا قبل از فیکس ۲.۹ هیچ‌جا صدا زده نمی‌شد.
**وضعیت: فیکس شد.**
- `DeterministicPlannerEngine` به `TodayScreen` وصل شد (بخش ۲.۹).
- `ReminderWorker` کامل حذف شد چون Alarm-based reminders (`AlarmScheduler`) کاملاً جایگزینش شده و دیگه هیچ نقشی نداشت؛ وابستگی‌های بی‌استفاده‌ی `androidx.work:work-runtime-ktx` و `androidx.hilt:hilt-work` هم از `build.gradle.kts` حذف شدن.

### 🟡 ۲.۱۲ — بخش ۴ (زبان): min SDK اشتباه بود
پرامپت `minSdk = 30` (Android 11) خواسته؛ کد `minSdk = 30` داشت — این درست بود، مغایرتی نیست. ✅ تاییدشده صحیح.

### 🟡 ۲.۱۳ — بخش ۳۷ (AI اختیاری بودن): ادعای نادرست در UI
متن Settings می‌گفت با وارد کردن API Key، دستیار «به شبکه متصل می‌شه»، در حالی که هیچ اتصال واقعی وجود نداشت — این دقیقاً همون «Fake AI» ایه که بخش ۶۲ پرامپت صریحاً منع کرده.
**وضعیت: فیکس شد — متن اصلاح شد تا صادقانه بگه فعلاً فقط حالت آفلاین/دستی موجوده.**

---

## خلاصه‌ی اولویت‌بندی کار باقی‌مانده

1. ✅ فاز ۱: فیکس باگ‌های بحرانی فنی که اپ فعلی رو می‌شکنه — **انجام شد**
2. ✅ فاز ۲: DAO های گمشده (Subtask, Reminder, Routine) + اتصال به UI — **انجام شد**
3. ✅ فاز ۳: پیاده‌سازی Routines (Template/Instance) با UI کامل — **انجام شد**
4. ✅ فاز ۴: پیاده‌سازی AI Tool Layer واقعی + Read/Action Tools + اتصال به یک Provider واقعی (Gemini، با کلید وارد‌شده توسط کاربر در Settings) — **انجام شد**
5. فاز ۵: Focus Mode — **هنوز مونده**
6. فاز ۶: Analytics — **هنوز مونده (پایه‌ی `HabitLog` برای آمار عادت‌ها آماده شد)**
7. ✅ فاز ۷: Goals/Projects Milestones + اتصال کامل زنجیره‌ی Goal→Project→Task — **انجام شد**
8. ✅ فاز ۸: Recurring Tasks — **انجام شد**
9. فاز ۹: تکمیل ناوبری به ۱۱ بخش کامل پرامپت (Tasks/Goals/Focus/Analytics هنوز صفحه‌ی مستقل تو bottom nav ندارن) — **هنوز مونده**
10. ✅ تکمیل جزئی ۲.۱۰ (Data model — `HabitLog`) و ۲.۱۱ (حذف کد مرده‌ی `ReminderWorker`) — **انجام شد**

بزرگترین کار باقی‌مانده حالا فاز‌های ۵ و ۶ (Focus Mode و Analytics) و تکمیل نهایی سیستم AI (برنامه‌ریزی روزانه/هفتگی خودکار، حافظه‌ی مکالمه‌ی پایدار) هستن.
