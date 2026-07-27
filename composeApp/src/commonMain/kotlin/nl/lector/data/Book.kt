package nl.lector.data

import nl.lector.design.CoverArt
import nl.lector.design.Oklch

/**
 * A book found in the reader's folder.
 *
 * Everything here comes from the file itself or from its sidecar. Nothing is
 * authored in the app, which is the point: the library is whatever is in the
 * folder, not a list we ship.
 */
data class Book(
    /** Stable across scans: derived from the path relative to the granted folder. */
    val id: String,
    val title: String,
    val author: String,
    /** BCP-47 as declared by the EPUB, uppercased for display. Blank if absent. */
    val language: String,
    /** Estimated until the renderer paginates for real (TECHNICALPRD §12, Spike B). */
    val pages: Int,
    /** True when the EPUB carries its own cover, so no lookup is needed. */
    val hasEmbeddedCover: Boolean,
    /** `percent_finished` as read from the KOReader sidecar at scan time. */
    val sidecarProgress: Float = 0f,
    /** Opaque handle the platform uses to reopen the file. */
    val locator: String = "",
) {
    /**
     * The generated placeholder's palette (PRD §6.7). Derived from the title, so a
     * book looks the same on every device and every rescan without storing anything.
     */
    val coverBackground: Oklch get() = palette(title).first
    val coverForeground: Oklch get() = palette(title).second
    val coverArt: CoverArt get() = CoverArt.entries[stableHash(title).mod(CoverArt.entries.size)]
}

/**
 * FNV-1a plus an avalanche pass. Deterministic across platforms, unlike
 * [String.hashCode]'s guarantees.
 *
 * The finalizer is not decoration. FNV's low bits carry visible structure, and the
 * cover hue is `hash.mod(360)` — which reads only those bits. Without this, six
 * real book titles all came out within one 70° band of teal, and a shelf where every
 * generated cover is the same colour looks broken rather than generated.
 */
internal fun stableHash(s: String): Int {
    var h = -2128831035
    s.forEach { h = (h xor it.code) * 16777619 }
    // MurmurHash3 finalizer: spreads entropy from the high bits down into the low.
    h = h xor (h ushr 16)
    h *= -2048144789
    h = h xor (h ushr 13)
    h *= -1028477387
    return h xor (h ushr 16)
}

/**
 * Deep, low-chroma ground with a near-white foreground — the same family the
 * prototype's covers were hand-picked from, so a generated cover still sits in the
 * shelf rather than shouting from it.
 */
private fun palette(title: String): Pair<Oklch, Oklch> {
    val h = stableHash(title)
    val hue = h.mod(360).toFloat()
    val lightness = 0.26f + (h.ushr(9).mod(20)) / 100f   // 0.26 .. 0.45
    val chroma = 0.03f + (h.ushr(17).mod(5)) / 100f      // 0.03 .. 0.07
    return Oklch(lightness, chroma, hue) to Oklch(0.95f, 0.012f, hue)
}
