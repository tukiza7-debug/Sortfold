package com.sortfold.app.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 1.1.0 designer polish (Part B5): one press-scale interaction for every
 * tappable card/surface, and a light haptic helper for toggle/apply actions.
 * The scale is a no-op when reduced motion is on; haptics are not motion and
 * stay available.
 */

/** Press spring scale: 0.97 while pressed, springs back on release. */
fun Modifier.pressable(interactionSource: MutableInteractionSource): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = if (reduced) 1f else if (pressed) 0.97f else 1f
    this.then(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = 1f
        },
    )
}

/** Remembers an interaction source wired for [pressable]. */
@Composable
fun rememberPressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Light tick for toggles, apply and destructive confirms. */
@Composable
fun rememberHaptics(): () -> Unit {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) {
        {
            feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
