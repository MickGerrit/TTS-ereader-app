package nl.lector.platform

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

@Composable
actual fun rememberCoverImage(path: String?): ImageBitmap? = remember(path) {
    if (path == null) return@remember null
    val file = File(path)
    if (!file.exists()) return@remember null
    runCatching {
        // Covers render at most ~150dp wide, so full-resolution artwork is wasted
        // memory; sample it down on the way in.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, bounds.outWidth / 600)
        }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }.getOrNull()
}
