package com.example.lifeos.util

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Basic utility for converting Gregorian dates to Jalali (Persian) dates.
 * In a real production app, consider using a comprehensive library like `com.github.samanzamani:PersianDate`.
 * This is a lightweight robust implementation for core display logic.
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

    fun gregorianToJalali(timeInMillis: Long): JalaliDate {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = timeInMillis

        var gy = calendar.get(Calendar.YEAR)
        var gm = calendar.get(Calendar.MONTH) + 1
        var gd = calendar.get(Calendar.DAY_OF_MONTH)

        var jy: Int
        val jm: Int
        val jd: Int

        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        var gy2 = if (gm > 2) (gy + 1) else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + -1

        for (i in 0 until gm - 1) {
            days += gDaysInMonth[i]
        }

        if (gm > 2 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            days++
        }

        jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461

        if (days > 365) {
            jy += ((days - 1) / 365)
            days = (days - 1) % 365
        }

        var i = 0
        while (i < 11 && days >= jDaysInMonth[i]) {
            days -= jDaysInMonth[i]
            i++
        }

        jm = i + 1
        jd = days + 1

        return JalaliDate(jy, jm, jd)
    }
}
