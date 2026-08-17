package com.example.lifeos.ui.screens

import com.example.lifeos.data.database.dao.FocusSessionDao
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.dao.HabitLogDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.RoutineInstanceEntity
import com.example.lifeos.domain.repositories.TaskRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Tests [AnalyticsViewModel] -- prompt section 19 (Analytics) had no test
 * coverage before this, completing the last item from README's
 * "بخش ۵۷ (Testing) عملاً پوشش داده نشده" list.
 *
 * Focuses on the two genuinely nontrivial pieces of logic: the "most
 * productive hour range" insight (the prompt's own example metric,
 * "You complete most important tasks between 9 AM and 12 PM") and the
 * week/month date-range boundary computation (Jalali, Saturday-start
 * week per prompt section 4), plus an end-to-end [load] test verifying the
 * completion-percentage arithmetic and that every DAO/repository call feeds
 * into the right UiState field.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var focusSessionDao: FocusSessionDao
    private lateinit var habitDao: HabitDao
    private lateinit var habitLogDao: HabitLogDao
    private lateinit var goalDao: GoalDao
    private lateinit var routineDao: RoutineDao
    private lateinit var viewModel: AnalyticsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        taskRepository = mockk(relaxed = true)
        focusSessionDao = mockk(relaxed = true)
        habitDao = mockk(relaxed = true)
        habitLogDao = mockk(relaxed = true)
        goalDao = mockk(relaxed = true)
        routineDao = mockk(relaxed = true)

        every { habitDao.getAllHabits() } returns MutableStateFlow(emptyList())
        every { goalDao.getAllGoals() } returns MutableStateFlow(emptyList())
        every { routineDao.getInstancesForDateRange(any(), any()) } returns MutableStateFlow(emptyList())
        coEvery { taskRepository.getCompletionTimestampsInRange(any(), any()) } returns emptyList()

        viewModel = AnalyticsViewModel(taskRepository, focusSessionDao, habitDao, habitLogDao, goalDao, routineDao)
        testDispatcher.scheduler.runCurrent() // let the init{} load() finish
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- computeMostProductiveHourRange: the prompt's own example insight ---

    @Test
    fun computeMostProductiveHourRange_fewerThanFiveCompletions_returnsNullToAvoidOverclaiming() {
        // Docstring is explicit about this: avoid a misleadingly confident
        // claim from only 1-2 data points.
        val timestamps = listOf(
            timestampAtHour(9), timestampAtHour(9), timestampAtHour(10), timestampAtHour(10)
        ) // only 4 points

        val result = viewModel.computeMostProductiveHourRange(timestamps)

        assertNull(result)
    }

    @Test
    fun computeMostProductiveHourRange_emptyList_returnsNull() {
        assertNull(viewModel.computeMostProductiveHourRange(emptyList()))
    }

    @Test
    fun computeMostProductiveHourRange_clearCluster_identifiesCorrectThreeHourWindow() {
        // 5 completions clustered at 9, 10, 10, 11, 11 -- the 3-hour window
        // starting at hour 9 (09:00-12:00) should contain all 5.
        val timestamps = listOf(
            timestampAtHour(9), timestampAtHour(10), timestampAtHour(10),
            timestampAtHour(11), timestampAtHour(11)
        )

        val result = viewModel.computeMostProductiveHourRange(timestamps)

        assertEquals("09:00 تا 12:00", result)
    }

    @Test
    fun computeMostProductiveHourRange_completionsSpreadEvenlyAcrossDay_stillPicksBestWindow() {
        // One completion in every hour of the day (24 total) -- every 3-hour
        // window has the same sum (3), so the algorithm should still
        // deterministically pick the first such window (hour 0) rather than
        // crash or behave inconsistently on a tie.
        val timestamps = (0 until 24).map { timestampAtHour(it) }

        val result = viewModel.computeMostProductiveHourRange(timestamps)

        assertEquals("00:00 تا 03:00", result)
    }

    @Test
    fun computeMostProductiveHourRange_windowNearEndOfDay_doesNotWrapAroundMidnight() {
        // Completions clustered at 22, 23 only -- the search space is
        // startHour in 0..21 (so the window [22,23,0] never gets considered
        // as if hours wrap around). With only 2 clustered hours and no
        // 3rd hour reinforcing them, hour 21's window ([21,22,23]) should
        // still win over any window that would incorrectly wrap to hour 0.
        val timestamps = listOf(
            timestampAtHour(22), timestampAtHour(22), timestampAtHour(23),
            timestampAtHour(23), timestampAtHour(23)
        )

        val result = viewModel.computeMostProductiveHourRange(timestamps)

        assertEquals("21:00 تا 00:00", result)
    }

    private fun timestampAtHour(hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // --- daysBetween ---

    @Test
    fun daysBetween_sameDay_returnsOne() {
        val start = 0L
        val end = 60 * 60 * 1000L // 1 hour later, same calendar day span
        assertEquals(1, viewModel.daysBetween(start, end))
    }

    @Test
    fun daysBetween_sevenDaySpan_returnsSeven() {
        val start = 0L
        val end = start + 6L * 24 * 60 * 60 * 1000L // 6 full days later = a 7-day inclusive span
        assertEquals(7, viewModel.daysBetween(start, end))
    }

    @Test
    fun daysBetween_neverReturnsLessThanOneEvenForZeroOrNegativeSpan() {
        assertEquals(1, viewModel.daysBetween(1000L, 500L))
    }

    // --- rangeFor: week (Saturday-start) and month boundaries ---

    @Test
    fun rangeFor_week_spansExactlySevenDaysInclusive() {
        val (start, end) = viewModel.rangeFor(AnalyticsPeriod.WEEK)

        val spanDays = ((end - start) / (24 * 60 * 60 * 1000L)).toInt()
        // end is 23:59:59.999 of the 7th day -- so the raw millis span is
        // just under 7 full days, and start-of-day to start-of-day is 6.
        assertTrue("expected roughly a 7-day window, got span of $spanDays days", spanDays in 6..7)
    }

    @Test
    fun rangeFor_week_startsOnSaturdayAtMidnight() {
        val (start, _) = viewModel.rangeFor(AnalyticsPeriod.WEEK)

        val cal = Calendar.getInstance().apply { timeInMillis = start }
        // Persian week starts on Saturday (prompt section 4/50: "Week start").
        assertEquals(Calendar.SATURDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun rangeFor_week_todayFallsWithinTheReturnedRange() {
        val now = System.currentTimeMillis()
        val (start, end) = viewModel.rangeFor(AnalyticsPeriod.WEEK)

        assertTrue("today ($now) should fall within [$start, $end]", now in start..end)
    }

    @Test
    fun rangeFor_month_todayFallsWithinTheReturnedRange() {
        val now = System.currentTimeMillis()
        val (start, end) = viewModel.rangeFor(AnalyticsPeriod.MONTH)

        assertTrue("today ($now) should fall within [$start, $end]", now in start..end)
    }

    @Test
    fun rangeFor_month_startsOnFirstDayOfJalaliMonthAtMidnight() {
        val (start, _) = viewModel.rangeFor(AnalyticsPeriod.MONTH)

        val startJalali = com.example.lifeos.util.JalaliCalendarUtil.gregorianToJalali(start)
        assertEquals(1, startJalali.day)

        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    }

    // --- load(): end-to-end wiring and completion-percentage arithmetic ---

    @Test
    fun load_computesCompletionPercentageFromScheduledAndCompletedCounts() = runTest(testDispatcher) {
        coEvery { taskRepository.countCompletedInRange(any(), any()) } returns 6
        coEvery { taskRepository.countScheduledInRange(any(), any()) } returns 8
        coEvery { taskRepository.countPostponedInRange(any(), any()) } returns 2

        viewModel.setPeriod(AnalyticsPeriod.WEEK)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(6, state.tasksCompleted)
        assertEquals(8, state.tasksScheduled)
        assertEquals(2, state.tasksPostponed)
        assertEquals(75, state.completionPercentage) // 6/8 = 75%
        assertEquals(false, state.isLoading)
    }

    @Test
    fun load_zeroScheduledTasks_completionPercentageIsZeroNotDivideByZeroCrash() = runTest(testDispatcher) {
        coEvery { taskRepository.countCompletedInRange(any(), any()) } returns 0
        coEvery { taskRepository.countScheduledInRange(any(), any()) } returns 0

        viewModel.setPeriod(AnalyticsPeriod.WEEK)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0, viewModel.uiState.value.completionPercentage)
    }

    @Test
    fun load_convertsFocusSecondsToMinutes() = runTest(testDispatcher) {
        coEvery { focusSessionDao.getTotalFocusSecondsInRange(any(), any()) } returns 150 // 2.5 minutes

        viewModel.setPeriod(AnalyticsPeriod.WEEK)
        testDispatcher.scheduler.runCurrent()

        assertEquals(2, viewModel.uiState.value.focusMinutes) // integer division, matches production code
    }

    @Test
    fun load_habitConsistency_populatesOnePerHabitWithCorrectPercentage() = runTest(testDispatcher) {
        val habit = HabitEntity(id = "h1", name = "ورزش")
        every { habitDao.getAllHabits() } returns MutableStateFlow(listOf(habit))
        coEvery { habitLogDao.countLogsInRange(eq("h1"), any(), any()) } returns 5

        viewModel.setPeriod(AnalyticsPeriod.WEEK)
        testDispatcher.scheduler.runCurrent()

        val consistency = viewModel.uiState.value.habitConsistency
        assertEquals(1, consistency.size)
        assertEquals("h1", consistency.first().habit.id)
        assertEquals(5, consistency.first().completedDays)
        assertTrue(consistency.first().percentage in 0..100)
    }

    @Test
    fun load_goalProgress_includesEveryGoalFromGoalDao() = runTest(testDispatcher) {
        val goals = listOf(GoalEntity(id = "g1", title = "هدف ۱"), GoalEntity(id = "g2", title = "هدف ۲"))
        every { goalDao.getAllGoals() } returns MutableStateFlow(goals)

        viewModel.setPeriod(AnalyticsPeriod.WEEK)
        testDispatcher.scheduler.runCurrent()

        assertEquals(2, viewModel.uiState.value.goalProgress.size)
    }

    @Test
    fun load_routineCompletion_countsOnlyCompletedInstances() = runTest(testDispatcher) {
        val instances = listOf(
            RoutineInstanceEntity(id = "i1", templateId = "t1", dateMillis = 0L, isCompleted = true),
            RoutineInstanceEntity(id = "i2", templateId = "t1", dateMillis = 0L, isCompleted = false),
            RoutineInstanceEntity(id = "i3", templateId = "t1", dateMillis = 0L, isCompleted = true)
        )
        every { routineDao.getInstancesForDateRange(any(), any()) } returns MutableStateFlow(instances)

        viewModel.setPeriod(AnalyticsPeriod.WEEK)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(3, state.routineInstancesTotal)
        assertEquals(2, state.routineInstancesCompleted)
    }

    @Test
    fun load_filtersOutNullCompletionTimestampsBeforeComputingProductiveHourRange() = runTest(testDispatcher) {
        // getCompletionTimestampsInRange can return nulls for
        // scheduled-but-not-yet-completed tasks (see its List<Long?> return
        // type) -- these must be filtered out, not passed through to break
        // computeMostProductiveHourRange's Calendar math.
        coEvery { taskRepository.getCompletionTimestampsInRange(any(), any()) } returns
            listOf(null, timestampAtHour(9), null, timestampAtHour(9), timestampAtHour(10), timestampAtHour(10), timestampAtHour(11))

        viewModel.setPeriod(AnalyticsPeriod.WEEK)
        testDispatcher.scheduler.runCurrent()

        // Should not throw, and with 5 non-null clustered points should
        // produce a real (non-null) insight.
        assertEquals("09:00 تا 12:00", viewModel.uiState.value.mostProductiveHourRange)
    }

    @Test
    fun setPeriod_switchesUiStateToRequestedPeriod() = runTest(testDispatcher) {
        viewModel.setPeriod(AnalyticsPeriod.MONTH)
        testDispatcher.scheduler.runCurrent()

        assertEquals(AnalyticsPeriod.MONTH, viewModel.uiState.value.period)
    }
}
