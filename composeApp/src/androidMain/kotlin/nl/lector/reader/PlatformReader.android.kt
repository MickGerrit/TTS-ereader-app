package nl.lector.reader

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.lector.data.SpokenSegment
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import androidx.compose.ui.graphics.toArgb
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.screen.ReaderScreen
import nl.lector.state.LectorState
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Locator

@Composable
actual fun PlatformReader(
    state: LectorState,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onAppearance: () -> Unit,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
) {
    val book = state.book
    val opened = book?.locator?.let { rememberPublication(it) }

    ReaderScreen(
        state = state,
        onBack = onBack,
        onContents = onContents,
        onAppearance = onAppearance,
        onTogglePlay = onTogglePlay,
        onExpand = onExpand,
    ) {
        when (opened) {
            null -> Message("No book open.")
            is OpenResult.Loading -> Message("Opening…")
            is OpenResult.Failed -> Message(opened.reason)
            is OpenResult.Opened -> ReadiumPage(state, opened.publication)
        }
    }
}

@Composable
private fun ReadiumPage(state: LectorState, publication: org.readium.r2.shared.publication.Publication) {
    val activity = LocalContext.current as FragmentActivity
    val containerId = remember { View.generateViewId() }
    val scope = rememberCoroutineScope()
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    val chromeAccent = LocalChrome.current.accent

    // Where to open: the position we already have for this book, so a reopened book
    // lands where the reader left it rather than at the cover.
    val startLocator = remember(publication, state.bookId) {
        publication.locatorFromProgression(state.pct)
    }

    AndroidView(
        // The chrome floats over the page, so the page reserves a band for it at top
        // and bottom — constant whether the chrome is showing or not, exactly as the
        // prototype's fixed 76px does. Text sliding under the title bar when the
        // chrome appears would be worse than a slightly shorter page.
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 56.dp,
            ),
        factory = { context -> FragmentContainerView(context).apply { id = containerId } },
        update = {
            if (activity.supportFragmentManager.findFragmentById(containerId) != null) return@AndroidView

            val factory = EpubNavigatorFactory(publication).createFragmentFactory(
                initialLocator = startLocator,
                initialPreferences = lectorPreferences(state),
                listener = null,
                configuration = lectorNavigatorConfiguration(),
            )
            activity.supportFragmentManager.fragmentFactory = factory
            val fragment = factory.instantiate(
                activity.classLoader,
                EpubNavigatorFragment::class.java.name,
            ) as EpubNavigatorFragment

            activity.supportFragmentManager.commitNow {
                replace(containerId, fragment, "reader-$containerId")
            }
            navigator = fragment
        },
    )

    // Live restyling: every appearance change is one call.
    LaunchedEffect(navigator, state.theme, state.warmth, state.font, state.size, state.bold, state.lead, state.margin) {
        navigator?.submitPreferences(lectorPreferences(state))
    }

    /**
     * The tap contract from HANDOFF §5, implemented against Readium's input stream
     * because the WebView owns touch inside the page. Returning true consumes the
     * tap, which is what stops Readium's own edge-tap page turns from firing as well
     * as ours.
     */
    DisposableEffect(navigator) {
        val current = navigator ?: return@DisposableEffect onDispose { }
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val width = current.view?.width ?: return false
                val third = width / 100f
                when {
                    event.point.x < third * 28 -> current.goBackward(animated = true)
                    event.point.x > third * 72 -> current.goForward(animated = true)
                    else -> state.chromeHidden = !state.chromeHidden
                }
                return true
            }
        }
        current.addInputListener(listener)
        onDispose { current.removeInputListener(listener) }
    }

    // Progress comes from the engine's own position now, not a page counter we keep.
    LaunchedEffect(navigator) {
        val current = navigator ?: return@LaunchedEffect
        current.currentLocator.collect { locator ->
            locator.locations.totalProgression?.let { progression ->
                val id = state.book?.id ?: return@collect
                state.progress[id] = progression.toFloat()
                state.readerLocator = locator.toJSON().toString()
                state.save()
            }
        }
    }

    /**
     * Hand the book's text to the speech engine.
     *
     * Readium splits the content into segments that each carry their own locator, so
     * the highlight below decorates a real position in the document rather than a
     * range we guessed at.
     */
    LaunchedEffect(publication) {
        state.spokenSegments.clear()
        state.spokenIndex = -1
        val segments = withContext(Dispatchers.IO) {
            runCatching {
                publication.content()?.elements().orEmpty()
                    .filterIsInstance<Content.TextElement>()
                    .flatMap { element ->
                        element.segments.mapNotNull { segment ->
                            segment.text.trim().takeIf { it.isNotBlank() }?.let {
                                SpokenSegment(it, segment.locator.toJSON().toString())
                            }
                        }
                    }
            }.getOrDefault(emptyList())
        }
        state.spokenSegments += segments
    }

    // The spoken segment is highlighted in the page itself, through the engine's
    // decoration layer — the WebView equivalent of the prototype's sentence span.
    LaunchedEffect(navigator, state.spokenIndex) {
        val current = navigator ?: return@LaunchedEffect
        val segment = state.spokenSegments.getOrNull(state.spokenIndex)
        val spokenLocator = segment?.let { Locator.fromJSON(JSONObject(it.locatorJson)) }

        // Follow the voice: the page advances as it reads, so listening with the book
        // open keeps the words on screen (PRD §6.6). Only while actually speaking —
        // otherwise it would drag the page back every time you turned it.
        if (state.ttsOn && spokenLocator != null) {
            current.go(spokenLocator, animated = true)
        }

        val decorations = spokenLocator
            ?.let {
                listOf(
                    Decoration(
                        id = "spoken",
                        locator = it,
                        style = Decoration.Style.Highlight(tint = chromeAccent.toArgb()),
                    ),
                )
            }
            .orEmpty()
        runCatching { current.applyDecorations(decorations, "lector-tts") }
    }

    // The transport on the Listening screen drives the same navigator.
    DisposableEffect(navigator, publication) {
        val current = navigator
        state.readerController = current?.let {
            object : ReaderController {
                override fun goForward() { it.goForward(animated = true) }
                override fun goBackward() { it.goBackward(animated = true) }
                override fun goTo(progression: Float) {
                    publication.locatorFromProgression(progression)?.let { target ->
                        scope.launch { it.go(target, animated = false) }
                    }
                }
            }
        }
        onDispose { state.readerController = null }
    }
}

/** A locator for a fraction of the whole book, using the reading order's positions. */
private fun org.readium.r2.shared.publication.Publication.locatorFromProgression(
    progression: Float,
): Locator? {
    if (progression <= 0f) return readingOrder.firstOrNull()?.let { locatorFromLink(it) }
    val index = ((readingOrder.size) * progression).toInt().coerceIn(0, readingOrder.lastIndex)
    return readingOrder.getOrNull(index)?.let { locatorFromLink(it) }
}

@Composable
private fun Message(text: String) {
    Box(
        Modifier.fillMaxSize().background(LocalChrome.current.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(32.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = LocalFonts.current.body, fontSize = 14.sp,
                color = LocalChrome.current.muted,
            ),
        )
    }
}
