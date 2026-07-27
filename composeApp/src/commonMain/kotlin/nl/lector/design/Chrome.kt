package nl.lector.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The six app-chrome tokens from HANDOFF.md §2, plus the values derived from them.
 *
 * This governs chrome only. The four reading themes are a separate product feature
 * and deliberately do not inherit from here — see [ReadingTheme] and HANDOFF §6.8.
 */
@Immutable
data class Chrome(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val inverseSurface: Color,
    val inverseFg: Color,
    // derived
    val fill: Color,
    val fgSoft: Color,
    val accentSoft: Color,
    val tonal: Color,
    val isDark: Boolean,
) {
    companion object {
        private fun build(
            bg: Oklch, surface: Oklch, fg: Oklch, muted: Oklch, border: Oklch,
            accent: Oklch, onAccent: Oklch, inverseSurface: Oklch, inverseFg: Oklch,
            isDark: Boolean,
        ) = Chrome(
            bg = bg.toColor(),
            surface = surface.toColor(),
            fg = fg.toColor(),
            muted = muted.toColor(),
            border = border.toColor(),
            accent = accent.toColor(),
            onAccent = onAccent.toColor(),
            inverseSurface = inverseSurface.toColor(),
            inverseFg = inverseFg.toColor(),
            // --fill / --fg-soft / --accent-soft are mixes with `transparent`,
            // which CSS resolves to the same colour at reduced alpha.
            fill = fg.toColor(0.07f),
            fgSoft = fg.toColor(0.05f),
            accentSoft = accent.toColor(0.12f),
            // Material tonal container = accent at low chroma over the surface.
            tonal = mix(surface, accent, 0.15f).toColor(),
            isDark = isDark,
        )

        val Light = build(
            bg = Oklch(0.98f, 0.004f, 95f),
            surface = Oklch(1.00f, 0.002f, 95f),
            fg = Oklch(0.20f, 0.018f, 70f),
            muted = Oklch(0.48f, 0.012f, 70f),
            border = Oklch(0.90f, 0.006f, 95f),
            accent = Oklch(0.52f, 0.10f, 28f),
            onAccent = Oklch(0.99f, 0.003f, 95f),
            inverseSurface = Oklch(0.26f, 0.012f, 70f),
            inverseFg = Oklch(0.97f, 0.004f, 95f),
            isDark = false,
        )

        val Dark = build(
            bg = Oklch(0.175f, 0.008f, 70f),
            surface = Oklch(0.225f, 0.009f, 70f),
            fg = Oklch(0.93f, 0.005f, 95f),
            muted = Oklch(0.67f, 0.011f, 78f),
            border = Oklch(0.31f, 0.009f, 70f),
            accent = Oklch(0.68f, 0.115f, 32f),
            // Not white: the dark accent is light enough that white fails
            // contrast on it (HANDOFF §2).
            onAccent = Oklch(0.17f, 0.012f, 40f),
            inverseSurface = Oklch(0.90f, 0.006f, 95f),
            inverseFg = Oklch(0.18f, 0.012f, 70f),
            isDark = true,
        )
    }
}

val LocalChrome = staticCompositionLocalOf { Chrome.Light }

/** Material 3 shape scale, as used by the Android prototype. */
object Shape {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 28.dp
}

/** App theme setting. Chrome only — reading themes stay independent. */
enum class Appearance(val label: String) { System("System"), Light("Light"), Dark("Dark") }
