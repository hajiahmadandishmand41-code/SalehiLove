package com.salehilove.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PersianCalendarTest {

    @Test
    fun `known date nowruz conversion`() {
        // 1 Farvardin 1403 == 20 March 2024
        val g = LocalDate.of(2024, 3, 20)
        val j = PersianCalendar.toJalali(g)
        assertEquals(1403, j.year)
        assertEquals(1, j.month)
        assertEquals(1, j.day)
        assertEquals("۱ فروردین ۱۴۰۳", j.toPersianString())
    }

    @Test
    fun `known date mid year conversion`() {
        // 15 Mehr 1403 == 6 October 2024
        val g = LocalDate.of(2024, 10, 6)
        val j = PersianCalendar.toJalali(g)
        assertEquals(1403, j.year)
        assertEquals(7, j.month)
        assertEquals(15, j.day)
    }

    @Test
    fun `round trip is stable for a full leap cycle`() {
        var date = LocalDate.of(2023, 3, 21)
        repeat(365 * 5) {
            val j = PersianCalendar.toJalali(date)
            val back = PersianCalendar.toGregorian(j)
            assertEquals("round trip failed for $date", date, back)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `esfand length in leap and normal years`() {
        assertEquals(30, PersianCalendar.jalaliMonthLength(1403, 12)) // 1403 is leap
        assertEquals(29, PersianCalendar.jalaliMonthLength(1402, 12)) // 1402 is normal
    }

    @Test
    fun `weekday names map correctly`() {
        // 2024-10-06 is a Sunday -> یکشنبه
        assertEquals("یکشنبه", PersianCalendar.weekdayName(LocalDate.of(2024, 10, 6)))
    }

    @Test
    fun `leap year detection`() {
        assertTrue(PersianCalendar.isLeapYear(1403))
        assertEquals(false, PersianCalendar.isLeapYear(1402))
    }
}
