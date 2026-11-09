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
        /**
         * Where the swipe started and how wide the surface is. When given, the swipe must start near
         * the edge it moves away from: a swipe to the right must start near the left edge, and a swipe
         * to the left near the right edge. Swipes that close an open panel leave these out.
         */
        val startXPx: Float? = null,
        val widthPx: Float? = null,
    )

    /** The edge zone is a fifth of the width, at least 48dp and at most 160dp. */
    const val EDGE_FRACTION = 0.2f
    const val MIN_EDGE_DP = 48f
    const val MAX_EDGE_DP = 160f

    fun edgeZonePx(widthPx: Float, density: Float): Float =
        (widthPx * EDGE_FRACTION).coerceIn(MIN_EDGE_DP * density, MAX_EDGE_DP * density)

    fun classify(t: Touch): SwipeDirection? {
        if (t.multiTouch || t.longPress || t.pageScrolledHorizontally) return null
        if (t.durationMs > MAX_DURATION_MS) return null
        val dx = t.dxPx / t.density
        val dy = t.dyPx / t.density
        if (kotlin.math.abs(dx) < MIN_DISTANCE_DP) return null
        if (kotlin.math.abs(dx) < MIN_HORIZONTAL_RATIO * kotlin.math.abs(dy)) return null
        val direction = if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
        if (t.startXPx != null && t.widthPx != null && t.widthPx > 0f) {
            val zone = edgeZonePx(t.widthPx, t.density)
            val fromEdge = when (direction) {
                SwipeDirection.RIGHT -> t.startXPx
                SwipeDirection.LEFT -> t.widthPx - t.startXPx
            }
            if (fromEdge > zone) return null
        }
        return direction
    }
}
