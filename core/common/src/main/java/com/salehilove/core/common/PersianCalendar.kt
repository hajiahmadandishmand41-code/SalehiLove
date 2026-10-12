package com.salehilove.core.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Dependency-free Jalali (Solar Hijri) calendar conversion.
 *
 * The conversion arithmetic is a faithful Kotlin port of the widely used
 * jalaali-js algorithm (Behrang Norouzinia, MIT), which handles the 33-year
 * leap cycle and the historical break years correctly. It is covered by unit
 * tests incl. a 5-year round trip.
 */
object PersianCalendar {

    private val jalaliMonthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    private val weekdayNames = arrayOf(
        "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه",
    )

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** Converts ASCII digits in a string to Persian digits. */
    fun toPersianDigits(text: String): String = buildString(text.length) {
        text.forEach { c -> append(if (c in '0'..'9') persianDigits[c - '0'] else c) }
    }

    /** Converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits back to ASCII. */
    fun toEnglishDigits(text: String): String = buildString(text.length) {
        text.forEach { c ->
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                },
            )
        }
    }

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        /** e.g. "۱۲ مهر ۱۴۰۴" */ fun toPersianString(): String =
            toPersianDigits("$day ${jalaliMonthNames[month - 1]} $year")
    }

    // ---- jalaali-js core (ports of div/mod use truncating semantics) ----

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCal {
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        require(jy >= jp && jy < breaks[bl - 1]) { "Invalid Jalaali year $jy" }
        var jm = breaks[bl - 1]
        var found = false
        for (i in 1 until bl) {
            jm = breaks[i]
            jump = jm - jp
            if (jy < jm) {
                found = true
                break
            }
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        if (!found) {
            jump = jm - jp
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + (n % 33 + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = (gy + (gm - 8) / 6 + 100100) * 1461 / 4 +
            (153 * ((gm + 9) % 12) + 2) / 5 +
            gd - 34840408
        d = d - (gy + 100100 + (gm - 8) / 6) / 100 * 3 / 4 + 752
        return d
    }

    private class GDate(val gy: Int, val gm: Int, val gd: Int)

    private fun d2g(jdn: Int): GDate {
        var j = 4 * jdn + 139361631
        j = j + (4 * jdn + 183187720) / 146097 * 3 / 4 * 4 - 3908
        val i = j % 1461 / 4 * 5 + 308
        val gd = i % 153 / 5 + 1
        val gm = i / 153 % 12 + 1
        val gy = j / 1461 - 100100 + (8 - gm) / 6
        return GDate(gy, gm, gd)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - jm / 7 * (jm - 7) + jd - 1
    }

    // ---- public API ----------------------------------------------------------

    /** Gregorian -> Jalali */
    fun toJalali(gregorian: LocalDate): JalaliDate {
        val jdn = g2d(gregorian.year, gregorian.monthValue, gregorian.dayOfMonth)
        val gy = d2g(jdn).gy
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                val jm = 1 + k / 31
                val jd = k % 31 + 1
                return JalaliDate(jy, jm, jd)
            } else {
                k -= 186
            }
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        val jm = 7 + k / 30
        val jd = k % 30 + 1
        return JalaliDate(jy, jm, jd)
    }

    /** Jalali -> Gregorian */
    fun toGregorian(jalali: JalaliDate): LocalDate {
        require(jalali.month in 1..12) { "month must be 1..12" }
        require(jalali.day in 1..jalaliMonthLength(jalali.year, jalali.month)) {
            "day out of range for ${jalali.year}/${jalali.month}"
        }
        val jdn = j2d(jalali.year, jalali.month, jalali.day)
        val g = d2g(jdn)
        return LocalDate.of(g.gy, g.gm, g.gd)
    }

    fun isLeapYear(jalaliYear: Int): Boolean = jalCal(jalaliYear).leap == 0

    fun jalaliMonthLength(year: Int, month: Int): Int = when (month) {
        in 1..6 -> 31
        in 7..11 -> 30
        12 -> if (isLeapYear(year)) 30 else 29
        else -> throw IllegalArgumentException("month must be 1..12")
    }

    fun weekdayName(date: LocalDate): String = weekdayNames[date.dayOfWeek.value - 1]

    /** e.g. "شنبه ۱۲ مهر ۱۴۰۴ – ۰۹:۳۰" */
    fun formatDateTime(dateTime: LocalDateTime): String {
        val j = toJalali(dateTime.toLocalDate())
        val time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        return toPersianDigits(
            "${weekdayName(dateTime.toLocalDate())} ${j.day} ${jalaliMonthNames[j.month - 1]} ${j.year} – $time",
        )
    }

    fun formatDate(date: LocalDate): String = toJalali(date).toPersianString()

    fun formatTime(dateTime: LocalDateTime): String =
        toPersianDigits(dateTime.format(DateTimeFormatter.ofPattern("HH:mm")))

    fun monthName(month: Int): String = jalaliMonthNames[month - 1]
}
