package nl.lector.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lector.design.Eyebrow
import nl.lector.design.IconBtn
import nl.lector.design.LectorIcons
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.M3Slider
import nl.lector.design.Mono
import nl.lector.design.ReadingFont
import nl.lector.design.ReadingTheme
import nl.lector.design.Segmented
import nl.lector.state.LectorState
import org.readium.r2.shared.publication.Locator

@Composable
actual fun ReaderEngineSpike(state: LectorState, onBack: () -> Unit) {
    val c = LocalChrome.current
    val book = state.book
    var locator by remember { mutableStateOf<Locator?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(LectorIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    book?.title ?: "No book",
                    style = TextStyle(
                        fontFamily = LocalFonts.current.display, fontSize = 17.sp, color = c.fg,
                    ),
                )
                Mono(
                    locator?.locations?.totalProgression
                        ?.let { "${(it * 100).toInt()}% · Readium locator" }
                        ?: "opening…",
                    size = 10.sp,
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (val result = book?.locator?.let { rememberPublication(it) }) {
                null -> Centered("No book open.")
                is OpenResult.Loading -> Centered("Opening with Readium…")
                is OpenResult.Failed -> Centered(result.reason)
                is OpenResult.Opened -> ReadiumSurface(
                    publication = result.publication,
                    preferences = lectorPreferences(state),
                    initialLocator = locator,
                    onLocatorChanged = { locator = it },
                )
            }
        }

        // The spike's controls: change a token, watch the WebView restyle live.
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(16.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Theme")
            Segmented(
                options = ReadingTheme.entries,
                selected = state.theme,
                label = { it.label },
                onSelect = { state.theme = it; state.save() },
            )
            Eyebrow("Typeface")
            Segmented(
                options = ReadingFont.entries,
                selected = state.font,
                label = { it.label },
                onSelect = { state.font = it; state.save() },
            )
            Eyebrow("Warmth · ${state.warmth}%")
            M3Slider(
                value = state.warmth / 100f,
                onValue = { state.warmth = (it * 100).toInt(); state.save() },
            )
        }
    }
}

@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = TextStyle(
                fontFamily = LocalFonts.current.body, fontSize = 14.sp,
                color = LocalChrome.current.muted,
            ),
        )
    }
}
