package nl.lector.reader

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Hosts Readium's `EpubNavigatorFragment` inside Compose.
 *
 * The reading surface stops being Compose here and becomes a WebView driven by
 * Readium CSS. TECHNICALPRD §1 anticipated exactly this: the EPUB engine ships as
 * parallel native toolkits, so the reader is per-platform while the state, tokens
 * and everything around it stay shared.
 */
@Composable
fun ReadiumSurface(
    publication: Publication,
    preferences: org.readium.r2.navigator.epub.EpubPreferences,
    initialLocator: Locator?,
    onLocatorChanged: (Locator) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as FragmentActivity
    val containerId = remember { View.generateViewId() }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context -> FragmentContainerView(context).apply { id = containerId } },
        update = {
            // Commit once, on the first pass where the container is actually attached.
            if (activity.supportFragmentManager.findFragmentById(containerId) != null) return@AndroidView

            val factory = EpubNavigatorFactory(publication).createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = preferences,
                listener = null,
                configuration = lectorNavigatorConfiguration(),
            )
            activity.supportFragmentManager.fragmentFactory = factory

            val fragment = factory.instantiate(
                activity.classLoader,
                EpubNavigatorFragment::class.java.name,
            ) as EpubNavigatorFragment

            activity.supportFragmentManager.commitNow {
                replace(containerId, fragment, "readium-$containerId")
            }
            navigator = fragment
        },
    )

    // Live restyling: every appearance change is one submitPreferences call.
    LaunchedEffect(navigator, preferences) {
        navigator?.submitPreferences(preferences)
    }

    LaunchedEffect(navigator) {
        val current = navigator ?: return@LaunchedEffect
        current.currentLocator.collect { onLocatorChanged(it) }
    }
}

/** Page turns, so the spike can be driven with the same gestures as the reader. */
suspend fun EpubNavigatorFragment.turn(forward: Boolean) {
    if (forward) goForward(animated = true) else goBackward(animated = true)
}
