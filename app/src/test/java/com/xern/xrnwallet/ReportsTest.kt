package com.xern.xrnwallet

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.SettingsStore
import com.xern.xrnwallet.data.Stats
import com.xern.xrnwallet.ui.CategoryColours
import com.xern.xrnwallet.ui.categoryColoursDark
import com.xern.xrnwallet.ui.categoryColoursLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/** Reports' charts: how the trend opens, which points of a squeezed chart are labelled, the category colours. */
class ReportsTest {
    private fun opened(json: String?): Settings {
        val prefs: SharedPreferences = MemoryPrefs()
        if (json != null) prefs.edit().putString("settings", json).apply()
        return SettingsStore(prefs).settings.value
    }

    @Test
    fun theTrendOpensWithEveryPointShowingUnlessScrollingWasChosen() {
        assertFalse(opened(null).trendScroll)
        // 0.5.2 wrote trendFit down as false whether or not anyone chose it, so it no longer counts.
        assertFalse(opened("""{"trendFit":false}""").trendScroll)
        assertTrue(opened("""{"trendScroll":true}""").trendScroll)
        // A choice made now outlives a restart.
        val prefs = MemoryPrefs()
        SettingsStore(prefs).update { it.copy(trendScroll = true) }
        assertTrue(SettingsStore(prefs).settings.value.trendScroll)
    }

    /** OKLab lightness and the distance between two colours, x100 as the dataviz validator counts it. */
    private fun oklab(colour: Color): Triple<Double, Double, Double> {
        fun linear(c: Float) = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        val (r, g, b) = Triple(linear(colour.red), linear(colour.green), linear(colour.blue))
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Triple(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun distance(a: Color, b: Color): Double {
        val (l1, a1, b1) = oklab(a)
        val (l2, a2, b2) = oklab(b)
        return 100 * sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun checkPalette(colours: CategoryColours, surface: Color, lightness: ClosedFloatingPointRange<Double>, minContrast: Float) {
        assertEquals(8, colours.hues.toSet().size)
        colours.hues.forEach { hue ->
            assertTrue("$hue lightness ${oklab(hue).first}", oklab(hue).first in lightness)
            assertTrue("$hue contrast ${contrast(hue, surface)}", contrast(hue, surface) >= minContrast)
        }
        // Neighbouring ranks sit next to each other in the pie and the stacked bars.
        colours.hues.zipWithNext().forEach { (a, b) -> assertTrue("$a next to $b", distance(a, b) >= 15) }
        // A ninth category on is grey, never a hue again.
        assertEquals(colours.other, colours.ranked(8))
        assertEquals(colours.other, colours.ranked(19))
    }

    @Test
    fun theCategoryColoursReadOnTheDarkPageAndStayApart() {
        // The old colours were pale on the dark page: the yellow at OKLab lightness 0.89.
        checkPalette(categoryColoursDark, Color(0xFF1E1E20), 0.48..0.67, 3f)
    }

    @Test
    fun theCategoryColoursReadOnTheLightPageAndStayApart() {
        checkPalette(categoryColoursLight, Color.White, 0.43..0.77, 2f)
    }
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
