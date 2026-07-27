package nl.lector.data

/**
 * What the app is using on disk, and getting rid of it.
 *
 * Only the cache: books live in the reader's own folder and are never copied, and
 * preferences are a few hundred bytes. So "storage" here is exactly the cover cache,
 * which is disposable by construction — the next scan extracts it again.
 */
interface Storage {
    fun usedBytes(): Long

    /** @return how many bytes went away. */
    fun clear(): Long
}

/** For previews and tests: nothing on disk, nothing to clear. */
class NoStorage : Storage {
    override fun usedBytes() = 0L
    override fun clear() = 0L
}

/** "12.4 MB". One decimal, no formatter dependency. */
fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "empty"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${tenths(bytes, 1024)} kB"
    else -> "${tenths(bytes, 1024 * 1024)} MB"
}

private fun tenths(bytes: Long, unit: Int): String {
    val scaled = (bytes * 10 / unit)
    return "${scaled / 10}.${scaled % 10}"
}
