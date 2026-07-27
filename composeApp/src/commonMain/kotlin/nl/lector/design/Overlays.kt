package nl.lector.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lector.state.Snack
import kotlin.math.roundToInt

/** Past this speed, a flick dismisses regardless of how far the sheet was dragged. */
private const val FlingDismissVelocity = 900f

/** Below halfway the sheet springs back; past it, a slow drag still dismisses. */
private const val DragDismissFraction = 0.5f

/**
 * Material bottom sheet that tracks the finger.
 *
 * The handle follows the drag one-to-one, and only on release does velocity decide:
 * a flick dismisses at any distance, a slow drag dismisses past halfway, anything
 * else springs back. The Listening screen's swipe-up/drag-down pair is the primary
 * way into and out of Playback (HANDOFF §5), so this has to feel like the sheet is
 * attached to the finger rather than replaying a canned animation.
 */
@Composable
fun BoxScope.BottomSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalChrome.current
    val scope = rememberCoroutineScope()
    val scrimInteraction = remember { MutableInteractionSource() }

    // Tall enough to start fully offscreen before the sheet has been measured.
    val offscreen = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val offset = remember { Animatable(offscreen) }
    var sheetHeight by remember { mutableFloatStateOf(offscreen) }
    var present by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            present = true
            offset.snapTo(sheetHeight)
            offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
        } else if (present) {
            offset.animateTo(sheetHeight, tween(220))
            present = false
        }
    }

    if (!present) return

    /** Ride the offset out to the edge, then tell the caller it closed. */
    fun dismiss() {
        scope.launch {
            offset.animateTo(sheetHeight, tween(200))
            onDismiss()
        }
    }

    // Scrim fades with the drag, so dragging half-way looks half-dismissed.
    val progress = (1f - offset.value / sheetHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.36f * progress))
            .clickable(interactionSource = scrimInteraction, indication = null) { dismiss() },
    )

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp)
            .offset { IntOffset(0, offset.value.roundToInt()) }
            .onSizeChanged { if (it.height > 0) sheetHeight = it.height.toFloat() }
            .clip(RoundedCornerShape(topStart = Shape.xl, topEnd = Shape.xl))
            .background(c.surface),
    ) {
        Column(
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    // 1:1 with the finger, and never above its resting position.
                    scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    val flicked = velocity > FlingDismissVelocity
                    val draggedPast = offset.value > sheetHeight * DragDismissFraction
                    if (flicked || draggedPast) {
                        dismiss()
                    } else {
                        offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 420f))
                    }
                },
            ),
        ) {
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp, 4.dp)
                    .clip(CircleShape)
                    .background(c.border),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title,
                    style = TextStyle(
                        fontFamily = LocalFonts.current.display, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, color = c.fg,
                    ),
                )
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { dismiss() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Done",
                        style = TextStyle(
                            fontFamily = LocalFonts.current.body, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = c.accent,
                        ),
                    )
                }
            }
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(
                    bottom = 30.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            content = content,
        )
    }
}

/**
 * Material snackbar: left-aligned message with a trailing action, sitting above the
 * navigation bar. Android's answer to the iOS build's centred toast.
 */
@Composable
fun BoxScope.Snackbar(
    snack: Snack?,
    onAction: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val c = LocalChrome.current
    AnimatedVisibility(
        visible = snack != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 },
        exit = fadeOut(tween(200)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomPadding)
                .clip(RoundedCornerShape(Shape.xs))
                .background(c.inverseSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                rich(snack?.message.orEmpty(), monoColor = blend(c.accent, c.inverseFg, 0.22f)),
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 13.sp,
                    lineHeight = 18.2.sp, color = c.inverseFg,
                ),
            )
            Text(
                snack?.action.orEmpty().uppercase(),
                modifier = Modifier
                    .clip(RoundedCornerShape(Shape.xs))
                    .clickable(onClick = onAction)
                    .padding(6.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.39.sp,
                    color = blend(c.accent, c.inverseFg, 0.28f),
                ),
            )
        }
    }
}

/** The snackbar sits on the inverse surface, so the accent is pulled toward the
 *  inverse foreground to stay readable there. */
private fun blend(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)
