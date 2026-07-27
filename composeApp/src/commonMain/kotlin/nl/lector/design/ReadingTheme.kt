package nl.lector.design

import androidx.compose.ui.graphics.Color

/**
 * The four reading themes (HANDOFF.md §3) — a product feature with its own token
 * set. A dark app can open a paper-white page, so these never read [Chrome].
 */
enum class ReadingTheme(
    val label: String,
    val bg: Oklch,
    val fg: Oklch,
    /** Ceiling on how far the background may warm, per theme. */
    val ceilingBg: Float,
    /** Ceiling on how far the text may warm, per theme. */
    val ceilingFg: Float,
) {
    Paper("Paper", Oklch(0.975f, 0.005f, 88f), Oklch(0.22f, 0.014f, 60f), 0.22f, 0.08f),
    Sepia("Sepia", Oklch(0.93f, 0.028f, 80f), Oklch(0.29f, 0.030f, 52f), 0.26f, 0.10f),
    Grey("Grey", Oklch(0.36f, 0.006f, 90f), Oklch(0.87f, 0.006f, 90f), 0.14f, 0.16f),
    Black("Black", Oklch(0f, 0f, 0f), Oklch(0.87f, 0.004f, 90f), 0.05f, 0.34f);

    /** True while the surface is dark enough to need light status-bar icons. */
    val isDarkSurface: Boolean get() = this == Grey || this == Black
}

/** The single amber every theme warms toward. */
val Amber = Oklch(0.84f, 0.11f, 72f)

/**
 * Warmth is a continuous transform, not a set of presets (PRD §6.5, HANDOFF §3).
 * The per-theme ceiling is what makes one slider work on all four: on pure black
 * the background barely moves and the *text* warms instead, so the OLED promise
 * survives night reading.
 *
 * @param warmth 0f..1f
 */
fun ReadingTheme.backgroundAt(warmth: Float): Color =
    mix(bg, Amber, warmth * ceilingBg).toColor()

fun ReadingTheme.foregroundAt(warmth: Float): Color =
    mix(fg, Amber, warmth * ceilingFg).toColor()
