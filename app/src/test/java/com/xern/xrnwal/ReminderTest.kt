package com.xern.xrnwal

import com.xern.xrnwal.data.Reminder
import com.xern.xrnwal.work.Reminders
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderTest {
    private fun reminder(repeat: Int, start: LocalDate) =
        Reminder(id = 1, name = "Evening", minuteOfDay = 20 * 60, repeat = repeat, startEpochDay = start.toEpochDay())

    @Test
    fun aWeeklyReminderKeepsItsWeekdayWhenItsAlarmIsSetAgain() {
        // Made on Monday 2 November 2026 for 20:00.
        val weekly = reminder(1, LocalDate.of(2026, 11, 2))
        // The phone restarts, or another reminder is saved, on Wednesday morning.
        assertEquals(LocalDateTime.of(2026, 11, 9, 20, 0), Reminders.next(weekly, LocalDateTime.of(2026, 11, 4, 9, 0)))
        // On Monday before eight it is that evening; once it has gone off, the Monday after.
        assertEquals(LocalDateTime.of(2026, 11, 9, 20, 0), Reminders.next(weekly, LocalDateTime.of(2026, 11, 9, 19, 59)))
        assertEquals(LocalDateTime.of(2026, 11, 16, 20, 0), Reminders.next(weekly, LocalDateTime.of(2026, 11, 9, 20, 0)))
    }

    @Test
    fun aMonthlyReminderKeepsItsDayOfTheMonth() {
        // Made on 15 October.
        val monthly = reminder(2, LocalDate.of(2026, 10, 15))
        // Set again on 20 November: 15 December, not 20 December.
        assertEquals(LocalDateTime.of(2026, 12, 15, 20, 0), Reminders.next(monthly, LocalDateTime.of(2026, 11, 20, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 15, 20, 0), Reminders.next(monthly, LocalDateTime.of(2026, 11, 3, 9, 0)))

        // Made on the 31st: the last day of a shorter month, then the 31st again.
        val last = reminder(2, LocalDate.of(2027, 1, 31))
        assertEquals(LocalDateTime.of(2027, 2, 28, 20, 0), Reminders.next(last, LocalDateTime.of(2027, 2, 1, 9, 0)))
        assertEquals(LocalDateTime.of(2027, 3, 31, 20, 0), Reminders.next(last, LocalDateTime.of(2027, 2, 28, 21, 0)))
    }

    @Test
    fun aReminderKeepsItsDayThroughACopyAndOneFromBeforeCountsFromToday() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val weekly = reminder(1, LocalDate.of(2026, 11, 2))
        assertEquals(weekly, json.decodeFromString<Reminder>(json.encodeToString(weekly)))
        val older = json.decodeFromString<Reminder>("""{"id":1,"name":"Evening","minuteOfDay":1200,"repeat":1}""")
        assertEquals(LocalDate.now(), older.start)
    }

    @Test
    fun aDailyReminderIsTodayOrTomorrow() {
        val daily = reminder(0, LocalDate.of(2026, 11, 2))
        assertEquals(LocalDateTime.of(2026, 11, 4, 20, 0), Reminders.next(daily, LocalDateTime.of(2026, 11, 4, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 5, 20, 0), Reminders.next(daily, LocalDateTime.of(2026, 11, 4, 20, 0)))
    }
}
