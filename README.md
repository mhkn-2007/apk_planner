# LifeOS — apk_planner

اپلیکیشن اندروید Personal OS طبق Master Prompt (فارسی/RTL-first، Kotlin + Jetpack Compose).

برای تاریخچه‌ی کامل باگ‌ها و فیچرهای پیاده‌شده در هر فاز، `BUGS_AND_GAPS.md` را ببینید.

## نواقص باقی‌مانده (بازبینی مستقل — این نوبت)

این لیست حاصل یک بازبینی کامل و مستقل کد فعلی در برابر متن کامل Master Prompt است (نه صرفاً تکیه بر گزارش‌های قبلی، که خودشان چند مورد را از قلم انداخته بودند).

### 🔴 جدی

1. ~~**پیشرفت اهداف/پروژه‌ها محاسبه نمی‌شود** — `GoalEntity.progressPercentage` و `ProjectEntity.progressPercentage` هیچ‌جا آپدیت نمی‌شوند و همیشه صفر می‌مانند.~~ **فیکس شد** — `GoalProjectProgressUseCase` اضافه شد؛ پیشرفت واقعی از روی مایلستون‌ها (یا در نبود آن‌ها، نسبت تسک‌های تکمیل‌شده) محاسبه و ذخیره می‌شود.
2. ~~**Time Blocking بصری و نماهای تقویم (بخش ۱۳-۱۴)** — تقویم فعلی فقط یک grid ماهانه + لیست ساده‌ی تسک‌های روز انتخاب‌شده است.~~ **فیکس شد** — `DayTimelineView` واقعی با تشخیص تداخل (`DeterministicPlannerEngine.detectConflicts`) اضافه شد.

### 🟠 مهم

3. ~~**Archive و Duplicate تسک (بخش ۷)** — کاربر نمی‌تواند یک تسک را آرشیو یا کپی کند.~~ **فیکس شد** — `isArchived` + آرشیو/کپی کامل با UI (منوی overflow روی کارت تسک، فیلتر «آرشیو» در صفحه‌ی Tasks). کپی، زیرکارها و یادآوری‌ها را هم منتقل می‌کند.
4. ~~**`CalendarEventEntity` کد مرده‌ی جدید**~~ **فیکس شد** — به `CalendarViewModel`/`CalendarScreen` وصل شد؛ کاربر می‌تواند رویداد دستی اضافه/حذف کند.

### 🟡 متوسط

5. ~~**ویرایش/کپی روتین از طریق UI ممکن نیست**~~ **فیکس شد** — `updateTemplate`/`duplicateTemplate` + منوی overflow روی کارت روتین.
6. ~~**`Category`/`Tag` (بخش ۴۴) هنوز پیاده نشده**~~ **فیکس شد** — `CategoryEntity` + UI کامل ایجاد/حذف/فیلتر/اتصال به تسک.

## بازبینی مستقل دوم (این نوبت)

موارد ۱ تا ۶ بالا مستقلاً راستی‌آزمایی شدند و همگی درست تشخیص داده شده بودند (به‌جز مورد ۱ که در همین فاصله با `GoalProjectProgressUseCase` فیکس شده — کد فعلی `GoalsAndProjectsScreen` واقعاً آن را صدا می‌زند). یک نقص مهم دیگر که در هیچ‌کدام از بازبینی‌های قبلی (نه `BUGS_AND_GAPS.md`، نه بازبینی اول این فایل) ذکر نشده بود:

### 🔴 جدی

7. **بخش ۵۷ (Testing) عملاً پوشش داده نشده** — پرامپت به‌صراحت تست خودکار برای موارد زیادی خواسته: task creation/update/completion، multiple reminders، recurring tasks، routine templates/instances، goals، projects، habits، focus sessions، analytics، scheduling/conflict detection، AI action parsing/authorization، database relationships، offline behavior. فایل تست فعلی (`LifeOSTests.kt`) فقط تبدیل تقویم جلالی را پوشش می‌دهد (۸ تست) — هیچ تست دیگری در پروژه وجود نداشت.

**وضعیت: بخشی فیکس شد.** دو فایل تست جدید اضافه شد:
- `DeterministicPlannerEngineTest` — پوشش کامل sort/workload/conflict-detection/postponement. در همین حین یک باگ واقعی هم پیدا و فیکس شد: `detectConflicts` قبلاً فقط جفت‌های همسایه (بعد از مرتب‌سازی بر اساس زمان شروع) را بررسی می‌کرد، پس تداخل بین یک تسک با بازه‌ی زمانی پهن و یک تسک غیرهمسایه که داخل آن بازه بود ممکن بود اصلاً تشخیص داده نشود؛ الگوریتم به مقایسه‌ی کامل (با یک بهینه‌سازی early-break صحیح) تغییر کرد.
- `AIToolLayerTest` — با MockK (وابستگی تست جدید، چون قبلاً هیچ کتابخانه‌ی mock‌سازی در پروژه نبود)، پوشش validation (عنوان خالی، clamp کردن اولویت خارج از بازه‌ی ۰-۴)، حالت‌های not-found، و مهم‌تر از همه رفتار آستانه‌ی تأیید (بخش ۳۵): تأیید شد که حذف/جابجایی گروهی بالای `CONFIRMATION_THRESHOLD` واقعاً چیزی را تغییر نمی‌دهد تا کاربر صریحاً تأیید کند.
- **توجه مهم:** این تست‌ها با بررسی دستی دقیق امضای هر متد در سورس نوشته و منطقشان ردیابی شده (از جمله شبیه‌سازی الگوریتم `detectConflicts` با پایتون برای اطمینان از صحت انتظارات)، ولی محیط sandbox اجازه‌ی اجرای واقعی Gradle/JUnit را نمی‌دهد (دسترسی شبکه به `google()`/`mavenCentral()` مسدود است) — پس صحت کامپایل و پاس‌شدن واقعی این تست‌ها هنوز با یک build واقعی تأیید نشده.
- **هنوز باقی‌مانده:** بقیه‌ی موارد بخش ۵۷ (task CRUD، multiple reminders، recurring tasks، routine templates/instances، goals، projects، habits، focus sessions، analytics، AI action parsing/authorization در سطح `AIToolCatalog`، database relationships، offline behavior) هنوز تست ندارند — این‌ها عمدتاً نیازمند Room in-memory database testing یا Hilt test setup هستند که زیرساخت جداگانه‌ای می‌طلبد.

### 🟡 متوسط

8. **`Modifier.semantics` هیچ‌جا استفاده نشده (بخش ۵۳: Accessibility)** — `contentDescription` روی آیکون‌ها به‌طور گسترده رعایت شده (۴۲ مورد)، ولی هیچ‌جا برای توصیف وضعیت‌های سفارشی (مثل نوار پیشرفت هدف/عادت یا تایمر فوکوس) از semantics صریح استفاده نشده؛ برای screen reader این عناصر ممکن است فقط به‌صورت اعداد خام یا بدون برچسب خوانده شوند.

## وضعیت فازها

فازهای ۱ تا ۹ (طبق `BUGS_AND_GAPS.md`) همگی انجام شده‌اند. موارد ۱ تا ۶ فیکس شدند. مورد ۷ بخشی فیکس شد (دو کلاس منطق تجاری حساس تست شدند، مابقی بخش ۵۷ باز است). مورد ۸ هنوز باز است.
