package com.krlbryn.sophia.core

enum class SwipeDirection { LEFT, RIGHT }

/**
 * Decides whether a finished touch on the page was a deliberate horizontal swipe for Sophia's
 * gesture controls, or ordinary page interaction that must be left alone.
 *
 * A swipe must be quick, long enough, clearly horizontal, made with one finger, must not have started
 * as a long press (text selection), and must not have scrolled the page sideways.
 */
object SwipeClassifier {
    const val MIN_DISTANCE_DP = 96f
    const val MAX_DURATION_MS = 450L
    /** Horizontal travel must be at least this many times the vertical travel. */
    const val MIN_HORIZONTAL_RATIO = 2.5f

    data class Touch(
        val dxPx: Float,
        val dyPx: Float,
        val durationMs: Long,
        val density: Float,
        val multiTouch: Boolean = false,
        val longPress: Boolean = false,
        val pageScrolledHorizontally: Boolean = false,
    )

    fun classify(t: Touch): SwipeDirection? {
        if (t.multiTouch || t.longPress || t.pageScrolledHorizontally) return null
        if (t.durationMs > MAX_DURATION_MS) return null
        val dx = t.dxPx / t.density
        val dy = t.dyPx / t.density
        if (kotlin.math.abs(dx) < MIN_DISTANCE_DP) return null
        if (kotlin.math.abs(dx) < MIN_HORIZONTAL_RATIO * kotlin.math.abs(dy)) return null
        return if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
    }
}
