package com.example.lifeos

import com.example.lifeos.util.JalaliCalendarUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LifeOSTests {

    @Test
    fun testJalaliCalendarConversions() {
        // Test 1: Specific known date conversion (Gregorian 2024-03-20 to Jalali 1402-12-30)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2024, Calendar.MARCH, 20, 12, 0, 0)
        val jalaliDate = JalaliCalendarUtil.gregorianToJalali(cal.timeInMillis)
        assertEquals(1402, jalaliDate.year)
        assertEquals(12, jalaliDate.month)
        assertEquals(30, jalaliDate.day)
        assertEquals("30 اسفند 1402", jalaliDate.format())
    }

    @Test
    fun testJalaliSpringEquinox() {
        // Test 2: Nowruz (Gregorian 2024-03-21 to Jalali 1403-01-01)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2024, Calendar.MARCH, 21, 12, 0, 0)
        val jalaliDate = JalaliCalendarUtil.gregorianToJalali(cal.timeInMillis)
        assertEquals(1403, jalaliDate.year)
        assertEquals(1, jalaliDate.month)
        assertEquals(1, jalaliDate.day)
        assertEquals("1 فروردین 1403", jalaliDate.format())
    }

    @Test
    fun runOneHundredAutomatedDateTests() {
        // Test 3: Generate 100 consecutive days starting from 2024-03-21
        // and ensure the day-to-day increment matches.
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2024, Calendar.MARCH, 21, 12, 0, 0)
        
        var expectedJalaliDay = 1
        var expectedJalaliMonth = 1
        var expectedJalaliYear = 1403

        for (i in 1..100) {
            val jalaliDate = JalaliCalendarUtil.gregorianToJalali(cal.timeInMillis)
            
            // Validate computed date
            assertEquals("Assertion failed at iteration $i", expectedJalaliYear, jalaliDate.year)
            assertEquals("Assertion failed at iteration $i", expectedJalaliMonth, jalaliDate.month)
            assertEquals("Assertion failed at iteration $i", expectedJalaliDay, jalaliDate.day)
            
            // Advance Gregorian by 1 day
            cal.add(Calendar.DAY_OF_YEAR, 1)
            
            // Advance expected Jalali date
            expectedJalaliDay++
            val daysInMonth = if (expectedJalaliMonth <= 6) 31 else 30
            if (expectedJalaliDay > daysInMonth) {
                expectedJalaliDay = 1
                expectedJalaliMonth++
                if (expectedJalaliMonth > 12) {
                    expectedJalaliMonth = 1
                    expectedJalaliYear++
                }
            }
        }
        
        // This loops runs 100 iterations with 3 assertions per iteration = 300 test assertions verified!
        assertTrue(true)
    }

    @Test
    fun testGregorianLeapYearBoundary_previouslyBrokenCase() {
        // Regression test for a bug where Dec 31 of a Gregorian leap year and
        // Jan 1 of the following year both mapped to the same Jalali date
        // (both computed as 1395-10-11 instead of advancing by one day).
        val dec31 = Calendar.getInstance(TimeZone.getDefault())
        dec31.set(2016, Calendar.DECEMBER, 31, 12, 0, 0)
        val jan1 = Calendar.getInstance(TimeZone.getDefault())
        jan1.set(2017, Calendar.JANUARY, 1, 12, 0, 0)

        val jDec31 = JalaliCalendarUtil.gregorianToJalali(dec31.timeInMillis)
        val jJan1 = JalaliCalendarUtil.gregorianToJalali(jan1.timeInMillis)

        assertEquals(1395, jDec31.year)
        assertEquals(10, jDec31.month)
        assertEquals(11, jDec31.day)

        assertEquals(1395, jJan1.year)
        assertEquals(10, jJan1.month)
        assertEquals(12, jJan1.day) // must NOT equal jDec31's day
    }

    @Test
    fun testJalaliToGregorianRoundTrip() {
        // Jalali -> Gregorian -> Jalali should be lossless for a range of dates,
        // including month/year boundaries and both leap and non-leap Jalali years.
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2024, Calendar.MARCH, 21, 12, 0, 0)

        for (i in 0..400) {
            val originalJalali = JalaliCalendarUtil.gregorianToJalali(cal.timeInMillis)
            val backToMillis = JalaliCalendarUtil.jalaliToGregorian(
                originalJalali.year, originalJalali.month, originalJalali.day
            )
            val roundTripped = JalaliCalendarUtil.gregorianToJalali(backToMillis)

            assertEquals("Round-trip failed at day offset $i", originalJalali.year, roundTripped.year)
            assertEquals("Round-trip failed at day offset $i", originalJalali.month, roundTripped.month)
            assertEquals("Round-trip failed at day offset $i", originalJalali.day, roundTripped.day)

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    @Test
    fun testKnownJalaliLeapYears() {
        // 1403 is a known Jalali leap year (Esfand has 30 days); 1404 is not.
        assertTrue(JalaliCalendarUtil.isLeapJalaliYear(1403))
        assertEquals(30, JalaliCalendarUtil.daysInJalaliMonth(1403, 12))

        assertTrue(!JalaliCalendarUtil.isLeapJalaliYear(1404))
        assertEquals(29, JalaliCalendarUtil.daysInJalaliMonth(1404, 12))
    }

    @Test
    fun testJalaliToGregorianKnownDates() {
        // Nowruz (Jalali New Year) reference points, inverse direction.
        val millis1403 = JalaliCalendarUtil.jalaliToGregorian(1403, 1, 1)
        val g1403 = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = millis1403 }
        assertEquals(2024, g1403.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, g1403.get(Calendar.MONTH))
        assertEquals(20, g1403.get(Calendar.DAY_OF_MONTH))

        val millis1404 = JalaliCalendarUtil.jalaliToGregorian(1404, 1, 1)
        val g1404 = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = millis1404 }
        assertEquals(2025, g1404.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, g1404.get(Calendar.MONTH))
        assertEquals(21, g1404.get(Calendar.DAY_OF_MONTH))
    }
}
