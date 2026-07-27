package nl.lector.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The colour maths is the one place in this app where being subtly wrong is
 * invisible in code review and obvious on screen. These guard the two claims
 * HANDOFF.md makes explicitly.
 */
class OklchTest {

    @Test
    fun `achromatic extremes round-trip to black and white`() {
        val black = Oklch(0f, 0f, 0f).toColor()
        assertEquals(0f, black.red, 0.001f)
        assertEquals(0f, black.green, 0.001f)
        assertEquals(0f, black.blue, 0.001f)

        val white = Oklch(1f, 0f, 0f).toColor()
        assertEquals(1f, white.red, 0.01f)
        assertEquals(1f, white.green, 0.01f)
        assertEquals(1f, white.blue, 0.01f)
    }

    @Test
    fun `mix returns the endpoints unchanged`() {
        val a = Oklch(0.2f, 0.05f, 30f)
        val b = Oklch(0.9f, 0.11f, 200f)
        assertEquals(a, mix(a, b, 0f))
        assertEquals(b.l, mix(a, b, 1f).l, 0.0001f)
        assertEquals(b.c, mix(a, b, 1f).c, 0.0001f)
    }

    @Test
    fun `mix takes the shorter hue arc across the wrap point`() {
        // 350° → 10° is 20° forward, not 340° backward.
        val m = mix(Oklch(0.5f, 0.1f, 350f), Oklch(0.5f, 0.1f, 10f), 0.5f)
        assertEquals(360f, m.h, 0.001f)
    }

    /**
     * "On pure black the background barely moves and the text warms instead — the
     * OLED promise survives night reading. Get this wrong and the black theme turns
     * brown." (HANDOFF §3)
     */
    @Test
    fun `black theme stays black at full warmth`() {
        val bg = ReadingTheme.Black.backgroundAt(1f)
        assertTrue(
            bg.red < 0.09f && bg.green < 0.09f && bg.blue < 0.09f,
            "black background warmed to ${bg.red}/${bg.green}/${bg.blue}, expected near-black",
        )
    }

    @Test
    fun `black theme warms its text instead`() {
        val cold = ReadingTheme.Black.foregroundAt(0f)
        val warm = ReadingTheme.Black.foregroundAt(1f)
        assertTrue(warm.red > warm.blue, "warmed text should be warmer than it is cool")
        assertTrue(
            (warm.red - warm.blue) > (cold.red - cold.blue),
            "warmth should widen the red/blue gap on the black theme",
        )
    }

    @Test
    fun `paper theme warms its background and stays light`() {
        val cold = ReadingTheme.Paper.backgroundAt(0f)
        val warm = ReadingTheme.Paper.backgroundAt(1f)
        assertTrue(warm.red > warm.blue, "warmed paper should be amber-leaning")
        assertTrue(
            (warm.red - warm.blue) > (cold.red - cold.blue),
            "warmth should move the paper background, not just the text",
        )
        assertTrue(warm.green > 0.75f, "paper must stay a light surface, was ${warm.green}")
    }

    @Test
    fun `every reading theme keeps usable contrast at full warmth`() {
        ReadingTheme.entries.forEach { theme ->
            val bg = theme.backgroundAt(1f)
            val fg = theme.foregroundAt(1f)
            val delta = kotlin.math.abs(luminance(bg) - luminance(fg))
            assertTrue(delta > 0.35f, "${theme.label} collapsed to $delta contrast at full warmth")
        }
    }

    private fun luminance(c: androidx.compose.ui.graphics.Color) =
        0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
}
