package nl.lector.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lector.state.Screen

/** Small top app bar — left-aligned title, no back label. Android, not iOS. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalChrome.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) IconBtn(LectorIcons.Back, "Back", onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            style = TextStyle(
                fontFamily = LocalFonts.current.display, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, color = c.fg,
            ),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Large top app bar — the headline sits under the action row. */
@Composable
fun LargeTopBar(title: String, topRow: @Composable RowScope.() -> Unit = {}) {
    val c = LocalChrome.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = topRow,
        )
        Text(
            title,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            style = TextStyle(
                fontFamily = LocalFonts.current.display, fontSize = 29.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.87).sp, color = c.fg,
            ),
        )
    }
}

/**
 * Material navigation bar with the pill indicator.
 *
 * Three destinations, and only three: Library, Listening, Settings. Reader and
 * Voices are pushed screens, not tabs (HANDOFF §4).
 */
@Composable
fun NavBar(current: Screen, onNavigate: (Screen) -> Unit) {
    val c = LocalChrome.current
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 18.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            NavDest(LectorIcons.Library, "Library", current == Screen.Library) { onNavigate(Screen.Library) }
            NavDest(LectorIcons.Headphones, "Listening", current == Screen.Listen) { onNavigate(Screen.Listen) }
            NavDest(LectorIcons.Settings, "Settings", current == Screen.Settings) { onNavigate(Screen.Settings) }
        }
    }
}

@Composable
private fun RowScope.NavDest(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalChrome.current
    val tint by animateColorAsState(if (selected) c.accent else c.muted, label = "navTint")
    val pill by animateColorAsState(
        if (selected) c.tonal else Color.Transparent, label = "navPill",
    )
    Column(
        Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(60.dp, 32.dp).clip(CircleShape).background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(23.dp), tint = tint)
        }
        Text(
            label,
            style = TextStyle(
                fontFamily = LocalFonts.current.body, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, color = tint,
            ),
        )
    }
}
