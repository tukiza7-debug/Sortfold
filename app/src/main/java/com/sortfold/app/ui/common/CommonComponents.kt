package com.sortfold.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sortfold.app.ui.theme.LocalReducedMotion

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
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            // Folder tab
            drawRoundRect(
                color = outline,
                topLeft = Offset(w * 0.08f, h * 0.16f),
                size = Size(w * 0.30f, h * 0.14f),
                cornerRadius = CornerRadius(w * 0.05f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
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

/** Standard empty state: icon slot-free, one line of explanation. */
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

/** Standard error state used by every screen that can fail. */
@Composable
fun ErrorState(title: String, description: String, modifier: Modifier = Modifier, retry: (() -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (retry != null) {
            Row(horizontalArrangement = Arrangement.Center) {
                androidx.compose.material3.TextButton(onClick = retry) { Text(androidx.compose.ui.res.stringResource(com.sortfold.app.R.string.action_retry)) }
            }
        }
    }
}
