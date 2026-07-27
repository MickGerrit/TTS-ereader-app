package nl.lector.data

import android.content.Context
import java.io.File

/** The cover cache, which is the only thing Lector puts on this device. */
class AndroidStorage(private val context: Context) : Storage {

    private val covers: File get() = File(context.cacheDir, "covers")

    override fun usedBytes(): Long =
        covers.listFiles()?.sumOf { it.length() } ?: 0L

    override fun clear(): Long {
        val before = usedBytes()
        covers.listFiles()?.forEach { runCatching { it.delete() } }
        return before - usedBytes()
    }
}
