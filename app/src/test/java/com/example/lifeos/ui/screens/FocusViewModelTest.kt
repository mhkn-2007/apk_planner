package com.example.lifeos.ui.screens

import android.content.Context
import com.example.lifeos.data.database.dao.FocusSessionDao
import com.example.lifeos.data.database.dao.TaskDao
import com.example.lifeos.data.database.entities.FocusSessionEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.util.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [FocusViewModel] -- Focus Mode (prompt section 18) had no test
 * coverage before this. Covers the Pomodoro cycle-advancement logic (the
 * one genuinely nontrivial algorithm here: work -> short break, repeating,
 * with a long break every `sessionsBeforeLongBreak` completed work
 * sessions), and the session-persistence contract (elapsed time on early
 * stop, taskId only recorded for WORK sessions, wasCompleted flag).
 *
 * [NotificationHelper] is a Kotlin `object` that touches real Android
 * framework APIs (NotificationManager) -- it's mocked via MockK's
 * `mockkObject` so these tests exercise FocusViewModel's own logic without
 * needing Robolectric just to satisfy that one side-effect call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {

    private lateinit var focusSessionDao: FocusSessionDao
    private lateinit var taskDao: TaskDao
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var context: Context
    private lateinit var viewModel: FocusViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        focusSessionDao = mockk(relaxed = true)
        taskDao = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { focusSessionDao.getAllSessions() } returns MutableStateFlow(emptyList())
        every { taskDao.getAllTasks() } returns MutableStateFlow(emptyList())

        mockkObject(com.example.lifeos.util.NotificationHelper)
        every { com.example.lifeos.util.NotificationHelper.createNotificationChannel(any()) } returns Unit

        viewModel = FocusViewModel(focusSessionDao, taskDao, alarmScheduler, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(com.example.lifeos.util.NotificationHelper)
    }

    private fun runFullSession() {
        // Drives the countdown to completion using the test dispatcher's
        // virtual clock -- no real 1-second delays are actually waited on.
        viewModel.start()
        testDispatcher.scheduler.advanceTimeBy(viewModel.totalSeconds * 1000L + 100)
        testDispatcher.scheduler.runCurrent()
    }

    // --- Default state ---

    @Test
    fun initialState_defaultsToWorkSessionWithPresetDuration() {
        assertEquals(FocusSessionType.WORK, viewModel.sessionType)
        assertEquals(defaultFocusPreset.workMinutes * 60, viewModel.totalSeconds)
        assertEquals(defaultFocusPreset.workMinutes * 60, viewModel.remainingSeconds)
        assertFalse(viewModel.isRunning)
    }

    // --- Guards while running ---

    @Test
    fun selectSessionType_ignoredWhileRunning() {
        viewModel.start()

        viewModel.selectSessionType(FocusSessionType.SHORT_BREAK)

        // Must still be WORK -- changing the type mid-session would corrupt
        // the running timer/alarm deadline.
        assertEquals(FocusSessionType.WORK, viewModel.sessionType)
    }

    @Test
    fun selectTask_ignoredWhileRunning() {
        viewModel.start()
        val task = TaskEntity(id = "t1", title = "test")

        viewModel.selectTask(task)

        assertNull(viewModel.selectedTask)
    }

    @Test
    fun updatePreset_ignoredWhileRunning() {
        viewModel.start()
        val newPreset = FocusPreset(workMinutes = 50, shortBreakMinutes = 10, longBreakMinutes = 30)

        viewModel.updatePreset(newPreset)

        assertEquals(defaultFocusPreset.workMinutes * 60, viewModel.totalSeconds)
    }

    // --- start(): schedules the completion alarm at the correct deadline ---

    @Test
    fun start_schedulesFocusAlarmForTheFullSessionDuration() {
        val deadlineSlot = slot<Long>()
        every {
            alarmScheduler.scheduleFocusSessionAlarm(any(), any(), any(), capture(deadlineSlot))
        } returns Unit

        val before = System.currentTimeMillis()
        viewModel.start()
        val after = System.currentTimeMillis()

        val expectedMin = before + defaultFocusPreset.workMinutes * 60 * 1000L
        val expectedMax = after + defaultFocusPreset.workMinutes * 60 * 1000L
        assertTrue(deadlineSlot.captured in expectedMin..expectedMax)
    }

    @Test
    fun start_whileAlreadyRunning_doesNotScheduleASecondAlarm() {
        viewModel.start()
        viewModel.start() // second call should be a no-op

        io.mockk.verify(exactly = 1) { alarmScheduler.scheduleFocusSessionAlarm(any(), any(), any(), any()) }
    }

    // --- stop(): early stop persists partial elapsed time, doesn't advance the cycle ---

    @Test
    fun stop_earlyStop_persistsElapsedTimeNotFullDuration() = runTest(testDispatcher) {
        val slot = slot<FocusSessionEntity>()
        coEvery { focusSessionDao.insertSession(capture(slot)) } returns Unit

        viewModel.start()
        // Let 10 virtual seconds pass, then stop early.
        testDispatcher.scheduler.advanceTimeBy(10_000L)
        testDispatcher.scheduler.runCurrent()
        viewModel.stop()
        testDispatcher.scheduler.runCurrent()

        assertEquals(10, slot.captured.actualDurationSeconds)
        assertFalse(slot.captured.wasCompleted)
    }

    @Test
    fun stop_earlyStop_resetsSameSessionTypeRatherThanAdvancingCycle() = runTest(testDispatcher) {
        viewModel.start()
        testDispatcher.scheduler.advanceTimeBy(5_000L)
        testDispatcher.scheduler.runCurrent()
        viewModel.stop()
        testDispatcher.scheduler.runCurrent()

        // Stopping a WORK session early must NOT count as a completed work
        // session in the Pomodoro cycle -- it should still be WORK, freshly
        // reset, not advanced to a break.
        assertEquals(FocusSessionType.WORK, viewModel.sessionType)
        assertEquals(defaultFocusPreset.workMinutes * 60, viewModel.remainingSeconds)
    }

    @Test
    fun stop_whenNotRunning_isANoOp() = runTest(testDispatcher) {
        viewModel.stop()

        coVerify(exactly = 0) { focusSessionDao.insertSession(any()) }
    }

    @Test
    fun stop_cancelsTheScheduledAlarm() = runTest(testDispatcher) {
        viewModel.start()
        testDispatcher.scheduler.runCurrent()
        viewModel.stop()
        testDispatcher.scheduler.runCurrent()

        io.mockk.verify(exactly = 1) { alarmScheduler.cancelFocusSessionAlarm(any(), any()) }
    }

    // --- Full completion: Pomodoro cycle advancement (the core algorithm) ---

    @Test
    fun completingAWorkSession_advancesToShortBreak() = runTest(testDispatcher) {
        runFullSession()

        assertEquals(FocusSessionType.SHORT_BREAK, viewModel.sessionType)
        assertEquals(defaultFocusPreset.shortBreakMinutes * 60, viewModel.remainingSeconds)
        assertFalse(viewModel.isRunning)
    }

    @Test
    fun completingAWorkSession_persistsCompletedSessionWithFullDuration() = runTest(testDispatcher) {
        val slot = slot<FocusSessionEntity>()
        coEvery { focusSessionDao.insertSession(capture(slot)) } returns Unit

        runFullSession()

        assertTrue(slot.captured.wasCompleted)
        assertEquals(defaultFocusPreset.workMinutes * 60, slot.captured.actualDurationSeconds)
        assertEquals("WORK", slot.captured.type)
    }

    @Test
    fun completingAWorkSessionWithATaskSelected_recordsTaskIdOnTheSession() = runTest(testDispatcher) {
        val task = TaskEntity(id = "t1", title = "مطالعه")
        every { taskDao.getAllTasks() } returns MutableStateFlow(listOf(task))
        // Rebuild the view model so incompleteTasks picks up the task, then select it.
        viewModel = FocusViewModel(focusSessionDao, taskDao, alarmScheduler, context)
        viewModel.selectTask(task)

        val slot = slot<FocusSessionEntity>()
        coEvery { focusSessionDao.insertSession(capture(slot)) } returns Unit

        runFullSession()

        assertEquals("t1", slot.captured.taskId)
    }

    @Test
    fun completingABreakSession_doesNotRecordATaskIdEvenIfOneWasPreviouslySelected() = runTest(testDispatcher) {
        val task = TaskEntity(id = "t1", title = "مطالعه")
        every { taskDao.getAllTasks() } returns MutableStateFlow(listOf(task))
        viewModel = FocusViewModel(focusSessionDao, taskDao, alarmScheduler, context)
        viewModel.selectTask(task)
        viewModel.selectSessionType(FocusSessionType.SHORT_BREAK)

        val slot = slot<FocusSessionEntity>()
        coEvery { focusSessionDao.insertSession(capture(slot)) } returns Unit

        runFullSession()

        // A break session was never "about" a task -- the taskId link is
        // WORK-only, per FocusViewModel.finishSession's own condition.
        assertNull(slot.captured.taskId)
    }

    @Test
    fun fourthCompletedWorkSession_triggersLongBreakInsteadOfShortBreak() = runTest(testDispatcher) {
        // defaultFocusPreset.sessionsBeforeLongBreak == 4 -- complete three
        // full work->break cycles, then a fourth work session should route
        // to LONG_BREAK instead of the usual SHORT_BREAK.
        repeat(3) {
            runFullSession() // WORK -> SHORT_BREAK
            viewModel.selectSessionType(FocusSessionType.WORK) // manually go back to WORK for the next cycle
        }
        assertEquals(FocusSessionType.WORK, viewModel.sessionType)

        runFullSession() // 4th WORK completion

        assertEquals(FocusSessionType.LONG_BREAK, viewModel.sessionType)
        assertEquals(defaultFocusPreset.longBreakMinutes * 60, viewModel.remainingSeconds)
    }

    @Test
    fun completingABreakSession_returnsToWorkType() = runTest(testDispatcher) {
        viewModel.selectSessionType(FocusSessionType.SHORT_BREAK)

        runFullSession()

        assertEquals(FocusSessionType.WORK, viewModel.sessionType)
        assertEquals(defaultFocusPreset.workMinutes * 60, viewModel.remainingSeconds)
    }

    // --- totalFocusSecondsForTask: simple DAO delegation, but used by the AI daily review too ---

    @Test
    fun totalFocusSecondsForTask_delegatesToDaoAndReturnsResultViaCallback() = runTest(testDispatcher) {
        coEvery { focusSessionDao.getTotalFocusSecondsForTask("t1") } returns 1500

        var captured: Int? = null
        viewModel.totalFocusSecondsForTask("t1") { captured = it }
        testDispatcher.scheduler.runCurrent()

        assertEquals(1500, captured)
    }
}
