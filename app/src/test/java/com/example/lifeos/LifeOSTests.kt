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
}
