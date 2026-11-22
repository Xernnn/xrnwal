package com.xern.xrnwal.work

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The size each widget's chart is drawn at, worked out from the widget's size as the launcher reports
 * it, so a stretched widget gets a sharp chart rather than a blown-up one. What is taken off is the
 * layout's padding and the rows above and below the chart; the layouts give the chart the rest.
 */
object ChartSizes {
    /**
     * The most one chart may take. A widget update may carry bitmaps up to 1.5 times the screen's pixels
     * (15.5 MB on a 1080 by 2400 phone); a chart stays well below that, and below the 1 MB a binder call
     * carries should a launcher ever copy it whole. A larger chart is drawn smaller and scaled up.
     */
    const val MAX_BYTES = 1_000_000L

    /** Pace's ring: the padding at its sides, and the padding, title row and gap above and below it. */
    private const val RING_SIDES = 29f
    private const val RING_ROWS = 55f

    /** Each chart's sides: the layouts' padding, and the week's three dp of room for the last letter. */
    private const val BAR_SIDES = 32f

    /**
     * Rhythm's rows other than the chart: padding, the title row, the hero figure and, for the week,
     * the letters. Measured from the layouts; the chart's view takes what is left and stretches the
     * bitmap to it, so a dp or two either way only stretches the bars.
     */
    private const val WEEK_ROWS = 129f
    private const val MONTH_ROWS = 111f

    data class Chart(val widthDp: Float, val heightDp: Float)

    data class Pixels(val width: Int, val height: Int) {
        val bytes: Long get() = width.toLong() * height * 4
    }

    fun paceRing(widthDp: Float, heightDp: Float): Chart {
        val side = min(widthDp - RING_SIDES, heightDp - RING_ROWS).coerceIn(72f, 160f)
        return Chart(side, side)
    }

    fun paceBar(widthDp: Float): Chart = Chart((widthDp - BAR_SIDES).coerceAtLeast(40f), 22f)

    fun week(widthDp: Float, heightDp: Float): Chart =
        Chart((widthDp - BAR_SIDES).coerceAtLeast(40f), (heightDp - WEEK_ROWS).coerceIn(20f, 160f))

    fun month(widthDp: Float, heightDp: Float): Chart =
        Chart((widthDp - BAR_SIDES).coerceAtLeast(40f), (heightDp - MONTH_ROWS).coerceIn(20f, 160f))

    /** [chart] at [density], scaled down evenly when it would pass [MAX_BYTES]. */
    fun pixels(chart: Chart, density: Float): Pixels {
        var width = chart.widthDp * density
        var height = chart.heightDp * density
        val bytes = width * height * 4
        if (bytes > MAX_BYTES) {
            val k = sqrt(MAX_BYTES / bytes)
            width *= k
            height *= k
        }
        return Pixels(max(1, width.toInt()), max(1, height.toInt()))
    }
}
