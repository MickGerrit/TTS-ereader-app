package nl.lector.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Swallow pointer events so an ancestor gesture handler does not also act on them.
 *
 * The reader listens for taps and swipes across the whole surface, and its chrome
 * floats on top of that surface. Without this, a tap on the back button or the
 * scrubber also lands as a page turn. Children still receive events first, so the
 * buttons inside keep working — this only stops the event continuing outward.
 */
fun Modifier.consumePointer(enabled: Boolean = true): Modifier =
    if (!enabled) this else this.pointerInput(enabled) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }

// ─── text helpers ─────────────────────────────────────────────────────────

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LocalChrome.current.muted) {
    Text(text.uppercase(), modifier = modifier, style = eyebrowStyle(color))
}

@Composable
fun Mono(
    text: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 12.5.sp,
    color: Color = LocalChrome.current.muted,
    maxLines: Int = 1,
) = Text(
    text, modifier = modifier, style = monoStyle(size, color),
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

/** The wordmark: `lec` in ink, `tor` in the accent (HANDOFF §preamble). */
@Composable
fun Wordmark(size: androidx.compose.ui.unit.TextUnit = 23.sp, modifier: Modifier = Modifier) {
    val c = LocalChrome.current
    Row(modifier) {
        Text(
            "lec",
            style = TextStyle(
                fontFamily = LocalFonts.current.display, fontSize = size,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em(size), color = c.fg,
            ),
        )
        Text(
            "tor",
            style = TextStyle(
                fontFamily = LocalFonts.current.display, fontSize = size,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em(size), color = c.accent,
            ),
        )
    }
}

private fun Double.em(base: androidx.compose.ui.unit.TextUnit) = (this * base.value).sp

// ─── buttons ──────────────────────────────────────────────────────────────

/** 48dp circular icon button — the Material touch-target minimum. */
@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalChrome.current.fg,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
    }
}

enum class BtnStyle { Filled, Tonal, Text }

@Composable
fun LectorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Filled,
    enabled: Boolean = true,
) {
    val c = LocalChrome.current
    val bg = when {
        !enabled -> c.fill
        style == BtnStyle.Filled -> c.accent
        style == BtnStyle.Tonal -> c.tonal
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> c.muted
        style == BtnStyle.Filled -> c.onAccent
        else -> c.accent
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (style == BtnStyle.Text) 12.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = LocalFonts.current.body, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.sp, color = fg,
            ),
        )
    }
}

// ─── card + rows ──────────────────────────────────────────────────────────

@Composable
fun CardM3(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalChrome.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Shape.md))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(Shape.md)),
        content = content,
    )
}

/** Divider between consecutive rows, matching `.row + .row`. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalChrome.current.border))
}

/**
 * `.row` — 56dp minimum, label block on the left, trailing content on the right.
 */
@Composable
fun LectorRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val c = LocalChrome.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, color = c.fg,
                ),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = if (subtitleMono) monoStyle(12.5.sp, c.muted)
                    else TextStyle(
                        fontFamily = LocalFonts.current.body, fontSize = 12.5.sp,
                        lineHeight = 17.5.sp, color = c.muted,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Row whose whole surface is a control, ending in a chevron. */
@Composable
fun ChevronRow(
    title: String,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    onClick: () -> Unit,
) = LectorRow(title, subtitle = subtitle, subtitleMono = subtitleMono, onClick = onClick) {
    Icon(
        LectorIcons.Chevron, null,
        Modifier.size(18.dp), tint = LocalChrome.current.muted,
    )
}

// ─── Material switch ──────────────────────────────────────────────────────

/** Track outline when off, filled accent with a grown thumb when on. */
@Composable
fun M3Switch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = LocalChrome.current
    val track by animateColorAsState(if (checked) c.accent else c.fill, label = "track")
    val outline by animateColorAsState(if (checked) c.accent else c.muted, label = "outline")
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, label = "thumbSize")
    val thumbColor by animateColorAsState(if (checked) c.surface else c.muted, label = "thumb")
    // Left edge of the thumb, not its centre: 6dp of track showing on the trailing
    // side either way, so the switch stays symmetric as the thumb grows.
    val offset by animateDpAsState(if (checked) 22.dp else 6.dp, label = "offset")

    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(CircleShape)
            .background(track)
            .border(2.dp, outline, CircleShape),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = offset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) =
    LectorRow(title, subtitle = subtitle, onClick = onToggle) { M3Switch(checked) }

// ─── connected segmented button ───────────────────────────────────────────

@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalChrome.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .border(1.dp, c.muted, CircleShape),
    ) {
        options.forEachIndexed { i, opt ->
            if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(c.muted))
            val on = opt == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (on) c.tonal else Color.Transparent)
                    .clickable { onSelect(opt) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(opt),
                    style = TextStyle(
                        fontFamily = LocalFonts.current.body, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, color = if (on) c.accent else c.fg,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

// ─── Material slider ──────────────────────────────────────────────────────

/**
 * Thick 14dp track with a 5dp pill thumb, per the prototype's `.slider`.
 * [value] and the callback are normalised 0f..1f; callers map to their own range.
 */
@Composable
fun M3Slider(value: Float, onValue: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChrome.current
    var width by remember { mutableStateOf(1f) }
    val v = value.coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { onValue((it.x / width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onValue((change.position.x / width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackH = 14.dp.toPx()
            val y = size.height / 2f
            val r = trackH / 2f
            // remaining track
            drawRoundRect(
                color = c.fill,
                topLeft = Offset(0f, y - r),
                size = Size(size.width, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            )
            // filled portion
            if (v > 0f) {
                drawRoundRect(
                    color = c.accent,
                    topLeft = Offset(0f, y - r),
                    size = Size(size.width * v, trackH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                )
            }
            // thumb: 5dp pill, ringed by 3dp of surface so it reads on the track
            val thumbW = 5.dp.toPx()
            val thumbH = 30.dp.toPx()
            val ring = 3.dp.toPx()
            val cx = (size.width - thumbW) * v + thumbW / 2f
            drawRoundRect(
                color = c.surface,
                topLeft = Offset(cx - thumbW / 2f - ring, y - thumbH / 2f - ring),
                size = Size(thumbW + ring * 2, thumbH + ring * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((thumbW + ring * 2) / 2f),
            )
            drawRoundRect(
                color = c.accent,
                topLeft = Offset(cx - thumbW / 2f, y - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbW / 2f),
            )
        }
    }
}

// ─── pill ─────────────────────────────────────────────────────────────────

@Composable
fun Pill(text: String, on: Boolean = false, dot: Boolean = false) {
    val c = LocalChrome.current
    Row(
        Modifier
            .clip(RoundedCornerShape(Shape.sm))
            .background(if (on) c.tonal else c.fill)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fg = if (on) c.accent else c.muted
        if (dot) Box(Modifier.size(5.dp).clip(CircleShape).background(fg))
        Text(
            text.uppercase(),
            style = TextStyle(
                fontFamily = LocalFonts.current.mono, fontSize = 10.5.sp,
                letterSpacing = 0.53.sp, color = fg,
            ),
        )
    }
}

// ─── progress bar ─────────────────────────────────────────────────────────

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 4.dp,
    trackColor: Color = LocalChrome.current.fg.copy(alpha = 0.10f),
    fillColor: Color = LocalChrome.current.accent,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(CircleShape)
                .background(fillColor),
        )
    }
}

// ─── note text ────────────────────────────────────────────────────────────

/** `.note` — the quiet explanatory paragraph under a control group. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        style = TextStyle(
            fontFamily = LocalFonts.current.body, fontSize = 12.5.sp,
            lineHeight = 18.75.sp, color = LocalChrome.current.muted,
        ),
    )
}
