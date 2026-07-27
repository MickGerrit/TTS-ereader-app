package nl.lector.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decode a cached cover image off disk.
 *
 * Platform-specific because image decoding is, and returns null for anything that
 * will not decode — a book whose "cover" turns out to be a broken JPEG falls back to
 * the generated placeholder rather than showing a hole.
 */
@Composable
expect fun rememberCoverImage(path: String?): ImageBitmap?
