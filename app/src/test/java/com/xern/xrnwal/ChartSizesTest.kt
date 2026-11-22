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

    private fun all(widthDp: Float, heightDp: Float) = listOf(ChartSizes.dial(widthDp, heightDp), ChartSizes.bar(widthDp))

    @Test
    fun chartsAtTheirPlacedSizeAreDrawnAtFullSharpness() {
        // Two by two and four by two on the Redmi Note 14's home screen: 173 by 187 dp and 376 by 187 dp.
        val ring = ChartSizes.dial(173f, 187f)
        assertEquals(ChartSizes.Chart(135f, 135f), ring)
        assertEquals(ChartSizes.Pixels((135 * density).toInt(), (135 * density).toInt()), ChartSizes.pixels(ring, density))
        val bar = ChartSizes.bar(376f)
        assertEquals(ChartSizes.Pixels((344 * density).toInt(), (18 * density).toInt()), ChartSizes.pixels(bar, density))
        all(376f, 187f).forEach { assertTrue(ChartSizes.pixels(it, density).bytes <= ChartSizes.MAX_BYTES) }
    }

    @Test
    fun aWidgetStretchedOverTheWholeScreenStaysWithinTheLimits() {
        all(screenWidthDp, screenHeightDp).forEach { chart ->
            val px = ChartSizes.pixels(chart, density)
            assertTrue("$chart is ${px.bytes} bytes", px.bytes <= ChartSizes.MAX_BYTES)
            // Drawn once for light mode and once for dark, with room to spare.
            assertTrue(2 * px.bytes * 5 < remoteViewsLimit)
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
            assertTrue(chart.widthDp >= 40f && chart.heightDp >= 18f)
        }
    }
}
