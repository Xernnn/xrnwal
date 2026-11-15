package com.xern.xrnwallet

import com.xern.xrnwallet.domain.Dates
import com.xern.xrnwallet.ui.Art
import com.xern.xrnwallet.ui.hourOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** The colour icons, the time wheels' arithmetic and how times read. */
class LookTest {
    @After
    fun twelveHourAgain() {
        Dates.use24Hour = false
    }

    @Test
    fun aPlainIconDrawsInColourAndKeepsItsSymbolAsAFallback() {
        // Every icon stored before the colour drawings is plain, so every category changes at once.
        assertNotNull(Art.drawable("🍺"))
        assertNotNull(Art.vector("🍺"))
        // Typed with a variation selector, as an imported category may be.
        assertEquals(Art.drawable("🏋"), Art.drawable("🏋️"))
    }

    @Test
    fun theOtherStylesAndEmojiKeepTheirOwnLook() {
        assertNull(Art.drawable("s2:🍺"))
        assertNull(Art.drawable("s5:🍺"))
        assertNull(Art.drawable("e:🍺"))
        assertNotNull(Art.vector("s5:🍺"))
        assertEquals(Art.vector("🍺"), Art.vector("s5:🍺"))
        assertEquals(Art.STYLES, Art.tabOf("e:🍺"))
        assertEquals(4, Art.tabOf("s5:🍺"))
        assertEquals(Art.STYLES + 1, Art.tabNames.size)
    }

    @Test
    fun everyIconOfferedHasAColourDrawing() {
        val missing = (1..1).flatMap { Art.choices(it) }.filter { Art.drawable(it) == null }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun darkDrawingsAreKnown() {
        assertTrue(Art.isDark("🎵"))
        assertFalse(Art.isDark("🍺"))
        // A dark subject in another style is a symbol, which takes its own colour.
        assertFalse(Art.isDark("s2:🎵"))
    }

    @Test
    fun twelveHourWheelsGiveTheHourOfTheDay() {
        assertEquals(0, hourOf(12, pm = false))
        assertEquals(1, hourOf(1, pm = false))
        assertEquals(11, hourOf(11, pm = false))
        assertEquals(12, hourOf(12, pm = true))
        assertEquals(13, hourOf(1, pm = true))
        assertEquals(23, hourOf(11, pm = true))
    }

    @Test
    fun timesReadAsThePhoneWritesThem() {
        assertEquals("4:05 PM", Dates.time(LocalTime.of(16, 5)))
        Dates.use24Hour = true
        assertEquals("16:05", Dates.time(LocalTime.of(16, 5)))
        assertEquals("09:30", Dates.time(LocalTime.of(9, 30)))
        assertEquals("00:00", Dates.time(LocalTime.MIDNIGHT))
    }
}
