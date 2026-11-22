package com.xern.xrnwal

import com.xern.xrnwal.work.ChartSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSizesTest {
    /** A 1080 by 2400 phone at 440 dpi, as the Redmi Note 14 is: 393 by 873 dp. */
    private val density = 2.75f
    private val screenWidthDp = 1080 / density
    private val screenHeightDp = 2400 / density

    /** What one widget update may carry in bitmaps on that phone: 1.5 times the screen's pixels. */
    private val remoteViewsLimit = 1080L * 2400 * 4 * 3 / 2

    private fun all(widthDp: Float, heightDp: Float) = listOf(
        ChartSizes.paceRing(widthDp, heightDp),
        ChartSizes.paceBar(widthDp),
        ChartSizes.week(widthDp, heightDp),
        ChartSizes.month(widthDp, heightDp),
    )

    @Test
    fun chartsAtTheirPlacedSizeAreDrawnAtFullSharpness() {
        val ring = ChartSizes.paceRing(172f, 172f)
        assertEquals(ChartSizes.Pixels((ring.widthDp * density).toInt(), (ring.heightDp * density).toInt()), ChartSizes.pixels(ring, density))
        val month = ChartSizes.month(361f, 172f)
        assertEquals(ChartSizes.Pixels((329 * density).toInt(), (month.heightDp * density).toInt()), ChartSizes.pixels(month, density))
        all(361f, 172f).forEach { assertTrue(ChartSizes.pixels(it, density).bytes <= ChartSizes.MAX_BYTES) }
    }

    @Test
    fun aWidgetStretchedOverTheWholeScreenStaysWithinTheLimits() {
        all(screenWidthDp, screenHeightDp).forEach { chart ->
            val px = ChartSizes.pixels(chart, density)
            assertTrue("$chart is ${px.bytes} bytes", px.bytes <= ChartSizes.MAX_BYTES)
            assertTrue(px.bytes * 10 < remoteViewsLimit)
        }
    }

    @Test
    fun aChartTooBigForTheLimitKeepsItsShape() {
        val chart = ChartSizes.Chart(400f, 400f)
        val px = ChartSizes.pixels(chart, 4f)
        assertTrue(px.bytes <= ChartSizes.MAX_BYTES)
        assertEquals(px.width.toFloat(), px.height.toFloat(), 1f)
    }

    @Test
    fun aWidgetShrunkBelowItsMinimumStillGetsAChart() {
        all(40f, 40f).forEach { chart ->
            assertTrue(chart.widthDp >= 40f && chart.heightDp >= 20f)
        }
    }
}
