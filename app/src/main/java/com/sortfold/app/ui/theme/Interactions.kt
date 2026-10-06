package com.sortfold.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
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
 *
 * 1.2.0 (C-01): the scale is now driven by animateFloatAsState with
 * Motion.pressSpring() — it used to snap because a plain float never
 * animated. Reduced motion keeps the scale at exactly 1f; haptics are not
 * motion and stay available.
 */

/** Press spring scale: 0.97 while pressed, springs back on release. */
fun Modifier.pressable(interactionSource: MutableInteractionSource): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (reduced || !pressed) 1f else 0.97f,
        animationSpec = if (reduced) Motion.reduced() else Motion.pressSpring(),
        label = "press-scale",
    )
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
