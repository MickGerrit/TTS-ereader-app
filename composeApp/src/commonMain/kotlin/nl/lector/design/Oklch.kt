package nl.lector.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Every colour in this app is authored in Oklch, exactly as the tokens are written
 * in HANDOFF.md §2 and §3. Nothing here invents a palette: the numbers in
 * [Chrome] and [ReadingTheme] are transcribed verbatim from the handoff, and this
 * file is only the conversion that CSS `oklch()` and `color-mix(in oklch, …)`
 * would have done in the prototype.
 *
 * Keeping the authored space means the warmth transform in [ReadingTheme] stays a
 * two-line mix instead of a table of hand-picked hex values that drift apart.
 */
@Immutable
data class Oklch(val l: Float, val c: Float, val h: Float)

/** `oklch(l% c h)` → sRGB. [alpha] covers CSS `color-mix(…, transparent)`. */
fun Oklch.toColor(alpha: Float = 1f): Color {
    val hr = h * (PI.toFloat() / 180f)
    val a = c * cos(hr)
    val b = c * sin(hr)

    // Oklab → LMS (cube root space) → linear sRGB, Björn Ottosson's matrices.
    val lp = l + 0.3963377774f * a + 0.2158037573f * b
    val mp = l - 0.1055613458f * a - 0.0638541728f * b
    val sp = l - 0.0894841775f * a - 1.2914855480f * b
    val lc = lp * lp * lp
    val mc = mp * mp * mp
    val sc = sp * sp * sp

    return Color(
        red = encode(4.0767416621f * lc - 3.3077115913f * mc + 0.2309699292f * sc),
        green = encode(-1.2684380046f * lc + 2.6097574011f * mc - 0.3413193965f * sc),
        blue = encode(-0.0041960863f * lc - 0.7034186147f * mc + 1.7076147010f * sc),
        alpha = alpha,
    )
}

/** Linear → sRGB gamma. Out-of-gamut channels clamp, as the browser's would. */
private fun encode(x: Float): Float {
    // The linear branch also catches negatives, which would make pow() NaN.
    val v = if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f
    return v.coerceIn(0f, 1f)
}

/**
 * CSS `color-mix(in oklch, a, b t%)` — polar, taking the shorter hue arc.
 *
 * Mixing in Oklch rather than Oklab is not pedantry here: the warmth control mixes
 * every reading theme toward one amber, and the hue path is what keeps the black
 * theme from going brown on the way (HANDOFF §3).
 */
fun mix(a: Oklch, b: Oklch, t: Float): Oklch {
    var dh = b.h - a.h
    if (dh > 180f) dh -= 360f
    if (dh < -180f) dh += 360f
    return Oklch(
        l = a.l + (b.l - a.l) * t,
        c = a.c + (b.c - a.c) * t,
        h = a.h + dh * t,
    )
}
