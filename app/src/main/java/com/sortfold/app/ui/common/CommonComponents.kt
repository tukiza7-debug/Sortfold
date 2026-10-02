package com.sortfold.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sortfold.app.ui.theme.LocalReducedMotion
import com.sortfold.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * The Sortfold loader: three bars inside a folder outline that shift into
 * sorted (left-aligned) order. Indeterminate while scanning; the app shows
 * real determinate progress everywhere the total is known.
 * Honors the reduced-motion setting: renders the settled, static state.
 */
@Composable
fun SortedBarsLoader(
    modifier: Modifier = Modifier,
    label: String,
) {
    val reduced = LocalReducedMotion.current
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    val transition = rememberInfiniteTransition(label = "sortfold-loader")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shift",
    )
    val phase = if (reduced) 1f else shift

    Column(
        modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(72.dp)) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.045f
            val barH = h * 0.085f
            // Folder outline
            drawRoundRect(
                color = outline,
                topLeft = Offset(w * 0.08f, h * 0.24f),
                size = Size(w * 0.84f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.09f),
                style = Stroke(width = stroke),
            )
            // Folder tab
            drawRoundRect(
                color = outline,
                topLeft = Offset(w * 0.08f, h * 0.16f),
                size = Size(w * 0.30f, h * 0.14f),
                cornerRadius = CornerRadius(w * 0.05f),
                style = Stroke(width = stroke),
            )
            // Three bars shifting from scattered x-offsets into left alignment
            val barWidths = listOf(0.42f, 0.26f, 0.34f)
            val startOffsets = listOf(0.14f, 0.32f, 0.06f)
            val ys = listOf(0.42f, 0.56f, 0.70f)
            for (i in barWidths.indices) {
                val x0 = w * (0.20f + startOffsets[i] * (1f - phase))
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x0, h * ys[i]),
                    size = Size(w * barWidths[i], barH),
                    cornerRadius = CornerRadius(barH / 2f),
                )
            }
        }
    }
}

/**
 * Shows [content] only when [busy] has lasted longer than [Motion.LOADER_DELAY_MS],
 * then keeps it on screen at least [Motion.LOADER_MIN_VISIBLE_MS] so it never
 * flashes. The only allowed infinite animation lives inside the loader.
 */
@Composable
fun DelayedLoader(busy: Boolean, label: String, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    var shownAt by remember { mutableStateOf(Long.MAX_VALUE) }

    LaunchedEffect(busy) {
        if (busy) {
            delay(Motion.LOADER_DELAY_MS)
            show = true
            shownAt = System.currentTimeMillis()
        } else if (show) {
            val elapsed = System.currentTimeMillis() - shownAt
            if (elapsed < Motion.LOADER_MIN_VISIBLE_MS) {
                delay(Motion.LOADER_MIN_VISIBLE_MS - elapsed)
            }
            show = false
        }
    }

    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(1)) else fadeIn(Motion.small()),
        exit = if (reduced) fadeOut(tween(1)) else fadeOut(Motion.small()),
    ) {
        SortedBarsLoader(label = label)
    }
}

/** Standard empty state: logo-derived mark, one line of explanation. */
@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortedBarsLoader(label = title)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Standard error state used by every screen that can fail. Inline, in plain
 * language, with at least one next action. Announced to TalkBack via a live
 * region so a failing step is never silent.
 */
@Composable
fun ErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actions != null) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

/**
 * Skeleton shimmer used instead of text spinners while Home, Wizard and
 * History load (1.1.0 Part B2). Renders a static tint when reduced motion
 * is on — the shimmer is the only thing removed.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val reduced = LocalReducedMotion.current
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )
    Surface(
        color = if (reduced) base else lerp(base, highlight, alpha),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().height(height),
    ) {}
}

/** List entry helper: spring + alpha with a capped stagger and reduced path. */
@Composable
fun StaggeredEntry(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    var appeared by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) {
        if (!appeared) {
            delay(Motion.staggerDelayMs(index).toLong())
            appeared = true
        }
    }
    AnimatedVisibility(
        visible = appeared,
        modifier = modifier,
        enter = if (reduced) {
            fadeIn(tween(1))
        } else {
            fadeIn(Motion.small()) + slideInVertically(Motion.entrySpring()) { it / 8 }
        },
    ) {
        content()
    }
}
