package nl.lector.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import nl.lector.data.Book
import nl.lector.data.Chapter
import nl.lector.data.SpokenSegment
import nl.lector.data.indexAt
import nl.lector.data.BundledVoices
import nl.lector.data.FolderGrant
import nl.lector.data.Voice
import nl.lector.data.VoiceCatalogue
import nl.lector.design.Appearance
import nl.lector.design.ReadingFont
import nl.lector.design.ReadingTheme

enum class Screen { Import, Library, Reader, Voices, Listen, Settings }
enum class Sheet { Appearance, Contents, Playback }

/** How the shelf is ordered. Persisted, because a reader has one habit, not three. */
enum class Sort(val label: String) {
    Recent("Recently read"), Title("By title"), Author("By author")
}

/**
 * A snackbar message. `*bold*` and `` `mono` `` are rendered as such.
 *
 * [onAction] makes it a prompt rather than a notice: the button does something and
 * ignoring it is a real answer. That is enough for the resume offer (PRD §6.8),
 * which HANDOFF §7 lists as specified but undesigned — a dialog would be inventing
 * a component, and this one already exists and already reads as "you may act".
 */
data class Snack(
    val message: String,
    val action: String = "Got it",
    val durationMs: Long = 3400,
    val onAction: (() -> Unit)? = null,
)

/**
 * All app state in one observable object.
 *
 * Small enough that splitting it into per-screen view models would add indirection
 * without removing anything: the reader, the listening screen and the library are
 * three views of one position in one book, and the bugs worth avoiding come from
 * them disagreeing.
 */
class LectorState(private val prefs: Prefs) {

    // ── navigation ────────────────────────────────────────────────────────
    var screen by mutableStateOf(Screen.Import)
    var sheet by mutableStateOf<Sheet?>(null)

    /** Where closing a sheet should return to, when it was opened from elsewhere. */
    var sheetReturn: Screen? = null

    var snack by mutableStateOf<Snack?>(null)

    // ── library ───────────────────────────────────────────────────────────

    /** The folder the reader granted. Null until onboarding completes. */
    var folder by mutableStateOf<FolderGrant?>(null)

    /** Whatever the last scan found. Rebuilt on every scan, never authored. */
    val books: SnapshotStateList<Book> = mutableStateListOf()

    var bookId by mutableStateOf<String?>(null); private set

    /** Null only when the granted folder holds no readable EPUBs. */
    val book: Book? get() = books.firstOrNull { it.id == bookId } ?: books.firstOrNull()

    // ── persisted preferences ─────────────────────────────────────────────
    var onboarded by mutableStateOf(false); private set
    var word by mutableStateOf(-1)

    var font by mutableStateOf(ReadingFont.Serif)
    var size by mutableStateOf(18)
    var bold by mutableStateOf(false)
    var lead by mutableStateOf(1.62f)
    var margin by mutableStateOf(24)
    var theme by mutableStateOf(ReadingTheme.Paper)

    /** 0..100, as the slider reports it. */
    var warmth by mutableStateOf(0)
    var rate by mutableStateOf(1.0f)

    /** Speaking pitch, 1.0 being the voice's own (PRD §6.6). */
    var pitch by mutableStateOf(1.0f)

    /** Scrolling instead of paginated, per PRD §13.3. Page turns stay the default. */
    var scrolling by mutableStateOf(false)

    /** Minutes the sleep timer arms itself at when listening starts. 0 is off. */
    var sleepDefault by mutableStateOf(0)

    var covers by mutableStateOf(false)
    var appearance by mutableStateOf(Appearance.System)

    var lastWrite by mutableStateOf<String?>(null)
    var lastScan by mutableStateOf("never")
    var sort by mutableStateOf(Sort.Recent)

    /**
     * Open order, newest highest. A counter rather than a clock: ordering is all it
     * is ever asked for, and a counter needs no time source to persist or compare.
     */
    val opened: SnapshotStateMap<String, Int> = mutableStateMapOf()

    /** Reading position per book id, ahead of whatever the sidecar last said. */
    val progress: SnapshotStateMap<String, Float> = mutableStateMapOf()

    /** Book ids whose cover came back from Open Library. */
    val fetched: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    /** Voice ids downloaded on top of the bundled pair. */
    val added: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    // ── transient ─────────────────────────────────────────────────────────
    var ttsOn by mutableStateOf(false)

    /**
     * Bumped whenever the speaking position moves under the engine's feet — a
     * sentence seek, a page turn. The playback loop keys on it and resumes from the
     * new position instead of finishing the line it was already on.
     */
    var ttsEpoch by mutableStateOf(0)

    var chromeHidden by mutableStateOf(false)
    var scanning by mutableStateOf(false)

    /** An app-open scan is quiet; a pull-to-scan reports what it found. */
    var scanIsAuto by mutableStateOf(false)

    var fetchingCover by mutableStateOf<String?>(null)

    /**
     * The open book's text, in speaking order, with each fragment's position in the
     * publication. Filled by the reader once the engine has parsed the file; empty
     * until then, which is why playback waits rather than reading a fixture.
     */
    val spokenSegments: SnapshotStateList<SpokenSegment> = mutableStateListOf()

    /** Which segment is being spoken, or -1. Drives the highlight in the page. */
    var spokenIndex by mutableStateOf(-1)

    /**
     * The open book's own table of contents, read from its navigation document.
     * Empty until the reader has opened the file, and for books that carry none.
     */
    val chapters: SnapshotStateList<Chapter> = mutableStateListOf()

    /** True while a batch cover fetch is running. */
    var fetchingAll by mutableStateOf(false)

    /** Set when leaving a book, so the sidecar write happens in a coroutine. */
    var writeSidecarOnClose by mutableStateOf<Book?>(null)

    /** Library search. Transient — a filter, not a preference. */
    var query by mutableStateOf("")

    /** Why the last scan could not read the folder, if it could not. */
    var scanError by mutableStateOf<String?>(null)

    /** The live reader, while one is on screen. Drives the transport's page turns. */
    var readerController: nl.lector.reader.ReaderController? = null

    /** Readium's own position for the open book, serialised. Written to the sidecar. */
    var readerLocator by mutableStateOf<String?>(null)

    /** Minutes left on the sleep timer, or null when it is off. */
    var sleepMinutesLeft by mutableStateOf<Int?>(null)

    /** True while a listening session exists, even if paused. */
    val listening: Boolean get() = ttsOn || word >= 0

    // ── derived ───────────────────────────────────────────────────────────

    fun pctOf(id: String): Float =
        progress[id] ?: books.firstOrNull { it.id == id }?.sidecarProgress ?: 0f

    val pct: Float get() = book?.let { pctOf(it.id) } ?: 0f

    /** Which entry of the book's own contents the position falls in, or -1. */
    val chapterIndex: Int get() = chapters.indexAt(pct)

    /** The chapter being read, or null for a book with no navigation document. */
    val chapter: Chapter? get() = chapters.getOrNull(chapterIndex)

    fun installedVoices(): List<Voice> =
        BundledVoices + VoiceCatalogue.filter { added[it.id] == true }

    fun voicesSummary(): String {
        val v = installedVoices()
        return if (v.size <= 2) {
            v.joinToString(" and ") { it.name.substringBefore(" —") } + " installed"
        } else {
            "${v.size} voices installed"
        }
    }

    /** Books still showing a generated placeholder, which is what a fetch is for. */
    fun booksWithoutCover(): List<Book> = books.filter { it.coverImagePath == null }

    /** The shelf in the reader's chosen order. */
    fun sortedBooks(source: List<Book> = books): List<Book> = when (sort) {
        // Unopened books have no place in a recency order, so they follow the ones
        // that do, in title order rather than in whatever order the walk found them.
        Sort.Recent -> source.sortedWith(
            compareByDescending<Book> { opened[it.id] ?: 0 }.thenBy { it.title.lowercase() },
        )

        Sort.Title -> source.sortedBy { it.title.lowercase() }
        Sort.Author -> source.sortedWith(
            compareBy<Book> { it.author.lowercase() }.thenBy { it.title.lowercase() },
        )
    }

    /** Title-or-author match, case- and accent-insensitively enough for a shelf. */
    fun matchingBooks(): List<Book> {
        val q = query.trim()
        if (q.isBlank()) return sortedBooks()
        return sortedBooks(
            books.filter {
                it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true)
            },
        )
    }

    /**
     * How much of the book one page turn covers.
     *
     * Derived from the book's own page count rather than a fixed step, so progress
     * advances at the right rate for a 90-page book and a 900-page one alike.
     */
    val pageStep: Float get() = 1f / (book?.pages ?: 1).coerceAtLeast(1)

    // ── mutations ─────────────────────────────────────────────────────────

    fun openBook(id: String) {
        opened[id] = (opened.values.maxOrNull() ?: 0) + 1
        if (id != bookId) {
            // A different book means a different position; do not carry the old one.
            word = -1
            readerLocator = null
            chapters.clear()
        }
        bookId = id
        save()
    }

    fun finishOnboarding() {
        onboarded = true
        save()
    }

    fun bumpProgress(delta: Float) {
        val id = book?.id ?: return
        progress[id] = (pctOf(id) + delta).coerceIn(0f, 1f)
        save()
    }

    fun show(
        message: String,
        action: String = "Got it",
        durationMs: Long = 3400,
        onAction: (() -> Unit)? = null,
    ) {
        snack = Snack(message, action, durationMs, onAction)
    }

    /**
     * Another device is further along than this one (story 6.1).
     *
     * ponytail: read at scan time, and the app scans on every launch, so it is fresh
     * where it matters. A folder that syncs while the app is already open will not be
     * noticed until the next scan; re-read the one sidecar on open if that bites.
     */
    fun sidecarAhead(book: Book?): Boolean {
        val b = book ?: return false
        // Half a percent, so rounding between two engines is not a disagreement.
        return b.sidecarProgress > pctOf(b.id) + 0.005f
    }

    // ── persistence ───────────────────────────────────────────────────────

    fun save() {
        prefs.put("onboarded", onboarded.toString())
        prefs.put("folderLocator", folder?.locator.orEmpty())
        prefs.put("folderLabel", folder?.label.orEmpty())
        prefs.put("book", bookId.orEmpty())
        prefs.put("word", word.toString())
        prefs.put("font", font.name)
        prefs.put("size", size.toString())
        prefs.put("bold", bold.toString())
        prefs.put("lead", lead.toString())
        prefs.put("margin", margin.toString())
        prefs.put("theme", theme.name)
        prefs.put("warmth", warmth.toString())
        prefs.put("rate", rate.toString())
        prefs.put("pitch", pitch.toString())
        prefs.put("scrolling", scrolling.toString())
        prefs.put("sleepDefault", sleepDefault.toString())
        prefs.put("covers", covers.toString())
        prefs.put("appearance", appearance.name)
        prefs.put("lastWrite", lastWrite.orEmpty())
        prefs.put("lastScan", lastScan)
        prefs.put("sort", sort.name)
        prefs.put("locator", readerLocator.orEmpty())
        prefs.put("opened", opened.entries.joinToString(",") { "${it.key}=${it.value}" })
        prefs.put("progress", progress.entries.joinToString(",") { "${it.key}=${it.value}" })
        prefs.put("fetched", fetched.filterValues { it }.keys.joinToString(","))
        prefs.put("added", added.filterValues { it }.keys.joinToString(","))
    }

    fun load() {
        prefs.get("onboarded")?.let { onboarded = it.toBoolean() }
        val locator = prefs.get("folderLocator").orEmpty()
        if (locator.isNotEmpty()) {
            folder = FolderGrant(locator, prefs.get("folderLabel").orEmpty())
        }
        prefs.get("book")?.takeIf { it.isNotEmpty() }?.let { bookId = it }
        prefs.get("word")?.toIntOrNull()?.let { word = it }
        prefs.get("font")?.let { n -> ReadingFont.entries.firstOrNull { it.name == n }?.let { font = it } }
        prefs.get("size")?.toIntOrNull()?.let { size = it }
        prefs.get("bold")?.let { bold = it.toBoolean() }
        prefs.get("lead")?.toFloatOrNull()?.let { lead = it }
        prefs.get("margin")?.toIntOrNull()?.let { margin = it }
        prefs.get("theme")?.let { n -> ReadingTheme.entries.firstOrNull { it.name == n }?.let { theme = it } }
        prefs.get("warmth")?.toIntOrNull()?.let { warmth = it }
        prefs.get("rate")?.toFloatOrNull()?.let { rate = it }
        prefs.get("pitch")?.toFloatOrNull()?.let { pitch = it }
        prefs.get("scrolling")?.let { scrolling = it.toBoolean() }
        prefs.get("sleepDefault")?.toIntOrNull()?.let { sleepDefault = it }
        prefs.get("covers")?.let { covers = it.toBoolean() }
        prefs.get("appearance")?.let { n -> Appearance.entries.firstOrNull { it.name == n }?.let { appearance = it } }
        prefs.get("lastWrite")?.takeIf { it.isNotEmpty() }?.let { lastWrite = it }
        prefs.get("lastScan")?.let { lastScan = it }
        prefs.get("sort")?.let { n -> Sort.entries.firstOrNull { it.name == n }?.let { sort = it } }
        prefs.get("locator")?.takeIf { it.isNotEmpty() }?.let { readerLocator = it }

        prefs.get("opened")?.split(",")?.forEach { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) parts[1].toIntOrNull()?.let { opened[parts[0]] = it }
        }

        prefs.get("progress")?.split(",")?.forEach { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) parts[1].toFloatOrNull()?.let { progress[parts[0]] = it }
        }
        prefs.get("fetched")?.split(",")?.filter { it.isNotEmpty() }?.forEach { fetched[it] = true }
        prefs.get("added")?.split(",")?.filter { it.isNotEmpty() }?.forEach { added[it] = true }

        // A grant can be revoked from system settings between launches, so having
        // onboarded once is not enough — the folder has to still be there.
        screen = if (onboarded && folder != null) Screen.Library else Screen.Import
    }
}
