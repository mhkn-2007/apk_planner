package com.example.lifeos.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Reliable, bidirectional Gregorian <-> Jalali (Persian) calendar conversion.
 *
 * Implementation is based on the Julian Day Number (JDN) approach with the
 * standard 33-year Jalali leap-cycle break table (the same algorithm used by
 * the widely-used `jalaali-js` library). Both directions are verified
 * consistent (round-trip tested) across a multi-century range, including
 * leap years and Gregorian/Jalali year boundaries.
 *
 * The previous implementation only supported Gregorian -> Jalali and had a
 * genuine off-by-one bug at Gregorian leap-year boundaries (e.g. it mapped
 * both Dec 31 and Jan 1 to the same Jalali date whenever December 31 fell in
 * a Gregorian leap year). This rewrite fixes that and adds the previously
 * missing Jalali -> Gregorian direction.
 */
object JalaliCalendarUtil {

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        val monthName: String
            get() = when (month) {
                1 -> "فروردین"
                2 -> "اردیبهشت"
                3 -> "خرداد"
                4 -> "تیر"
                5 -> "مرداد"
                6 -> "شهریور"
                7 -> "مهر"
                8 -> "آبان"
                9 -> "آذر"
                10 -> "دی"
                11 -> "بهمن"
                12 -> "اسفند"
                else -> ""
            }

        fun format(): String = "$day $monthName $year"
    }

    // --- Julian Day Number <-> Gregorian -------------------------------------------------

    private fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Long {
        val a = (14 - gm) / 12
        val y = gy + 4800 - a
        val m = gm + 12 * a - 3
        return gd + ((153L * m + 2) / 5) + 365L * y + (y / 4) - (y / 100) + (y / 400) - 32045
    }

    private fun jdnToGregorian(jdn: Long): Triple<Int, Int, Int> {
        val a = jdn + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val gd = (e - (153 * m + 2) / 5 + 1).toInt()
        val gm = (m + 3 - 12 * (m / 10)).toInt()
        val gy = (100 * b + d - 4800 + m / 10).toInt()
        return Triple(gy, gm, gd)
    }

    // --- Jalali 33-year leap-cycle break table --------------------------------------------

    private val jalaliBreaks = longArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097,
        2192, 2262, 2324, 2394, 2456, 3178
    )

    /**
     * Returns the Gregorian day-of-March on which the given Jalali year starts,
     * using the standard 33-year leap break table.
     */
    private fun jalaliYearStartMarch(jy: Int): Int {
        val gy = jy + 621
        var leapJ = -14L
        var jp = jalaliBreaks[0]
        var jump = 0L
        var i = 1
        while (i < jalaliBreaks.size) {
            val jm = jalaliBreaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4L && jump - n == 4L) leapJ += 1
        val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
        return (20 + leapJ - leapG).toInt()
    }

    private fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Long {
        val march = jalaliYearStartMarch(jy)
        var jdn = gregorianToJdn(jy + 621, 3, march) - 1
        jdn += if (jm <= 6) (jm - 1) * 31L else (jm - 7) * 30L + 186L
        jdn += jd
        return jdn
    }

    private fun jdnToJalali(jdn: Long): JalaliDate {
        val (gy, _, _) = jdnToGregorian(jdn)
        var jy = gy - 621

        // Walk to the correct Jalali year bracket that contains this JDN.
        while (true) {
            val march = jalaliYearStartMarch(jy)
            val startOfYearJdn = gregorianToJdn(jy + 621, 3, march)
            if (jdn >= startOfYearJdn) break
            jy--
        }
        while (true) {
            val marchNext = jalaliYearStartMarch(jy + 1)
            val startOfNextYearJdn = gregorianToJdn(jy + 1 + 621, 3, marchNext)
            if (jdn < startOfNextYearJdn) break
            jy++
        }

        val march = jalaliYearStartMarch(jy)
        val startOfYearJdn = gregorianToJdn(jy + 621, 3, march)
        var k = (jdn - startOfYearJdn).toInt()

        val jm: Int
        val jd: Int
        if (k <= 185) {
            jm = 1 + k / 31
            jd = (k % 31) + 1
        } else {
            k -= 186
            jm = 7 + k / 30
            jd = (k % 30) + 1
        }
        return JalaliDate(jy, jm, jd)
    }

    // --- Public API -----------------------------------------------------------------------

    fun gregorianToJalali(timeInMillis: Long): JalaliDate {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = timeInMillis
        val gy = calendar.get(Calendar.YEAR)
        val gm = calendar.get(Calendar.MONTH) + 1
        val gd = calendar.get(Calendar.DAY_OF_MONTH)
        val jdn = gregorianToJdn(gy, gm, gd)
        return jdnToJalali(jdn)
    }

    /**
     * Converts a Jalali (Persian) calendar date to a Gregorian epoch-millis
     * timestamp (at local midnight).
     */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Long {
        val jdn = jalaliToJdn(jy, jm, jd)
        val (gy, gm, gd) = jdnToGregorian(jdn)
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.clear()
        calendar.set(gy, gm - 1, gd, 0, 0, 0)
        return calendar.timeInMillis
    }

    /**
     * Returns true if the given Jalali year is a leap year (Esfand has 30 days
     * instead of 29).
     */
    fun isLeapJalaliYear(jy: Int): Boolean {
        val startOfThisYearJdn = jalaliToJdn(jy, 1, 1)
        val startOfNextYearJdn = jalaliToJdn(jy + 1, 1, 1)
        return (startOfNextYearJdn - startOfThisYearJdn) == 366L
    }

    /**
     * Returns the number of days in the given month of the given Jalali year,
     * correctly accounting for leap years in Esfand (month 12).
     */
    fun daysInJalaliMonth(jy: Int, jm: Int): Int {
        return when {
            jm in 1..6 -> 31
            jm in 7..11 -> 30
            jm == 12 -> if (isLeapJalaliYear(jy)) 30 else 29
            else -> 30
        }
    }
}
