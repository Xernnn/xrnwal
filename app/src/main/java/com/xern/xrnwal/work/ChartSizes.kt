package com.xern.xrnwal.work

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The size each widget's chart is drawn at, worked out from the widget's size as the launcher reports
 * it, so a stretched widget gets a sharp chart rather than a blown-up one. What is taken off is the
 * layout's padding around the chart.
 */
object ChartSizes {
    /**
     * The most one chart may take. A widget update may carry bitmaps up to 1.5 times the screen's pixels
     * (15.5 MB on a 1080 by 2400 phone), and each chart goes in twice, once for light mode and once for
     * dark; a chart stays well below that, and below the 1 MB a binder call carries should a launcher
     * ever copy it whole. A larger chart is drawn smaller and scaled up.
     */
    const val MAX_BYTES = 1_000_000L

    /** Today's ring: the padding at its sides (19 dp each) and above and below it (22 and 14 dp). */
    private const val DIAL_SIDES = 38f
    private const val DIAL_ROWS = 36f

    /** Month's bar: the layout's padding at its sides. Its height is the layout's, 18 dp. */
    private const val BAR_SIDES = 32f
    private const val BAR_HEIGHT = 18f

    data class Chart(val widthDp: Float, val heightDp: Float)

    data class Pixels(val width: Int, val height: Int) {
        val bytes: Long get() = width.toLong() * height * 4
    }

    fun dial(widthDp: Float, heightDp: Float): Chart {
        val side = min(widthDp - DIAL_SIDES, heightDp - DIAL_ROWS).coerceAtLeast(60f)
        return Chart(side, side)
    }

    fun bar(widthDp: Float): Chart = Chart((widthDp - BAR_SIDES).coerceAtLeast(40f), BAR_HEIGHT)

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
