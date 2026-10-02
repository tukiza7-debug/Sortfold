package com.sortfold.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

/**
 * Single source of motion truth. Durations in milliseconds, two easings only:
 * emphasized decelerate for things entering, accelerate for things leaving.
 * Exit runs at ~75% of enter so screens never feel like they lag behind input.
 */
object Motion {
    const val PRESS_MS = 100
    const val SMALL_MS = 200
    const val ENTER_MS = 300
    const val EXIT_MS = 225

    /** Emphasized decelerate: fast start, gentle settle. */
    val Enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerate: quick leave, no lingering. */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val LOADER_DELAY_MS = 400L
    const val LOADER_MIN_VISIBLE_MS = 300L
    const val LIST_STAGGER_MS = 30
    const val LIST_STAGGER_MAX_ITEMS = 5

    /** Fades used everywhere when reduced motion is on: near-instant, no motion. */
    fun <T> reduced() = tween<T>(1, easing = LinearEasing)

    fun <T> enter() = tween<T>(ENTER_MS, easing = Enter)

    fun <T> exit() = tween<T>(EXIT_MS, easing = Exit)

    fun <T> small() = tween<T>(SMALL_MS, easing = Enter)

    fun <T> press() = tween<T>(PRESS_MS, easing = LinearEasing)
}
