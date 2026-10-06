package com.sortfold.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Single source of motion truth. Durations in milliseconds, two easings only:
 * emphasized decelerate for things entering, accelerate for things leaving.
 * Exit runs at ~75% of enter so screens never feel like they lag behind input.
 *
 * 1.1.0: spring physics for press states and card entry; every animation in
 * the app is wrapped by [reduced] checks at the call site — when reduced
 * motion is on, nothing moves, it only fades near-instantly.
 *
 * 1.2.0 (Part C): fade-through exit token for top-level destinations and the
 * press spring actually wired into [pressable].
 */
object Motion {
    const val PRESS_MS = 100
    const val SMALL_MS = 200
    const val ENTER_MS = 300
    const val EXIT_MS = 225

    /** C-02 fade-through: the outgoing destination leaves in 90 ms. */
    const val FADE_THROUGH_EXIT_MS = 90

    /** Emphasized decelerate: fast start, gentle settle. */
    val Enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerate: quick leave, no lingering. */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val LOADER_DELAY_MS = 400L
    const val LOADER_MIN_VISIBLE_MS = 300L
    const val LIST_STAGGER_MS = 30

    /**
     * Stagger cap: after this many items everything animates together so a
     * 200-row list does not cascade for seconds.
     */
    const val LIST_STAGGER_MAX_ITEMS = 5

    /** Spring for press feedback: subtle bounce, quick settle. */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Spring for cards entering a list. */
    fun <T> entrySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Fades used everywhere when reduced motion is on: near-instant, no motion. */
    fun <T> reduced() = tween<T>(1, easing = LinearEasing)

    fun <T> enter() = tween<T>(ENTER_MS, easing = Enter)

    fun <T> exit() = tween<T>(EXIT_MS, easing = Exit)

    fun <T> small() = tween<T>(SMALL_MS, easing = Enter)

    fun <T> press() = tween<T>(PRESS_MS, easing = LinearEasing)

    /** C-02 fade-through exit for top-level destinations. */
    fun <T> fadeThroughExit() = tween<T>(FADE_THROUGH_EXIT_MS, easing = Exit)

    /** Stagger delay for list item [index]; 0 beyond the cap. */
    fun staggerDelayMs(index: Int): Int =
        if (index < LIST_STAGGER_MAX_ITEMS) index * LIST_STAGGER_MS else 0
}
