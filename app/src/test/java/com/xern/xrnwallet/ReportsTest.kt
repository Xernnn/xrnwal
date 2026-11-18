package com.xern.xrnwallet

import com.xern.xrnwallet.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reports' charts: which points of a squeezed chart are labelled. */
class ReportsTest {
    private fun days(from: LocalDate, count: Int) = (0 until count).map { from.plusDays(it.toLong()) }

    @Test
    fun aChartWithRoomLabelsEveryPoint() {
        val week = days(LocalDate.of(2026, 11, 16), 7)
        assertEquals((0..6).toList(), Stats.fitLabels(week, monthly = false, room = 11))
        val months = (1..12).map { LocalDate.of(2026, it, 1) }
        assertEquals((0..11).toList(), Stats.fitLabels(months, monthly = true, room = 12))
    }

    @Test
    fun aSqueezedMonthIsLabelledEveryThirdDay() {
        val november = days(LocalDate.of(2026, 11, 1), 30)
        val labelled = Stats.fitLabels(november, monthly = false, room = 11).map { november[it].dayOfMonth }
        assertEquals(listOf(1, 4, 7, 10, 13, 16, 19, 22, 25, 28), labelled)
    }

    @Test
    fun aSqueezedQuarterIsLabelledOnTheFirstEleventhAndTwentyFirst() {
        val quarter = days(LocalDate.of(2026, 8, 22), 90)
        val labelled = Stats.fitLabels(quarter, monthly = false, room = 11).map { quarter[it] }
        assertEquals(LocalDate.of(2026, 9, 1), labelled.first())
        assertTrue(labelled.all { it.dayOfMonth in setOf(1, 11, 21) })
        assertTrue(labelled.size <= 11)
    }

    @Test
    fun aSqueezedYearOfDaysIsLabelledOnMonthStarts() {
        val year = days(LocalDate.of(2026, 1, 1), 365)
        val labelled = Stats.fitLabels(year, monthly = false, room = 11).map { year[it] }
        assertEquals(listOf(1, 3, 5, 7, 9, 11), labelled.map { it.monthValue })
        assertTrue(labelled.all { it.dayOfMonth == 1 })
    }

    @Test
    fun noTwoLabelsComeCloserThanTheRoomAllows() {
        // A month starting on the 15th crosses a month's end, where the 28th and the 1st sit close.
        val months = listOf(28, 29, 30, 31).map { days(LocalDate.of(2026, 1, 15), it) } +
            listOf(28, 30, 31).map { days(LocalDate.of(2026, 2, 1), it) }
        for (room in 4..14) {
            months.forEach { dates ->
                val labelled = Stats.fitLabels(dates, monthly = false, room = room)
                val gap = kotlin.math.ceil(dates.size / room.toDouble()).toInt()
                labelled.zipWithNext().forEach { (a, b) -> assertTrue("room $room: $a and $b", b - a >= gap) }
                assertTrue(labelled.size <= room)
            }
        }
    }
}
