package nl.lector.data

import nl.lector.screen.pitchFromSlider
import nl.lector.screen.rateFromSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/** The two bits of Settings arithmetic that a wrong constant would quietly break. */
class SettingsValueTest {

    @Test
    fun `byte sizes read as a person would say them`() {
        assertEquals("empty", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 kB", formatBytes(1536))
        assertEquals("12.0 MB", formatBytes(12L * 1024 * 1024))
    }

    @Test
    fun `the pitch slider spans 0_7 to 1_4 and passes through the voice's own`() {
        assertEquals(0.7f, pitchFromSlider(0f))
        assertEquals(1.4f, pitchFromSlider(1f))
        assertEquals(1.0f, pitchFromSlider(0.43f), 0.051f, "no way to get back to neutral")
    }

    @Test
    fun `the rate slider still spans 0_7 to 2_0`() {
        assertEquals(0.7f, rateFromSlider(0f))
        assertEquals(2.0f, rateFromSlider(1f))
    }

    @Test
    fun `both sliders step in five hundredths, so no readout shows three decimals`() {
        (0..20).forEach { i ->
            val t = i / 20f
            assertTrue((rateFromSlider(t) * 100).roundToInt() % 5 == 0, "rate at $t")
            assertTrue((pitchFromSlider(t) * 100).roundToInt() % 5 == 0, "pitch at $t")
        }
    }
}
